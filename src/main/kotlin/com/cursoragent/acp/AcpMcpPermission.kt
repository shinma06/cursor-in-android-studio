package com.cursoragent.acp

import com.cursoragent.service.AgentMcpTarget
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** Cursor 2026.09.02 MCP shape. Unknown tools/arguments remain fail-closed (#447). */
internal fun mcpPermissionTarget(value: JsonElement?): AgentMcpTarget? {
    val input = value?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val server = input.mcpText("providerIdentifier", 256) ?: return null
    val tool = input.mcpText("toolName", 128)?.takeIf { it == "get_project_modules" } ?: return null
    val project = moduleProject(input["args"]) ?: return null
    return AgentMcpTarget(server, tool, project)
}

/** Never authorize a changed request from retained earlier arguments or its human-readable title. */
internal fun matchesMcpPermission(payload: JsonObject, target: AgentMcpTarget): Boolean {
    if (payload.has("kind") && payload.mcpText("kind", 32) != "other") return false
    if (!payload.has("content")) return true // ACP permits an ID-only request for a known tool.
    val content = payload["content"].takeIf { it.isJsonArray }?.asJsonArray ?: return false
    if (content.size() != 1) return false
    val item = content[0].takeIf { it.isJsonObject }?.asJsonObject ?: return false
    if (item.mcpText("type", 32) != "content") return false
    val block = item["content"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
    if (block.mcpText("type", 32) != "text") return false
    val text = block["text"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return false
    if (text.length > 4096 || !text.startsWith("```json\n") || !text.endsWith("\n```")) return false
    return runCatching {
        JsonReader(StringReader(text.removePrefix("```json\n").removeSuffix("\n```"))).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.beginObject()
            require(reader.nextName() == "projectPath" && reader.peek() == JsonToken.STRING)
            val project = reader.nextString()
            require(!reader.hasNext()) // Duplicate keys and unshown extra arguments are not approvable.
            reader.endObject()
            project == target.projectPath && reader.peek() == JsonToken.END_DOCUMENT
        }
    }.getOrDefault(false)
}

private fun moduleProject(value: JsonElement?): String? {
    val args = value?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    if (args.keySet() != setOf("projectPath")) return null
    return args.mcpText("projectPath", 2048)?.takeIf {
        it.startsWith("/") || it.matches(Regex("[A-Za-z]:[\\\\/].*"))
    }
}

private fun JsonObject.mcpText(key: String, limit: Int): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf {
        it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl)
    }
