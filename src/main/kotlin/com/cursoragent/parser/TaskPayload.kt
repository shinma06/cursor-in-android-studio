package com.cursoragent.parser

import com.cursoragent.service.AgentTask
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Known fields from #118 only; invalid replacements clear their fields instead of retaining stale data. */
internal fun AgentTask.withTaskInput(input: JsonObject?): AgentTask = copy(
    name = taskName(input?.get("subagentType")),
    description = input?.taskString("description"),
    model = input?.taskString("model"),
    requestedAgentId = input?.taskId("agentId"),
    resumeId = input?.taskId("resume"),
)

internal fun AgentTask.withTaskOutput(output: JsonObject?, includeSteps: Boolean = false): AgentTask = copy(
    agentId = output?.taskId("agentId"),
    durationMs = taskDuration(output),
    isBackground = output?.get("isBackground")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean,
    resultText = if (includeSteps) taskResultText(output) else null,
    errorText = null,
)

private fun taskResultText(output: JsonObject?): String? {
    val steps = output?.get("conversationSteps")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    val text = steps.take(128).mapNotNull { step ->
        step.takeIf { it.isJsonObject }?.asJsonObject?.objectValue("assistantMessage")?.taskString("text", 32_768)
    }.joinToString("\n\n")
    if (text.isEmpty()) return null
    return text.take(65_536) + if (text.length > 65_536 || steps.size() > 128) "…（表示上限）" else ""
}

internal fun AgentTask.withTaskMetadata(params: JsonObject): AgentTask = copy(
    name = if (params.has("subagentType")) taskName(params["subagentType"]) else name,
    description = if (params.has("description")) params.taskString("description") else description,
    model = if (params.has("model")) params.taskString("model") else model,
    reportedAgentId = if (params.has("agentId")) params.taskId("agentId") else reportedAgentId,
    durationMs = if (params.has("durationMs")) taskDuration(params) else durationMs,
    isBackground = if (params.has("isBackground")) params["isBackground"]?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
    }?.asBoolean else isBackground,
)

/** Public string custom, observed name object, and observed Cursor request wrapper only. */
private fun taskName(type: JsonElement?): String? {
    if (type?.isJsonPrimitive == true && type.asJsonPrimitive.isString) return type.asString.takeIf {
        it in setOf("unspecified", "computer_use", "explore", "video_review", "browser_use", "shell", "vm_setup_helper")
    }
    val value = type?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    return value.taskString("custom") ?: value.objectValue("custom")?.let {
        it.taskString("name") ?: it.objectValue("custom")?.taskString("name")
    }
}

internal fun taskDuration(value: JsonObject?): Long? = value?.get("durationMs")?.takeIf {
    it.isJsonPrimitive && (it.asJsonPrimitive.isString || it.asJsonPrimitive.isNumber)
}?.asString?.takeIf { it.length <= 19 && it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
    ?.toLongOrNull()?.takeIf { it <= 365L * 24 * 60 * 60 * 1000 }

internal fun JsonObject.taskString(name: String, limit: Int = 2_048): String? = get(name)?.takeIf {
    it.isJsonPrimitive && it.asJsonPrimitive.isString
}?.asString?.let { if (it.length > limit) it.take(limit) + "…（表示上限）" else it }?.takeIf { it.isNotBlank() }

internal fun JsonObject.objectValue(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.taskId(name: String): String? = get(name)?.takeIf {
    it.isJsonPrimitive && it.asJsonPrimitive.isString
}?.asString?.takeIf { it.isNotBlank() && it.length <= 2_048 }
