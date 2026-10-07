package com.cursoragent.acp

import com.cursoragent.service.AgentToolContent
import com.cursoragent.service.ContentDisplayState
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Bounded display projection only. Does not decode, resolve, fetch, or retain binary payloads. */
internal class AcpContent {
    private var retained = 0
    private var assistantCount = 0

    fun assistant(value: JsonElement?): AgentToolContent.Summary? {
        if (assistantCount++ > MAX_ASSISTANT_ITEMS) return null
        if (assistantCount > MAX_ASSISTANT_ITEMS) return limited("応答内容")
        return retain(block(value)) as AgentToolContent.Summary
    }

    fun tool(value: JsonElement?): List<AgentToolContent> {
        if (value?.isJsonArray != true) return listOf(invalid("content"))
        val result = value.asJsonArray.take(MAX_ITEMS).map { item ->
            val data = item.objectOrNull()
            retain(when (val type = data?.text("type")?.takeIf { it.isNotBlank() }) {
                "content" -> block(data["content"])
                "diff" -> diff(data)
                "terminal" -> AgentToolContent.Unsupported("terminal") // ID reference is not terminal output.
                null -> invalid("content")
                else -> unknown(type)
            })
        }
        return if (value.asJsonArray.size() > MAX_ITEMS) result + limited("content") else result
    }

    fun locations(value: JsonElement?): Pair<List<String>, String?> {
        if (value?.isJsonArray != true) return emptyList<String>() to "locations: 内容の形式が不正"
        var invalid = false
        var limited = value.asJsonArray.size() > MAX_ITEMS
        val paths = value.asJsonArray.take(MAX_ITEMS).mapNotNull {
            val path = it.objectOrNull()?.text("path")
            when {
                path.isNullOrBlank() -> { invalid = true; null }
                path.length > MAX_METADATA || retained + path.length > MAX_TURN_TEXT -> { limited = true; null }
                else -> { retained += path.length; path }
            }
        }
        val notice = listOfNotNull(
            "一部のlocationsの形式が不正".takeIf { invalid },
            "locationsの表示上限により省略".takeIf { limited },
        ).joinToString(" / ").ifEmpty { null }
        return paths to notice
    }

    private fun block(value: JsonElement?): AgentToolContent {
        val data = value.objectOrNull() ?: return invalid("content")
        val type = data.text("type")?.takeIf { it.isNotBlank() } ?: return invalid("content")
        if (type == "text") {
            val text = data.text("text") ?: return invalid("text")
            return if (text.length <= MAX_TEXT) AgentToolContent.Text(text)
            else AgentToolContent.Summary("text", ContentDisplayState.LIMITED, text.take(MAX_TEXT) + "\n[以降を省略]")
        }
        if (type !in setOf("image", "audio", "resource_link", "resource")) return unknown(type)
        var state = ContentDisplayState.METADATA
        val lines = mutableListOf<String>()
        fun field(source: JsonObject, key: String, label: String, required: Boolean = false) {
            val text = source.text(key)
            if (text == null || required && text.isBlank()) {
                if (required || source.has(key)) state = ContentDisplayState.INVALID
                return
            }
            if (text.length > MAX_METADATA && state != ContentDisplayState.INVALID) state = ContentDisplayState.LIMITED
            lines.add("$label: " + text.take(MAX_METADATA) + if (text.length > MAX_METADATA) " [以降を省略]" else "")
        }
        var kind = type
        when (type) {
            "image", "audio" -> {
                field(data, "mimeType", "申告MIME", required = true)
                if (type == "image") field(data, "uri", "URI")
                if (data.text("data").isNullOrEmpty()) state = ContentDisplayState.INVALID
                lines.add("サイズ: 不明（バイナリ未検証・非保持）")
            }
            "resource_link" -> {
                field(data, "name", "名前", required = true)
                field(data, "uri", "URI", required = true)
                field(data, "title", "タイトル")
                field(data, "description", "説明")
                field(data, "mimeType", "申告MIME")
                val size = data["size"]
                val bytes = size?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.toString()?.takeIf { it.matches(Regex("0|[1-9][0-9]{0,18}")) }?.toLongOrNull()
                if (data.has("size") && bytes == null) state = ContentDisplayState.INVALID
                lines.add(if (bytes == null) "サイズ: 不明" else "申告サイズ: $bytes bytes（未検証）")
            }
            "resource" -> {
                val resource = data["resource"].objectOrNull() ?: return invalid("resource")
                field(resource, "uri", "URI", required = true)
                field(resource, "mimeType", "申告MIME")
                when {
                    resource.has("text") && !resource.has("blob") -> {
                        kind = "resource (text)"
                        val text = resource.text("text")
                        if (text == null) state = ContentDisplayState.INVALID
                        else {
                            if (text.length > MAX_TEXT && state != ContentDisplayState.INVALID) state = ContentDisplayState.LIMITED
                            lines.add("本文:\n" + text.take(MAX_TEXT) + if (text.length > MAX_TEXT) "\n[以降を省略]" else "")
                        }
                    }
                    resource.has("blob") && !resource.has("text") -> {
                        kind = "resource (blob)"
                        if (resource.text("blob").isNullOrEmpty()) state = ContentDisplayState.INVALID
                        lines.add("サイズ: 不明（バイナリ未検証・非保持）")
                    }
                    else -> state = ContentDisplayState.INVALID
                }
            }
        }
        return AgentToolContent.Summary(kind, state, lines.joinToString("\n"))
    }

    private fun diff(data: JsonObject): AgentToolContent {
        val path = data.text("path")
        val before = data.text("oldText")
        val after = data.text("newText")
        if (path.isNullOrBlank() || after == null || data.has("oldText") && !data["oldText"].isJsonNull && before == null) return invalid("diff")
        // Never open a fabricated partial path or truncated diff in the native viewer.
        if (path.length > MAX_METADATA || after.length > MAX_TEXT || (before?.length ?: 0) > MAX_TEXT) return limited("diff")
        return AgentToolContent.Diff(path, before, after)
    }

    private fun retain(content: AgentToolContent): AgentToolContent {
        val size = when (content) {
            is AgentToolContent.Text -> content.text.length
            is AgentToolContent.Diff -> content.path.length + (content.before?.length ?: 0) + content.after.length
            is AgentToolContent.Summary -> content.type.length + content.details.length
            is AgentToolContent.Unsupported -> content.type.length
        }
        if (retained + size > MAX_TURN_TEXT) return limited("内容")
        retained += size
        return content
    }

    private fun unknown(type: String) = if (type.length <= MAX_METADATA)
        AgentToolContent.Summary(type, ContentDisplayState.UNSUPPORTED)
    else AgentToolContent.Summary(type.take(MAX_METADATA), ContentDisplayState.LIMITED, "表示未対応の型（以降を省略）")
    private fun invalid(type: String) = AgentToolContent.Summary(type, ContentDisplayState.INVALID)
    private fun limited(type: String) = AgentToolContent.Summary(type, ContentDisplayState.LIMITED)

    companion object {
        const val MAX_ITEMS = 64
        const val MAX_ASSISTANT_ITEMS = 512
        const val MAX_METADATA = 2_048
        const val MAX_TEXT = 32_768
        const val MAX_TURN_TEXT = 1024 * 1024
    }
}

private fun JsonElement?.objectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.text(key: String): String? = get(key)?.takeIf {
    it.isJsonPrimitive && it.asJsonPrimitive.isString
}?.asString
