package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.service.AgentTransport
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

internal fun recentChatEntries(
    visits: List<RecentChatId>,
    open: List<RecentChatEntry>,
    saved: List<Conversation>,
    legacy: List<ChatHistoryRecord>,
): List<RecentChatEntry> {
    val bodies = saved.map { RecentChatEntry(RecentChatId.Body(it.id), it.preview.ifBlank { "（本文なし）" }, it.updatedMs, it.transport, it.providerId) }
    val printIds = (open + bodies).filter { it.id is RecentChatId.Body && it.transport == AgentTransport.PRINT }.mapNotNull { it.providerId }.toSet()
    val old = legacy.filter { it.chatId !in printIds }.map {
        RecentChatEntry(RecentChatId.LegacyPrint(it.chatId), it.firstPromptPreview.ifBlank { "（本文なし）" }, it.lastUpdatedMs, null)
    }
    // Open views contain the latest metadata and drafts, including bodies not saved yet.
    val entries = (open + bodies + old).distinctBy { it.id }.associateBy { it.id }
    val visited = visits.mapNotNull(entries::get)
    val seen = visited.map { it.id }.toSet()
    return (visited + entries.values.filter { it.id !in seen }.sortedByDescending { it.updatedMs }).take(10)
}
