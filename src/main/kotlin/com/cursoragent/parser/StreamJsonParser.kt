package com.cursoragent.parser

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * stream-json (JSON Lines) の防御的パーサー。
 * 現行CLI print経路の2026-09観測形式を扱う。ACP JSON-RPCのパーサーではない。
 */
class StreamJsonParser(
    private val onEvent: (StreamEvent) -> Unit,
) {
    private val pendingLine = StringBuilder()

    /** Process output notifications may split a JSON line, including inside a string. */
    fun parseChunk(chunk: String) {
        var start = 0
        chunk.forEachIndexed { index, char ->
            if (char == '\n') {
                pendingLine.append(chunk, start, index)
                val line = pendingLine.toString()
                pendingLine.setLength(0)
                parseLine(line)
                start = index + 1
            }
        }
        pendingLine.append(chunk, start, chunk.length)
    }

    /** Deliver a complete final JSON value even when the producer omitted its newline. */
    fun finish() {
        val line = pendingLine.toString()
        pendingLine.setLength(0)
        parseLine(line)
    }

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
                StreamEvent.AssistantDelta(extractAssistantText(json), assistantKind(json))
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
                    usage = TokenUsage.parse(json.get("usage")),
                    requestId = parsePrintRequestId(json.get("request_id")),
                    subtype = json.get("subtype")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString,
                )
            }

            else -> StreamEvent.Unknown(type, json.toString())
        }
    }

    private fun assistantKind(json: JsonObject): PrintAssistantKind {
        val timestamp = json.get("timestamp_ms")
        val call = json.get("model_call_id")
        if (timestamp == null && call == null) return PrintAssistantKind.FINAL_FLUSH
        val validTimestamp = runCatching {
            timestamp != null && timestamp.isJsonPrimitive && timestamp.asJsonPrimitive.isNumber &&
                timestamp.asBigDecimal.toBigIntegerExact().signum() >= 0
        }.getOrDefault(false)
        val validCall = call != null && call.isJsonPrimitive && call.asJsonPrimitive.isString && call.asString.isNotBlank()
        return when {
            validTimestamp && call == null -> PrintAssistantKind.DELTA
            validTimestamp && validCall -> PrintAssistantKind.TOOL_FLUSH
            else -> PrintAssistantKind.UNRECOGNIZED
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
    data class AssistantDelta(val text: String, val kind: PrintAssistantKind = PrintAssistantKind.UNRECOGNIZED) : StreamEvent
    data class ThinkingDelta(val text: String) : StreamEvent
    data class ToolCall(val toolName: String) : StreamEvent
    data class ToolCallStarted(val payload: ParsedToolCall) : StreamEvent
    data class ToolCallCompleted(val payload: ParsedToolCall) : StreamEvent
    data class Result(
        val sessionId: String?,
        val model: String?,
        val result: String?,
        val isError: Boolean,
        val usage: TokenUsage? = null,
        val requestId: String? = null,
        val subtype: String? = null,
    ) : StreamEvent

    data class Unknown(val type: String, val trimmed: String) : StreamEvent
}

/** Keep opaque provider IDs byte-for-byte; a bad optional field must not discard the Result. */
internal fun parsePrintRequestId(value: com.google.gson.JsonElement?): String? {
    if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
    val id = value.asString
    return id.takeIf {
        it.isNotEmpty() && it.length <= 1024 && !it.first().isWhitespace() && !it.last().isWhitespace() &&
            it.none(Char::isISOControl) && Charsets.UTF_8.newEncoder().canEncode(it) && it.toByteArray(Charsets.UTF_8).size <= 1024
    }
}
