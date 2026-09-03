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
 * `agent ls`/`agent resume` (the CLI's own past-session picker) require a raw TTY
 * and hard-fail through this plugin's non-interactive subprocess (verified live,
 * see requirements doc §13), so past chats are tracked here instead, independent
 * of the CLI.
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

    fun list(): List<ChatHistoryRecord> = state.records.sortedByDescending { it.lastUpdatedMs }

    companion object {
        fun getInstance(project: Project): ChatHistoryState =
            project.getService(ChatHistoryState::class.java)
    }
}
