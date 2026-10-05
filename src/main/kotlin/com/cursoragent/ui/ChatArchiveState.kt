package com.cursoragent.ui

import com.intellij.ide.util.PropertiesComponent

/** Project-local list metadata only; bodies, provider sessions and live owners keep their lifetimes. */
internal class ChatArchiveState(private val properties: PropertiesComponent) {
    fun apply(entry: RecentChatEntry): RecentChatEntry {
        val parts = properties.getValue(key(entry.id))?.split(':')
        val time = parts?.getOrNull(1)?.toLongOrNull()
        if (parts?.size != 2 || parts[0] !in setOf("0", "1") || time == null || time <= 0) return entry
        return entry.copy(archived = parts[0] == "1", archiveChangedMs = time, updatedMs = maxOf(entry.updatedMs, time))
    }

    fun apply(entries: List<RecentChatEntry>): List<RecentChatEntry> = entries.map(::apply)

    fun matches(entry: RecentChatEntry): Boolean {
        val current = apply(entry.copy(archived = false, archiveChangedMs = 0))
        return current.archived == entry.archived && current.archiveChangedMs == entry.archiveChangedMs
    }

    fun set(entry: RecentChatEntry, archived: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!matches(entry) || entry.archived == archived || nowMs <= 0 || entry.archiveChangedMs == Long.MAX_VALUE) return false
        properties.setValue(key(entry.id), "${if (archived) 1 else 0}:${maxOf(nowMs, entry.archiveChangedMs + 1)}")
        return true
    }

    private fun key(id: RecentChatId): String = "CursorAgent.chatArchive." + pinnedChatKey(id)
}
