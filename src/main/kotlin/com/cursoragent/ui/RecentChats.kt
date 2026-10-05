package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTab
import com.cursoragent.settings.ChatHistoryRecord
import java.text.SimpleDateFormat
import java.util.Date

/** Provider print IDs and local body IDs are different namespaces, even when their strings match. */
internal sealed interface RecentChatId {
    data class Body(val id: String) : RecentChatId
    data class LegacyPrint(val id: String) : RecentChatId
}

/** Metadata only. Opening a closed conversation must reload its body through ConversationHistory. */
internal data class RecentChatEntry(
    val id: RecentChatId,
    val title: String,
    val updatedMs: Long,
    val transport: AgentTransport?,
    val providerId: String? = null,
    val open: Boolean = false,
    val running: Boolean = false,
    val description: String = "",
    val archived: Boolean = false,
    val archiveChangedMs: Long = 0,
) {
    val label: String get() = " ${title.replace('\n', ' ').replace('\r', ' ').take(120)} " +
        (if (updatedMs > 0) "(${SimpleDateFormat("MM/dd HH:mm").format(Date(updatedMs))}) " else "") +
        "[${if (id is RecentChatId.LegacyPrint) "旧履歴・本文なし" else transport}]" +
        (if (running) "（実行中）" else if (open) "（開いている）" else "")
}

/** Cursor's runtime visit list is bounded to ten; visual tab moves and background updates are not visits. */
internal class RecentChatVisits {
    private val ids = mutableListOf<RecentChatId>()

    fun visit(id: RecentChatId) {
        ids.remove(id)
        ids.add(0, id)
        if (ids.size > 10) ids.removeLast()
    }

    fun snapshot(): List<RecentChatId> = ids.toList()
}

internal fun availableChatEntries(
    open: List<RecentChatEntry>,
    saved: List<Conversation>,
    legacy: List<ChatHistoryRecord>,
): List<RecentChatEntry> {
    val bodies = saved.map { RecentChatEntry(RecentChatId.Body(it.id), it.preview.ifBlank { "（本文なし）" }, it.updatedMs, it.transport, it.providerId) }
    val old = legacy.map {
        RecentChatEntry(RecentChatId.LegacyPrint(it.chatId), it.firstPromptPreview.ifBlank { "（本文なし）" }, it.lastUpdatedMs, null)
    }
    // Open views contain the latest metadata and drafts, including bodies not saved yet.
    return mergeChatEntries(open, bodies + old)
}

internal fun mergeChatEntries(open: List<RecentChatEntry>, stored: List<RecentChatEntry>): List<RecentChatEntry> {
    val entries = open + stored
    val printIds = entries.filter { it.id is RecentChatId.Body && it.transport == AgentTransport.PRINT }.mapNotNull { it.providerId }.toSet()
    return entries.filterNot { it.id is RecentChatId.LegacyPrint && it.id.id in printIds }.distinctBy { it.id }
}

internal fun recentChatEntries(
    visits: List<RecentChatId>,
    open: List<RecentChatEntry>,
    saved: List<Conversation>,
    legacy: List<ChatHistoryRecord>,
): List<RecentChatEntry> = recentChatEntries(visits, availableChatEntries(open, saved, legacy))

internal fun recentChatEntries(visits: List<RecentChatId>, available: List<RecentChatEntry>): List<RecentChatEntry> {
    val entries = available.filterNot { it.archived }.associateBy { it.id }
    val visited = visits.mapNotNull(entries::get)
    val seen = visited.map { it.id }.toSet()
    return (visited + entries.values.filter { it.id !in seen }.sortedByDescending { it.updatedMs }).take(10)
}

/** One visible tab navigates all available history by update time, not the ten-item visit list. */
internal fun adjacentSavedChat(entries: List<RecentChatEntry>, selected: RecentChatId, reverse: Boolean): RecentChatId? {
    val ordered = entries.filterNot { it.archived }.sortedByDescending { it.updatedMs }
    val index = ordered.indexOfFirst { it.id == selected }
    if (index < 0 || ordered.size < 2) return null
    return ordered[Math.floorMod(index + if (reverse) -1 else 1, ordered.size)].id
}

/** A running token and a manually named tab must survive even if its input looks empty. */
internal fun canReplaceNavigationTab(tab: SessionTab, pendingWork: Boolean): Boolean =
    tab.run == null && !tab.renamedByUser && !pendingWork
