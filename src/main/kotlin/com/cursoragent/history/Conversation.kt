package com.cursoragent.history

import com.cursoragent.service.AgentTransport
import com.cursoragent.settings.WorktreeMode
import java.util.UUID

fun newHistoryId(): String = UUID.randomUUID().toString()

data class ChatMessage(val id: String = newHistoryId(), val role: String, val text: String)
data class SavedTurn(
    val id: String = newHistoryId(),
    val state: String = "running",
    val messages: List<ChatMessage> = emptyList(),
)

/** Display data only: never stores injected context, wire payloads, credentials or executable actions. */
data class Conversation(
    val version: Int = 1,
    val id: String = newHistoryId(),
    val transport: AgentTransport = AgentTransport.PRINT,
    val providerId: String? = null,
    val root: String? = null,
    val worktreeMode: WorktreeMode? = null,
    val updatedMs: Long = System.currentTimeMillis(),
    val turns: List<SavedTurn> = emptyList(),
) {
    val preview: String get() = turns.firstOrNull()?.messages?.firstOrNull { it.role == "user" }?.text?.take(120).orEmpty()
    fun interrupted(): Conversation = copy(turns = turns.map {
        if (it.state == "running") it.copy(state = "interrupted") else it
    })
    fun canResume(currentRoot: String?, mode: WorktreeMode): Boolean =
        transport == AgentTransport.PRINT && !providerId.isNullOrBlank() && root != null &&
            root == currentRoot && worktreeMode == mode && mode == WorktreeMode.DEFAULT
}

/** One controller owns this model; all calls run on EDT, independently of the selected tab. */
class ConversationRecorder(var conversation: Conversation, private val save: (Conversation) -> Unit) {
    private var turnId: String? = null
    private var assistantId: String? = null
    private val tools = mutableMapOf<String, String>()

    fun begin(id: String, prompt: String) {
        turnId = id
        assistantId = null
        tools.clear()
        publish(conversation.copy(turns = conversation.turns + SavedTurn(id, messages = listOf(ChatMessage(role = "user", text = prompt)))))
    }

    fun provider(id: String) { publish(conversation.copy(providerId = id)) }
    fun provenance(root: String?, mode: WorktreeMode) { publish(conversation.copy(root = root, worktreeMode = mode)) }
    fun newAssistant() { assistantId = null }
    fun assistant(text: String) {
        val id = assistantId ?: newHistoryId().also { assistantId = it }
        message(ChatMessage(id, "assistant", text))
    }
    fun tool(key: String, summary: String) {
        val id = tools.getOrPut(key) { newHistoryId() }
        message(ChatMessage(id, "tool", summary.take(500)))
    }
    fun error(text: String) { message(ChatMessage(role = "error", text = text)) }
    fun finish(state: String) {
        if (turnId == null) return
        publish(conversation.copy(turns = conversation.turns.map { if (it.id == turnId) it.copy(state = state) else it }))
        turnId = null
    }
    private fun message(message: ChatMessage) {
        if (turnId == null) return
        publish(conversation.copy(turns = conversation.turns.map { turn ->
            if (turn.id != turnId) turn else {
                val index = turn.messages.indexOfFirst { it.id == message.id }
                turn.copy(messages = if (index < 0) turn.messages + message else turn.messages.toMutableList().apply { set(index, message) })
            }
        }))
    }
    private fun publish(value: Conversation) {
        conversation = value.copy(updatedMs = System.currentTimeMillis())
        save(conversation)
    }
}
