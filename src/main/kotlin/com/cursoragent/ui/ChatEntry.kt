package com.cursoragent.ui

import com.cursoragent.actions.AgentWindowCommand
import com.cursoragent.settings.AgentMode

internal data class ChatEntryTab(
    val id: String,
    val emptyAgent: Boolean,
    val hasSelections: Boolean,
    val lastShownNanos: Long?,
)

internal sealed interface ChatEntry {
    data object Hide : ChatEntry
    /** A null id creates an Agent; an existing id preserves its complete view/draft. */
    data class Focus(val id: String?, val insertSelection: Boolean) : ChatEntry
}

/** Cursor 3.23.12 pane behavior; native Git-worktree/editor branches need their own capabilities. */
internal fun chatEntry(
    command: AgentWindowCommand,
    tabs: List<ChatEntryTab>,
    selectedId: String,
    focused: Boolean,
    visible: Boolean,
    nowNanos: Long,
): ChatEntry {
    val selected = tabs.first { it.id == selectedId }
    return when (command) {
        AgentWindowCommand.OPEN_CHAT -> if (focused) ChatEntry.Hide else
            ChatEntry.Focus(selectedId, visible || !selected.hasSelections)
        AgentWindowCommand.FOLLOW_UP -> ChatEntry.Focus(selectedId, true)
        AgentWindowCommand.NEW_AGENT -> {
            val recent = selected.lastShownNanos?.let { nowNanos - it < 500_000_000L } == true
            if (focused && selected.emptyAgent && !recent) ChatEntry.Hide
            else ChatEntry.Focus(tabs.firstOrNull { it.emptyAgent }?.id, true)
        }
        else -> error("Not a chat entry command: $command")
    }
}

internal data class ChatResetTab(val id: String, val empty: Boolean, val hasHistory: Boolean, val hasDraft: Boolean, val mode: AgentMode)
internal data class ChatReset(val reuseId: String?, val copyDraft: Boolean, val mode: AgentMode)

/** The input's reset reuses an empty view; otherwise it replaces the current view, retaining its owner. */
internal fun chatReset(tabs: List<ChatResetTab>, selectedId: String): ChatReset {
    val current = tabs.first { it.id == selectedId }
    val reusable = current.takeIf { it.empty } ?: tabs.firstOrNull { it.empty }
    if (reusable != null) return ChatReset(reusable.id, false, reusable.mode)
    return ChatReset(null, current.hasHistory,
        if (current.hasHistory && current.hasDraft) current.mode else AgentMode.AGENT)
}
