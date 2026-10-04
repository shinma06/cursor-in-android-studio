package com.cursoragent.ui

import com.cursoragent.history.ChatMessage
import com.cursoragent.history.Conversation
import com.cursoragent.history.SavedTurn
import com.cursoragent.service.AgentTransport
import com.cursoragent.settings.ChatHistoryRecord
import com.intellij.openapi.actionSystem.KeyboardShortcut
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

class RecentChatsTest {
    @Test
    fun `visit order is distinct from tab order bounded and detached`() {
        val visits = RecentChatVisits()
        val ids = (0..11).map { RecentChatId.Body("chat-$it") }
        ids.forEach(visits::visit)
        assertEquals(ids.reversed().take(10), visits.snapshot())
        val frozen = visits.snapshot()
        visits.visit(ids[5])
        assertEquals(listOf(ids[5]) + frozen.filterNot { it == ids[5] }, visits.snapshot())
        assertEquals(ids.reversed().take(10), frozen)
        visits.visit(RecentChatId.LegacyPrint(ids[5].id))
        assertEquals(RecentChatId.LegacyPrint(ids[5].id), visits.snapshot().first())
        assertTrue(ids[5] in visits.snapshot())
        assertEquals(10, visits.snapshot().size)
    }

    @Test
    fun `picker includes closed saved visits and fresh open metadata before fallback by update time`() {
        val saved = listOf(conversation("a", 100), conversation("b", 300), conversation("c", 200))
        val open = listOf(RecentChatEntry(RecentChatId.Body("a"), "current renamed tab", 999, AgentTransport.PRINT, open = true),
            RecentChatEntry(RecentChatId.Body("unsent"), "New Agent", 250, AgentTransport.PRINT, open = true))
        val visits = listOf(RecentChatId.Body("a"), RecentChatId.Body("c"), RecentChatId.Body("deleted"))
        val entries = recentChatEntries(visits, open, saved, listOf(ChatHistoryRecord("old", "legacy", 400)))
        assertEquals(listOf(RecentChatId.Body("a"), RecentChatId.Body("c"), RecentChatId.LegacyPrint("old"),
            RecentChatId.Body("b"), RecentChatId.Body("unsent")), entries.map { it.id })
        assertEquals("current renamed tab", entries.first().title)
        assertTrue(entries.first().open)
        assertFalse(entries[1].open)
        assertEquals(saved[2].preview, entries[1].title)
    }

    @Test
    fun `print legacy deduplication never removes ACP with the same provider string`() {
        val acp = conversation("acp", 500).copy(transport = AgentTransport.ACP, providerId = "provider")
        val old = ChatHistoryRecord("provider", "old print", 100)
        assertEquals(2, recentChatEntries(emptyList(), emptyList(), listOf(acp), listOf(old)).size)
        val print = conversation("print", 200).copy(providerId = "provider")
        val entries = recentChatEntries(emptyList(), emptyList(), listOf(acp, print), listOf(old))
        assertEquals(listOf(RecentChatId.Body("acp"), RecentChatId.Body("print")), entries.map { it.id })
        val openPrint = RecentChatEntry(RecentChatId.Body("pending-save"), "live", 600, AgentTransport.PRINT, "provider", open = true)
        assertEquals(listOf(openPrint.id, RecentChatId.Body("acp")),
            recentChatEntries(emptyList(), listOf(openPrint), listOf(acp), listOf(old)).map { it.id })
    }

    @Test
    fun `missing closed drafts disappear and fallback fills only ten entries`() {
        val saved = (1..15).map { conversation("$it", it.toLong()) }
        val visits = listOf(RecentChatId.Body("closed-unsaved"), RecentChatId.Body("2"))
        val entries = recentChatEntries(visits, emptyList(), saved, emptyList())
        assertEquals(10, entries.size)
        assertEquals(listOf("2") + (15 downTo 7).map(Int::toString), entries.map { (it.id as RecentChatId.Body).id })
        assertTrue(recentChatEntries(visits, emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `modifier release follows configured single stroke keys and preserves shift direction changes`() {
        val ctrlTab = shortcut("control TAB")
        val reverse = shortcut("control shift TAB")
        assertTrue(release(KeyEvent.VK_CONTROL, 0, ctrlTab))
        assertTrue(release(KeyEvent.VK_CONTROL, InputEvent.SHIFT_DOWN_MASK, reverse))
        assertFalse(release(KeyEvent.VK_META, 0, ctrlTab))
        assertFalse(release(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK, ctrlTab))
        assertFalse(release(KeyEvent.VK_SHIFT, InputEvent.CTRL_DOWN_MASK, reverse))
        assertTrue(release(KeyEvent.VK_SHIFT, 0, reverse))
        assertTrue(release(KeyEvent.VK_META, 0, shortcut("meta J")))
        assertTrue(release(KeyEvent.VK_ALT, InputEvent.CTRL_DOWN_MASK, shortcut("control alt J")))
        assertFalse(release(KeyEvent.VK_CONTROL, 0, KeyboardShortcut(KeyStroke.getKeyStroke("control K"), KeyStroke.getKeyStroke("control J"))))
        assertFalse(acceptsRecentChatRelease(KeyEvent(JPanel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED), listOf(ctrlTab)))
        assertFalse(acceptsRecentChatRelease(KeyEvent(JPanel(), KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED), emptyList()))
    }

    private fun conversation(id: String, time: Long) = Conversation(id = id, updatedMs = time,
        turns = listOf(SavedTurn(messages = listOf(ChatMessage(role = "user", text = "prompt $id")))))

    private fun shortcut(key: String) = KeyboardShortcut(KeyStroke.getKeyStroke(key), null)
    private fun release(key: Int, modifiers: Int, shortcut: KeyboardShortcut) =
        acceptsRecentChatRelease(KeyEvent(JPanel(), KeyEvent.KEY_RELEASED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED), listOf(shortcut))
}
