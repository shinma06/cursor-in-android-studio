package com.cursoragent.ui

import com.cursoragent.service.AgentTransport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SidebarChatsTest {
    @Test
    fun `prior archive uses strict timestamps and excludes pins archived and duplicate identities`() {
        val older = hit("older", 9).entry
        val legacy = older.copy(id = RecentChatId.LegacyPrint("older"))
        val pinned = hit("pin", 1).entry
        val candidates = listOf(hit("equal", 10).entry, hit("newer", 11).entry, older, older,
            pinned, hit("archived", 1).entry.copy(archived = true), legacy)
        assertEquals(listOf(older, legacy), priorArchiveCandidates(candidates, 10, setOf(pinned.id)))
        assertEquals(listOf(legacy), priorArchiveCandidates(candidates, 10, setOf(pinned.id, older.id)))
        assertTrue(priorArchiveCandidates(candidates, 0, emptySet()).isEmpty())
    }

    @Test
    fun `archive grouping wins over pins and header history has a separate collapsed section`() {
        val normal = hit("normal", now - 1)
        val archived = (1..8).map { hit("archive-$it", now + it).let { hit -> hit.copy(entry = hit.entry.copy(archived = true)) } }
        val rows = rows(listOf(normal) + archived, pinned = archived.map { it.entry.id }.toSet(), collapsed = setOf(ChatSection.ARCHIVED))
        assertEquals(listOf(ChatSection.TODAY, ChatSection.ARCHIVED), rows.filterIsInstance<AllChatRow.Section>().map { it.section })
        assertEquals(listOf(normal), rows.filterIsInstance<AllChatRow.Chat>().map { it.hit })
        assertEquals(AllChatRow.Section(ChatSection.ARCHIVED, 8, true), rows.last())
        assertEquals(6, rows(archived).filterIsInstance<AllChatRow.Chat>().size)
        val hits = archived + normal
        assertEquals(listOf(AllChatRow.Chat(normal), AllChatRow.Section(ChatSection.ARCHIVED, 8, true)), historyChatRows(hits, emptySet(), true, emptyMap(), true))
        assertEquals(listOf(normal) + archived.reversed(), historyChatRows(hits, emptySet(), false, emptyMap(), true).filterIsInstance<AllChatRow.Chat>().map { it.hit })
    }

    @Test
    fun `history pages twenty rows in each partition and keeps pins ahead of newer normal chats`() {
        val normal = (1..41).map { hit("normal-$it", now + it) }
        val archived = (1..22).map { hit("archived-$it", now + it).let { hit -> hit.copy(entry = hit.entry.copy(archived = true)) } }
        val pins = setOf(normal.first().entry.id, archived.first().entry.id)
        val rows = historyChatRows(normal + archived, pins, false, emptyMap(), true)
        val chats = rows.filterIsInstance<AllChatRow.Chat>()
        assertEquals(40, chats.size)
        assertEquals(normal.first(), chats.first().hit)
        assertEquals(listOf(ChatSection.CHATS, ChatSection.ARCHIVED), rows.filterIsInstance<AllChatRow.More>().map { it.section })
        val expanded = historyChatRows(normal + archived, pins, false, mapOf(ChatSection.CHATS to 40), true)
        assertEquals(60, expanded.filterIsInstance<AllChatRow.Chat>().size)
        assertEquals(listOf(AllChatRow.Section(ChatSection.ARCHIVED, 0, true)), historyChatRows(emptyList(), emptySet(), true, emptyMap(), true))
        assertTrue(historyChatRows(emptyList(), emptySet(), true, emptyMap(), false).isEmpty())
    }

    @Test
    fun `archive neighbor follows all section items with next then previous and never skips archived neighbor`() {
        val normal = (1..8).map { hit("normal-$it", now + it) }
        val old = hit("old", now - 100 * day)
        val archived = hit("archived", now).let { it.copy(entry = it.entry.copy(archived = true)) }
        val hits = normal + old + archived
        val all = sidebarChatRows(hits, setOf(old.entry.id), emptySet(), ChatSection.entries.associateWith { Int.MAX_VALUE }, now, utc)
            .filterIsInstance<AllChatRow.Chat>().map { it.hit.entry }
        assertEquals(normal.last().entry, archiveNeighbor(all, old.entry.id))
        assertEquals(normal[1].entry, archiveNeighbor(all, normal[2].entry.id), "paged-out row remains a candidate")
        assertNull(archiveNeighbor(all, normal.first().entry.id), "an archived next item means a new chat, not a scan backward")
        assertEquals(normal.first().entry, archiveNeighbor(all, archived.entry.id))
        assertNull(archiveNeighbor(all, RecentChatId.Body("missing")))
        assertNull(archiveNeighbor(listOf(old.entry), old.entry.id))
    }

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L

    @Test
    fun `local today and yesterday precede elapsed seven and thirty day boundaries`() {
        assertEquals(ChatSection.TODAY, chatDateSection(now - 1, now, utc))
        assertEquals(ChatSection.YESTERDAY, chatDateSection(now - day, now, utc))
        assertEquals(ChatSection.LAST_7_DAYS, chatDateSection(now - 7 * day + 1, now, utc))
        assertEquals(ChatSection.LAST_30_DAYS, chatDateSection(now - 7 * day, now, utc))
        assertEquals(ChatSection.LAST_30_DAYS, chatDateSection(now - 30 * day + 1, now, utc))
        assertEquals(ChatSection.OLDER, chatDateSection(now - 30 * day, now, utc))
        assertEquals(ChatSection.OLDER, chatDateSection(0, now, utc))
        val midnightTokyo = Instant.parse("2026-10-04T15:05:00Z").toEpochMilli()
        assertEquals(ChatSection.YESTERDAY, chatDateSection(midnightTokyo - 10 * 60_000L, midnightTokyo, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `yesterday remains a calendar day across daylight saving clock changes`() {
        val zone = ZoneId.of("America/New_York")
        val today = ZonedDateTime.of(2026, 11, 2, 0, 15, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = ZonedDateTime.of(2026, 11, 1, 0, 10, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(today - yesterday > day)
        assertEquals(ChatSection.YESTERDAY, chatDateSection(yesterday, today, zone))
    }

    @Test
    fun `pinned chats appear once before dated groups and every group starts at six`() {
        val today = (1..8).map { hit("today-$it", now - it) }
        val oldPin = hit("old-pin", now - 50 * day)
        val rows = rows(today + oldPin, pinned = setOf(oldPin.entry.id))
        assertEquals(listOf(ChatSection.PINNED, ChatSection.TODAY), rows.filterIsInstance<AllChatRow.Section>().map { it.section })
        assertEquals(oldPin, (rows[1] as AllChatRow.Chat).hit)
        assertEquals(today.take(6), rows.filterIsInstance<AllChatRow.Chat>().drop(1).map { it.hit })
        assertEquals(AllChatRow.More(ChatSection.TODAY), rows.last())
        assertEquals(1, rows.filterIsInstance<AllChatRow.Chat>().count { it.hit.entry.id == oldPin.entry.id })
    }

    @Test
    fun `collapsed sections hide both chats and More while expansion adds exactly six`() {
        val hits = (1..13).map { hit("$it", now - it) }
        val collapsed = rows(hits, collapsed = setOf(ChatSection.TODAY))
        assertEquals(listOf(AllChatRow.Section(ChatSection.TODAY, 13, true)), collapsed)
        assertTrue(collapsed.mapNotNull { it.target }.isEmpty())
        val expanded = rows(hits, limits = mapOf(ChatSection.TODAY to 12))
        assertEquals(12, expanded.filterIsInstance<AllChatRow.Chat>().size)
        assertTrue(expanded.last() is AllChatRow.More)
        assertFalse(rows(hits, limits = mapOf(ChatSection.TODAY to 18)).any { it is AllChatRow.More })
    }

    @Test
    fun `navigation traverses visible chats and More without treating headers as chat IDs`() {
        val hits = (1..7).map { hit("$it", now - it) }
        val targets = rows(hits).mapNotNull { it.target }
        val last = SidebarChatTarget.Chat(hits[5].entry.id)
        val more = SidebarChatTarget.More(ChatSection.TODAY)
        assertEquals(more, adjacentSidebarChat(targets, last, null, false))
        assertEquals(more, adjacentSidebarChat(targets, last, more, false))
        assertEquals(last, adjacentSidebarChat(targets, null, more, true))
        assertNull(adjacentSidebarChat(rows(hits, collapsed = setOf(ChatSection.TODAY)).mapNotNull { it.target }, last, null, false))
    }

    @Test
    fun `sidebar search is literal substring rather than the quick access fuzzy query`() {
        val title = hit("name", now).entry.copy(title = "Android Build")
        val description = hit("description", now - 1).entry.copy(title = "New Agent", description = "日本語の検索")
        assertEquals(listOf(title), searchSidebarChats(listOf(description, title), "  ANDROID  ").map { it.entry })
        assertEquals(listOf(description), searchSidebarChats(listOf(title, description), "日本語").map { it.entry })
        assertTrue(searchSidebarChats(listOf(title), "andb").isEmpty())
        assertTrue(searchAllChats(listOf(title), "andb").isNotEmpty())
        assertEquals(listOf(title, description), searchSidebarChats(listOf(description, title), "").map { it.entry })
    }

    @Test
    fun `pin limit counts available identities and always permits unpinning`() {
        val available = (1..76).map { RecentChatId.Body("$it") }.toSet<RecentChatId>()
        val first75 = available.take(75).toSet()
        val next = available.last()
        assertNull(togglePinnedChat(first75, next, available))
        val unpinned = togglePinnedChat(first75, first75.first(), available)!!
        assertEquals(74, unpinned.size)
        assertEquals(75, togglePinnedChat(unpinned, next, available)!!.size)
        val stale = RecentChatId.LegacyPrint("missing")
        assertEquals(setOf(stale, next), togglePinnedChat(setOf(stale), next, available))
        assertNull(togglePinnedChat(first75, stale, available))
        val body = RecentChatId.Body("same")
        val legacy = RecentChatId.LegacyPrint("same")
        assertEquals(setOf(body, legacy), togglePinnedChat(setOf(body), legacy, setOf(body, legacy)))
        val archived = RecentChatId.Body("archived")
        assertNull(togglePinnedChat(first75, archived, available + archived, available))
        val mixed = first75 + archived
        assertEquals(first75, togglePinnedChat(mixed, archived, available + archived, available))
        assertEquals(unpinned + archived, togglePinnedChat(unpinned, archived, available + archived, available))
        assertEquals(unpinned + archived + next, togglePinnedChat(unpinned + archived, next, available + archived, available))
    }

    @Test
    fun `pin persistence preserves opaque namespaces and ignores malformed entries`() {
        for (id in listOf(RecentChatId.Body("same:日本語"), RecentChatId.LegacyPrint("same:日本語"))) {
            assertEquals(id, pinnedChatId(pinnedChatKey(id)))
        }
        assertNotEquals(pinnedChatKey(RecentChatId.Body("same")), pinnedChatKey(RecentChatId.LegacyPrint("same")))
        for (invalid in listOf("", "body:", "unknown:YQ", "body:not base64", "body:_w", "body:YQ==")) assertNull(pinnedChatId(invalid))
    }

    private fun hit(id: String, time: Long) = AllChatHit(RecentChatEntry(RecentChatId.Body(id), id, time, AgentTransport.PRINT))
    private fun rows(hits: List<AllChatHit>, pinned: Set<RecentChatId> = emptySet(), collapsed: Set<ChatSection> = emptySet(), limits: Map<ChatSection, Int> = emptyMap()) =
        sidebarChatRows(hits, pinned, collapsed, limits, now, utc)
}
