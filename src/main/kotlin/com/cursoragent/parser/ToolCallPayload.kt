package com.cursoragent.parser

data class FileEditDetails(
    val path: String,
    val linesAdded: Int,
    val linesRemoved: Int,
    val diffString: String?,
    val beforeContent: String?,
    val afterContent: String?,
)

data class ShellResultDetails(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val interleavedOutput: String?,
)

data class ParsedToolCall(
    val callId: String,
    val subtype: String,
    val kind: String,
    val summary: String,
    val fileEdit: FileEditDetails? = null,
    val shellResult: ShellResultDetails? = null,
    val parentSessionId: String? = null,
    val task: com.cursoragent.service.AgentTool? = null,
)

/** Task correlation requires an already confirmed parent session; other tool contracts are unchanged. */
internal fun ParsedToolCall.belongsToPrintSession(sessionId: String?): Boolean =
    task == null || parentSessionId != null && parentSessionId == sessionId

internal fun ParsedToolCall.taskKey(): String = "${requireNotNull(parentSessionId).length}:$parentSessionId$callId"
