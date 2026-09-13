package com.cursoragent.parser

import com.google.gson.JsonObject

/**
 * Parses `tool_call` events from live `cursor-agent` stream-json. Events use `subtype`
 * `started`/`completed` and nest tool-specific payloads under keys like `readToolCall`,
 * `editToolCall`, `shellToolCall`. Only the `completed` shape is verified against a
 * captured live event (CLI `2026.09.02-c22c1a3`, Teams plan, 2026-09) — see
 * `src/test/resources/stream-json-fixtures/`; the `started` handling below is inferred
 * from that shape, not independently confirmed.
 */
object ToolCallPayloadParser {
    /**
     * Defensive per the parser package's stream-json contract: any unexpected shape
     * (a `*ToolCall` value that isn't an object, a `result`/`success` that isn't one,
     * etc.) is caught and turned into `null` rather than throwing, so one malformed
     * `tool_call` line can't abort parsing of the rest of the output chunk.
     */
    fun parse(json: JsonObject): ParsedToolCall? = runCatching { parseUnsafe(json) }.getOrNull()

    private fun parseUnsafe(json: JsonObject): ParsedToolCall? {
        val subtype = json.get("subtype")?.asString ?: return null
        val callId = json.get("call_id")?.asString ?: json.get("callId")?.asString ?: return null
        val toolCall = json.getAsJsonObject("tool_call") ?: return null

        val kindEntry = toolCall.entrySet().firstOrNull { it.key.endsWith("ToolCall") } ?: return null
        val kind = kindEntry.key.removeSuffix("ToolCall")
        if (!kindEntry.value.isJsonObject) return null
        val payload = kindEntry.value.asJsonObject

        return when (kind) {
            "task" -> parseTask(json, callId, subtype, payload)
            "read" -> parseRead(callId, subtype, payload)
            "edit" -> parseEdit(callId, subtype, payload)
            "shell" -> parseShell(callId, subtype, payload)
            else -> ParsedToolCall(
                callId = callId,
                subtype = subtype,
                kind = kind,
                summary = "$kind tool",
            )
        }
    }

    private fun parseTask(json: JsonObject, callId: String, subtype: String, payload: JsonObject): ParsedToolCall? {
        if (subtype !in setOf("started", "completed") ||
            (json.taskId("call_id") ?: json.taskId("callId")) != callId) return null
        val parent = json.taskId("session_id") ?: return null
        val result = payload.objectValue("result")
        val failed = result?.has("error") == true
        val success = if (failed) null else result?.objectValue("success")
        val details = com.cursoragent.service.AgentTask().withTaskInput(payload.objectValue("args"))
            .withTaskOutput(success, includeSteps = true).copy(
                errorText = if (failed) result.objectValue("error")?.taskString("error", 8_192) else null,
            )
        val status = when {
            failed -> "failed"
            subtype == "started" -> "in_progress"
            success != null -> "completed"
            else -> null
        }
        return ParsedToolCall(callId, subtype, "task", "子Task", parentSessionId = parent,
            task = com.cursoragent.service.AgentTool(callId, "子Task", "task", status, task = details))
    }

    private fun parseRead(callId: String, subtype: String, payload: JsonObject): ParsedToolCall {
        val path = payload.getAsJsonObject("args")?.get("path")?.asString ?: "file"
        return ParsedToolCall(
            callId = callId,
            subtype = subtype,
            kind = "read",
            summary = if (subtype == "started") "Reading $path" else "Read $path",
        )
    }

    private fun parseEdit(callId: String, subtype: String, payload: JsonObject): ParsedToolCall {
        val args = payload.getAsJsonObject("args")
        val path = args?.get("path")?.asString ?: "file"

        if (subtype == "started") {
            return ParsedToolCall(
                callId = callId,
                subtype = subtype,
                kind = "edit",
                summary = "Editing $path",
            )
        }

        val success = payload.getAsJsonObject("result")?.getAsJsonObject("success")
        val fileEdit = success?.let {
            FileEditDetails(
                path = it.get("path")?.asString ?: path,
                linesAdded = it.get("linesAdded")?.asInt ?: 0,
                linesRemoved = it.get("linesRemoved")?.asInt ?: 0,
                diffString = it.get("diffString")?.asString,
                beforeContent = it.get("beforeFullFileContent")?.asString,
                afterContent = it.get("afterFullFileContent")?.asString,
            )
        }

        val summary = fileEdit?.let { "+${it.linesAdded}/-${it.linesRemoved} ${it.path}" }
            ?: "Edited $path"

        return ParsedToolCall(
            callId = callId,
            subtype = subtype,
            kind = "edit",
            summary = summary,
            fileEdit = fileEdit,
        )
    }

    private fun parseShell(callId: String, subtype: String, payload: JsonObject): ParsedToolCall {
        val command = payload.getAsJsonObject("args")?.get("command")?.asString
            ?: payload.get("description")?.asString
            ?: "shell"

        if (subtype == "started") {
            return ParsedToolCall(
                callId = callId,
                subtype = subtype,
                kind = "shell",
                summary = "Running: $command",
            )
        }

        val success = payload.getAsJsonObject("result")?.getAsJsonObject("success")
        val shellResult = success?.let {
            ShellResultDetails(
                command = it.get("command")?.asString ?: command,
                exitCode = it.get("exitCode")?.asInt ?: -1,
                stdout = it.get("stdout")?.asString.orEmpty(),
                stderr = it.get("stderr")?.asString.orEmpty(),
                interleavedOutput = it.get("interleavedOutput")?.asString,
            )
        }

        val exitCode = shellResult?.exitCode ?: -1
        return ParsedToolCall(
            callId = callId,
            subtype = subtype,
            kind = "shell",
            summary = "Shell exited $exitCode: $command",
            shellResult = shellResult,
        )
    }
}
