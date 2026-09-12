package com.cursoragent.ui

import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import java.util.UUID

/** Explicit attachment identities/selections are fixed at registration; referenced files resolve at turn start. */
internal data class QueuedPrompt(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val mode: AgentMode,
    val model: String,
    val context: PromptContextSnapshot? = null,
)

internal data class QueueDispatch(val generation: Long, val revision: Long, val prompt: QueuedPrompt)

/** One controller/EDT owns this transient queue. Saved conversations never replay it. */
internal class PromptQueue(val conversationId: String) {
    private var revision = 0L
    private val entries = mutableListOf<QueuedPrompt>()
    var paused = false
        private set
    val size get() = entries.size
    fun snapshot(): List<QueuedPrompt> = entries.toList()

    fun add(text: String, mode: AgentMode, model: String, context: PromptContextSnapshot? = null): Boolean {
        if (text.isBlank()) return false
        if (entries.isEmpty()) paused = false
        revision++
        entries.add(QueuedPrompt(text = text, mode = mode, model = model, context = context))
        return true
    }

    fun pause() { revision++; paused = true }
    fun resume() { revision++; paused = false }
    fun clear() { entries.clear(); pause() }
    fun next(): QueuedPrompt? = if (paused) null else entries.firstOrNull()
    fun ticket(generation: Long): QueueDispatch? = next()?.let { QueueDispatch(generation, revision, it) }

    /** Recheck the scheduled action, conversation/selection, and idle run at actual dispatch time. */
    fun dispatch(ticket: QueueDispatch, generation: Long, ownerIsIdle: Boolean, start: (QueuedPrompt) -> Boolean): Boolean {
        if (!ownerIsIdle || ticket.generation != generation || ticket.revision != revision || next() != ticket.prompt) return false
        return if (start(ticket.prompt)) { remove(ticket.prompt.id); true } else { pause(); false }
    }
    fun remove(id: String) { if (entries.removeIf { it.id == id }) revision++ }
    fun edit(id: String, text: String): Boolean {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0 || text.isBlank()) return false
        pause()
        entries[index] = entries[index].copy(text = text)
        return true
    }
    fun move(id: String, offset: Int) {
        val from = entries.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in entries.indices) return
        pause()
        entries.add(to, entries.removeAt(from))
    }
}
