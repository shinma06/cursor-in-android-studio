package com.cursoragent.ui

import com.cursoragent.parser.FileEditDetails
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import com.cursoragent.service.AgentTransport
import com.cursoragent.service.RestorePolicy
import com.cursoragent.service.RestoreTarget
import com.cursoragent.settings.WorktreeMode
import java.nio.file.Path

/** Display-time observations only. Never persist these contents or derive restore permission from saved IDs. */
internal data class ObservedFileChange(
    val turnId: String,
    val callId: String,
    val path: String,
    val before: String?,
    val after: String?,
    val target: RestoreTarget,
    val transport: AgentTransport,
    val status: String,
)

internal data class FileChangeGroup(val edits: List<ObservedFileChange>) {
    val first get() = edits.first()
    val last get() = edits.last()
    val canShowDiff get() = first.before != null && last.after != null
    val revertRejection: String? get() = when {
        edits.any { it.transport != AgentTransport.PRINT } -> "ACPの差分は閲覧のみです。Revertは接続していません。"
        !canShowDiff || edits.any { it.before == null || it.after == null } -> "編集前後の全文を取得できないためRevertできません。"
        edits.zipWithNext().any { (before, after) -> before.after != after.before } -> "編集の間に別の変更があるため、まとめてRevertできません。"
        first.target.rootPath == null || first.target.worktreeMode == null -> RestorePolicy.UNKNOWN_TARGET
        first.target.worktreeMode == WorktreeMode.ISOLATED -> RestorePolicy.ISOLATED
        else -> null
    }
}

internal data class ChangesSnapshot(val conversationId: String, val turns: List<String>, val edits: List<ObservedFileChange>) {
    fun files(turnId: String? = null): List<FileChangeGroup> = edits.filter { turnId == null || it.turnId == turnId }
        .groupBy { Triple(it.target, it.path, it.transport) }.values.map(::FileChangeGroup)
}

/** One EDT owner per conversation. Provider call IDs are namespaced by the stable turn ID. */
internal class ConversationChanges(private val conversationId: String) {
    private val turns = linkedSetOf<String>()
    private data class Key(val turnId: String, val callId: String, val path: String, val target: RestoreTarget, val transport: AgentTransport)
    private val edits = linkedMapOf<Key, ObservedFileChange>()

    fun beginTurn(id: String) { turns.add(id) }
    fun snapshot() = ChangesSnapshot(conversationId, turns.toList(), edits.values.toList())

    fun print(turnId: String, callId: String, details: FileEditDetails, target: RestoreTarget) {
        record(ObservedFileChange(turnId, callId, details.path, details.beforeContent, details.afterContent, target, AgentTransport.PRINT, "completed"))
    }

    fun acp(turnId: String, tool: AgentTool, target: RestoreTarget) {
        val current = tool.content.filterIsInstance<AgentToolContent.Diff>().map { diff ->
            // ACP oldText absent means a new file; it still does not authorize a local Revert.
            ObservedFileChange(turnId, tool.id, diff.path, diff.before.orEmpty(), diff.after, target, AgentTransport.ACP, tool.status.orEmpty())
        }
        val paths = current.map { normalizedPath(it.path, target) }.toSet()
        edits.keys.removeIf { it.turnId == turnId && it.callId == tool.id && it.transport == AgentTransport.ACP && it.path !in paths }
        current.forEach(::record)
    }

    private fun record(value: ObservedFileChange) {
        if (value.path.isBlank()) return
        turns.add(value.turnId)
        val path = normalizedPath(value.path, value.target)
        val key = Key(value.turnId, value.callId, path, value.target, value.transport)
        // Updating a known call keeps its position; late duplicate completion cannot reorder later edits.
        edits[key] = value.copy(path = path)
    }

    private fun normalizedPath(path: String, target: RestoreTarget): String = runCatching {
        val candidate = Path.of(path)
        (target.rootPath?.let { Path.of(it).resolve(candidate) } ?: candidate).normalize().toString()
    }.getOrDefault(path)
}
