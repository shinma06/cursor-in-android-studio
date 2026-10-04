package com.cursoragent.ui

import com.cursoragent.actions.AgentWindowCommand

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
