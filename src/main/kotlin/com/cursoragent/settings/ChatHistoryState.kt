package com.cursoragent.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

data class ChatHistoryRecord(
    var chatId: String = "",
    var firstPromptPreview: String = "",
    var lastUpdatedMs: Long = 0L,
)

/**
 * Metadata for the current print transport; message bodies are not persisted here.
 * The 2026-09 non-TTY `agent ls`/`agent resume` picker failure motivated this store
 * (requirements §13). It does not rule out other transports' session APIs.
 * ACP restoration and compatibility with these stored chat IDs require #115 validation.
 */
@Service(Service.Level.PROJECT)
@State(name = "CursorAgentChatHistory", storages = [Storage("cursor-agent-chat-history.xml")])
class ChatHistoryState : PersistentStateComponent<ChatHistoryState.State> {
    class State {
        var records: MutableList<ChatHistoryRecord> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun recordTurn(chatId: String, promptPreview: String) {
        val existing = state.records.find { it.chatId == chatId }
        if (existing != null) {
            existing.lastUpdatedMs = System.currentTimeMillis()
        } else {
            state.records.add(ChatHistoryRecord(chatId, promptPreview.take(120), System.currentTimeMillis()))
        }
    }

    fun delete(chatId: String) { state.records.removeAll { it.chatId == chatId } }

    fun list(): List<ChatHistoryRecord> = state.records.sortedByDescending { it.lastUpdatedMs }

    companion object {
        fun getInstance(project: Project): ChatHistoryState =
            project.getService(ChatHistoryState::class.java)
    }
}
