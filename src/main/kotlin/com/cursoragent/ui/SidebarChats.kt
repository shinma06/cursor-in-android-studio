package com.cursoragent.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.Locale

internal enum class ChatSection(val key: String, val label: String) {
    CHATS("chats", "チャット"),
    PINNED("pinned", "固定したチャット"),
    TODAY("today", "今日"),
    YESTERDAY("yesterday", "昨日"),
    LAST_7_DAYS("last_7_days", "過去7日間"),
    LAST_30_DAYS("last_30_days", "過去30日間"),
    OLDER("older", "それ以前"),
    ARCHIVED("archived", "アーカイブ済み"),
}

internal sealed interface SidebarChatTarget {
    data class Chat(val id: RecentChatId) : SidebarChatTarget
    data class More(val section: ChatSection) : SidebarChatTarget
}

internal sealed interface AllChatRow {
    data class Chat(val hit: AllChatHit) : AllChatRow
    data class Section(val section: ChatSection, val count: Int, val collapsed: Boolean) : AllChatRow
    data class More(val section: ChatSection) : AllChatRow

    val target: SidebarChatTarget? get() = when (this) {
        is Chat -> SidebarChatTarget.Chat(hit.entry.id)
        is More -> SidebarChatTarget.More(section)
        is Section -> null
    }
}

/** Sidebar search is a case-insensitive substring, unlike quick access's fuzzy ranking. */
internal fun searchSidebarChats(entries: List<RecentChatEntry>, query: String): List<AllChatHit> {
    val text = query.trim().lowercase(Locale.ROOT)
    return entries.filter {
        it.title.lowercase(Locale.ROOT).contains(text) || it.description.lowercase(Locale.ROOT).contains(text)
    }.sortedByDescending { it.updatedMs }.map(::AllChatHit)
}

/** Today/yesterday are local calendar days; the remaining buckets use elapsed 7/30-day boundaries. */
internal fun chatDateSection(updatedMs: Long, nowMs: Long, zone: ZoneId): ChatSection {
    val date = Instant.ofEpochMilli(updatedMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val days = (nowMs.toDouble() - updatedMs.toDouble()) / 86_400_000.0
    return when {
        date == today -> ChatSection.TODAY
        date == today.minusDays(1) -> ChatSection.YESTERDAY
        days < 7 -> ChatSection.LAST_7_DAYS
        days < 30 -> ChatSection.LAST_30_DAYS
        else -> ChatSection.OLDER
    }
}

internal fun sidebarChatRows(
    hits: List<AllChatHit>,
    pinned: Set<RecentChatId>,
    collapsed: Set<ChatSection>,
    limits: Map<ChatSection, Int>,
    nowMs: Long,
    zone: ZoneId,
): List<AllChatRow> {
    val groups = hits.groupBy {
        when {
            it.entry.archived -> ChatSection.ARCHIVED
            it.entry.id in pinned -> ChatSection.PINNED
            else -> chatDateSection(it.entry.updatedMs, nowMs, zone)
        }
    }
    return buildList {
        ChatSection.entries.forEach { section ->
            val entries = groups[section]?.sortedByDescending { it.entry.updatedMs } ?: return@forEach
            add(AllChatRow.Section(section, entries.size, section in collapsed))
            if (section !in collapsed) {
                val limit = limits[section] ?: 6
                entries.take(limit).forEach { add(AllChatRow.Chat(it)) }
                if (entries.size > limit) add(AllChatRow.More(section))
            }
        }
    }
}

/** Count only available chats; stale stored pins do not create conversations or consume the active limit. */
internal fun togglePinnedChat(pinned: Set<RecentChatId>, id: RecentChatId, available: Set<RecentChatId>): Set<RecentChatId>? = when {
    id !in available -> null
    id in pinned -> pinned - id
    pinned.count { it in available } >= 75 -> null
    else -> pinned + id
}

/** Encode opaque IDs rather than using titles, paths or delimiter-sensitive raw property values. */
internal fun pinnedChatKey(id: RecentChatId): String {
    val (kind, value) = when (id) {
        is RecentChatId.Body -> "body:" to id.id
        is RecentChatId.LegacyPrint -> "print:" to id.id
    }
    return kind + Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}

internal fun pinnedChatId(value: String): RecentChatId? = try {
    val kind = value.substringBefore(':')
    val id = String(Base64.getUrlDecoder().decode(value.substringAfter(':')), Charsets.UTF_8)
    val decoded = if (id.isEmpty()) null else when (kind) {
        "body" -> RecentChatId.Body(id)
        "print" -> RecentChatId.LegacyPrint(id)
        else -> null
    }
    decoded?.takeIf { pinnedChatKey(it) == value }
} catch (_: IllegalArgumentException) { null }

/** Header history has independent 20-row pages; pins precede the other normal chats. */
internal fun historyChatRows(
    hits: List<AllChatHit>,
    pinned: Set<RecentChatId>,
    archivedCollapsed: Boolean,
    limits: Map<ChatSection, Int>,
    showEmptyArchive: Boolean,
): List<AllChatRow> = buildList {
    fun page(section: ChatSection, entries: List<AllChatHit>) {
        val limit = limits[section] ?: 20
        entries.take(limit).forEach { add(AllChatRow.Chat(it)) }
        if (entries.size > limit) add(AllChatRow.More(section))
    }
    page(ChatSection.CHATS, hits.filterNot { it.entry.archived }
        .sortedWith(compareByDescending<AllChatHit> { it.entry.id in pinned }.thenByDescending { it.entry.updatedMs }))
    val archived = hits.filter { it.entry.archived }.sortedByDescending { it.entry.updatedMs }
    if (archived.isNotEmpty() || showEmptyArchive) {
        add(AllChatRow.Section(ChatSection.ARCHIVED, archived.size, archivedCollapsed))
        if (!archivedCollapsed) page(ChatSection.ARCHIVED, archived)
    }
}

/** The sidebar's neighbor uses every filtered section item, including collapsed and paged-out rows. */
internal fun archiveNeighbor(entries: List<RecentChatEntry>, current: RecentChatId): RecentChatEntry? {
    val index = entries.indexOfFirst { it.id == current }
    if (index < 0) return null
    return (entries.getOrNull(index + 1) ?: entries.getOrNull(index - 1))?.takeUnless { it.archived }
}
