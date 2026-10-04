package com.cursoragent.ui

import com.cursoragent.service.AgentTransport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SidebarChatsTest {
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
