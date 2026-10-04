package com.cursoragent.ui

import com.cursoragent.service.AgentTransport
import com.intellij.openapi.actionSystem.KeyboardShortcut
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

class AllChatsTest {
    @Test
    fun `quick access sorts all history by update time and searches before limiting to 200`() {
        val entries = (1..240).map { entry("$it", "chat $it", it.toLong()) }
        assertEquals((240 downTo 41).map { RecentChatId.Body("$it") }, searchAllChats(entries, "  ").map { it.entry.id })
        val oldest = entries.first().copy(title = "Rare conversation")
        assertEquals(listOf(oldest), searchAllChats(entries.drop(1) + oldest, "rare").map { it.entry })
        assertEquals(240, searchAllChats(entries, "", Int.MAX_VALUE).size)
        assertTrue(searchAllChats(entries, "missing").isEmpty())
    }

    @Test
    fun `native fuzzy matching searches names and descriptions and returns safe title highlights`() {
        val title = entry("name", "Android Build", 1)
        val description = entry("description", "New Agent", 2).copy(description = "Android Build settings")
        val japanese = entry("japanese", "日本語の検索", 3)
        val hits = searchAllChats(listOf(title, description, japanese), "andb")
        assertEquals(setOf(title.id, description.id), hits.map { it.entry.id }.toSet())
        val highlighted = hits.first { it.entry.id == title.id }
        assertTrue(highlighted.highlights.isNotEmpty())
        highlighted.highlights.forEach { assertTrue(it.startOffset >= 0 && it.endOffset <= title.title.length) }
        assertTrue(hits.first { it.entry.id == description.id }.highlights.isEmpty())
        assertEquals(listOf(japanese), searchAllChats(listOf(title, description, japanese), "日本語").map { it.entry })
    }

    @Test
    fun `sidebar filtered results retain update order while quick access uses match relevance`() {
        val exact = entry("exact", "Build", 1)
        val recent = entry("recent", "Long older prefix Build suffix", 100)
        assertEquals(exact, searchAllChats(listOf(recent, exact), "Build").first().entry)
        assertEquals(listOf(recent, exact), searchAllChats(listOf(exact, recent), "Build", Int.MAX_VALUE, rankMatches = false).map { it.entry })
    }

    @Test
    fun `merging metadata retains live names and suppresses only matching print legacy aliases`() {
        val open = entry("body", "Manual name", 3).copy(providerId = "provider", description = "first prompt")
        val saved = open.copy(title = "old", updatedMs = 1)
        val legacy = RecentChatEntry(RecentChatId.LegacyPrint("provider"), "legacy", 2, null)
        assertEquals(listOf(open), mergeChatEntries(listOf(open, legacy), listOf(saved)))
        assertEquals(2, mergeChatEntries(listOf(open.copy(transport = AgentTransport.ACP), legacy), emptyList()).size)
        assertEquals(open.description, searchAllChats(mergeChatEntries(listOf(open), listOf(saved)), "first prompt").single().entry.description)
    }

    @Test
    fun `sidebar navigation highlights relative to current chat and stops at the ends`() {
        val ids = (1..3).map { RecentChatId.Body("$it") }
        assertEquals(ids[1], adjacentSidebarChat(ids, ids[0], null, false))
        assertEquals(ids[2], adjacentSidebarChat(ids, ids[0], ids[1], false))
        assertEquals(ids[2], adjacentSidebarChat(ids, ids[0], ids[2], false))
        assertEquals(ids[0], adjacentSidebarChat(ids, ids[0], null, true))
        assertEquals(ids[2], adjacentSidebarChat(ids, null, null, true))
        assertEquals(ids[0], adjacentSidebarChat(ids, null, null, false))
        assertNull(adjacentSidebarChat(emptyList(), null, null, false))
    }

    @Test
    fun `sidebar release waits for both Ctrl and Meta and respects custom or multi stroke keys`() {
        val ctrl = shortcut("control alt LEFT")
        assertFalse(acceptsSidebarChatRelease(release(KeyEvent.VK_ALT, InputEvent.CTRL_DOWN_MASK), listOf(ctrl)))
        assertFalse(acceptsSidebarChatRelease(release(KeyEvent.VK_CONTROL, InputEvent.META_DOWN_MASK), listOf(ctrl)))
        assertTrue(acceptsSidebarChatRelease(release(KeyEvent.VK_CONTROL, InputEvent.ALT_DOWN_MASK), listOf(ctrl)))
        assertTrue(acceptsSidebarChatRelease(release(KeyEvent.VK_META, 0), listOf(shortcut("meta alt RIGHT"))))
        assertTrue(acceptsSidebarChatRelease(release(KeyEvent.VK_ALT, 0), listOf(shortcut("alt RIGHT"))))
        assertFalse(acceptsSidebarChatRelease(release(KeyEvent.VK_CONTROL, 0), listOf(KeyboardShortcut(KeyStroke.getKeyStroke("control K"), KeyStroke.getKeyStroke("LEFT")))))
        assertFalse(acceptsSidebarChatRelease(release(KeyEvent.VK_CONTROL, 0), emptyList()))
    }

    private fun entry(id: String, title: String, updated: Long) = RecentChatEntry(RecentChatId.Body(id), title, updated, AgentTransport.PRINT)
    private fun shortcut(value: String) = KeyboardShortcut(KeyStroke.getKeyStroke(value), null)
    private fun release(key: Int, modifiers: Int) = KeyEvent(JPanel(), KeyEvent.KEY_RELEASED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED)
}
