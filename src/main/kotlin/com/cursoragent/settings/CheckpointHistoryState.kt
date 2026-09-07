package com.cursoragent.settings

import com.cursoragent.service.RestoreTarget
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

data class CheckpointRecord(
    var id: String = "",
    var promptPreview: String = "",
    var timestampMs: Long = 0L,
    var gitSha: String = "",
    var chatId: String? = null,
    /** `git ls-files --others --exclude-standard` output at snapshot time, used by
     *  [com.cursoragent.service.CheckpointService.restore] to remove files the agent
     *  created after this checkpoint (a plain `git checkout <sha> -- .` only restores
     *  paths that existed in the snapshot; it never deletes new ones). */
    var untrackedFilesAtSnapshot: MutableList<String> = mutableListOf(),
    // Missing fields in old XML deliberately stay unknown; never infer DEFAULT or the current root.
    var rootPath: String? = null,
    var worktreeMode: String? = null,
) {
    fun restoreTarget(): RestoreTarget = RestoreTarget(
        rootPath,
        WorktreeMode.entries.find { it.name == worktreeMode },
    )
}

@Service(Service.Level.PROJECT)
@State(name = "CursorAgentCheckpoints", storages = [Storage("cursor-agent-checkpoints.xml")])
class CheckpointHistoryState : PersistentStateComponent<CheckpointHistoryState.State> {
    class State {
        var records: MutableList<CheckpointRecord> = mutableListOf()
    }

    private var state = State()

    @Synchronized
    override fun getState(): State = State().also { snapshot ->
        snapshot.records = state.records.map(::copyRecord).toMutableList()
    }

    @Synchronized
    override fun loadState(state: State) {
        this.state = State().also { it.records = state.records.map(::copyRecord).toMutableList() }
    }

    @Synchronized
    fun addRecord(record: CheckpointRecord) {
        state.records.add(copyRecord(record))
    }

    @Synchronized
    fun findRecord(id: String): CheckpointRecord? = state.records.find { it.id == id }?.let(::copyRecord)

    @Synchronized
    fun recordsForChat(chatId: String?): List<CheckpointRecord> =
        state.records.filter { it.chatId == chatId }.map(::copyRecord)

    @Synchronized
    fun pruneOlderThan(cutoffMs: Long) {
        state.records.removeAll { it.timestampMs < cutoffMs }
    }

    private fun copyRecord(record: CheckpointRecord) =
        record.copy(untrackedFilesAtSnapshot = record.untrackedFilesAtSnapshot.toMutableList())

    companion object {
        fun getInstance(project: Project): CheckpointHistoryState =
            project.getService(CheckpointHistoryState::class.java)
    }
}
