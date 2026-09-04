package com.cursoragent.parser

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * stream-json (JSON Lines) の防御的パーサー。
 * cursor-agent 2026-09 時点の出力形式に対応する。
 */
class StreamJsonParser(
    private val onEvent: (StreamEvent) -> Unit,
) {
    fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return

        val json = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: return
        val event = runCatching { mapEvent(json) }.getOrElse { StreamEvent.Unknown("unknown", trimmed) }
        onEvent(event)
    }

    private fun mapEvent(json: JsonObject): StreamEvent {
        val type = json.get("type")?.asString ?: "unknown"
        val sessionId = json.get("session_id")?.asString ?: json.get("chatId")?.asString

        return when (type) {
            "system" -> {
                if (json.get("subtype")?.asString == "init") {
                    StreamEvent.SessionInit(
                        sessionId = sessionId,
                        model = json.get("model")?.asString,
                    )
                } else {
                    StreamEvent.Unknown(type, json.toString())
                }
            }

            "assistant" -> {
                StreamEvent.AssistantDelta(extractAssistantText(json))
            }

            "thinking" -> {
                if (json.get("subtype")?.asString == "delta") {
                    StreamEvent.ThinkingDelta(json.get("text")?.asString.orEmpty())
                } else {
                    StreamEvent.Unknown(type, json.toString())
                }
            }

            "tool_call" -> {
                ToolCallPayloadParser.parse(json)?.let { parsed ->
                    when (parsed.subtype) {
                        "started" -> StreamEvent.ToolCallStarted(parsed)
                        "completed" -> StreamEvent.ToolCallCompleted(parsed)
                        else -> StreamEvent.ToolCall(parsed.summary)
                    }
                } ?: StreamEvent.ToolCall(extractLegacyToolName(json))
            }

            "result" -> {
                StreamEvent.Result(
                    sessionId = sessionId,
                    model = json.get("model")?.asString,
                    result = json.get("result")?.asString,
                    isError = json.get("is_error")?.asBoolean == true,
                )
            }

            else -> StreamEvent.Unknown(type, json.toString())
        }
    }

    private fun extractAssistantText(json: JsonObject): String {
        json.get("text")?.asString?.let { return it }
        json.get("content")?.asString?.let { return it }

        json.getAsJsonObject("message")?.let { message ->
            extractTextFromContent(message.get("content"))?.let { return it }
        }

        extractTextFromContent(json.get("content"))?.let { return it }
        return ""
    }

    private fun extractTextFromContent(contentElement: com.google.gson.JsonElement?): String? {
        if (contentElement == null) return null
        if (contentElement.isJsonPrimitive) return contentElement.asString

        if (contentElement.isJsonArray) {
            return buildString {
                contentElement.asJsonArray.forEach { element ->
                    if (element.isJsonObject) {
                        element.asJsonObject.get("text")?.asString?.let { append(it) }
                    } else if (element.isJsonPrimitive) {
                        append(element.asString)
                    }
                }
            }.takeIf { it.isNotEmpty() }
        }

        return null
    }

    private fun extractLegacyToolName(json: JsonObject): String {
        return json.get("name")?.asString
            ?: json.getAsJsonObject("tool")?.get("name")?.asString
            ?: "tool"
    }
}

sealed interface StreamEvent {
    data class SessionInit(val sessionId: String?, val model: String?) : StreamEvent
    data class AssistantDelta(val text: String) : StreamEvent
    data class ThinkingDelta(val text: String) : StreamEvent
    data class ToolCall(val toolName: String) : StreamEvent
    data class ToolCallStarted(val payload: ParsedToolCall) : StreamEvent
    data class ToolCallCompleted(val payload: ParsedToolCall) : StreamEvent
    data class Result(
        val sessionId: String?,
        val model: String?,
        val result: String?,
        val isError: Boolean,
    ) : StreamEvent

    data class Unknown(val type: String, val trimmed: String) : StreamEvent
}
