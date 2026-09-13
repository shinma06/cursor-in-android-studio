package com.cursoragent.session

import com.cursoragent.settings.AgentMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionTabsTest {
    @Test
    fun `failed initial ACP preparation releases transport but a bound provider or stale token cannot`() {
        val sessions = SessionTabs()
        val tab = sessions.snapshot().selectedId
        sessions.selectTransport(tab, com.cursoragent.service.AgentTransport.ACP)
        sessions.updateComposer(tab, com.cursoragent.settings.AgentMode.AGENT, "", "prompt", 6)
        val first = sessions.beginTurn(tab)!!
        assertTrue(sessions.abortUnsentAcpTurn(first.token))
        assertTrue(sessions.selectTransport(tab, com.cursoragent.service.AgentTransport.PRINT))
        assertFalse(sessions.abortUnsentAcpTurn(first.token))
        sessions.selectTransport(tab, com.cursoragent.service.AgentTransport.ACP)
        sessions.updateComposer(tab, com.cursoragent.settings.AgentMode.AGENT, "", "prompt", 6)
        val sent = sessions.beginTurn(tab)!!
        sessions.bindChat(sent.token, "provider")
        assertFalse(sessions.abortUnsentAcpTurn(sent.token))
        assertFalse(sessions.selectTransport(tab, com.cursoragent.service.AgentTransport.PRINT))
    }

    @Test
    fun `transport belongs to tab and locks on first send including failed or stopped turns`() {
        val tabs = SessionTabs()
        val first = tabs.snapshot().selected.id
        assertTrue(tabs.selectTransport(first, com.cursoragent.service.AgentTransport.ACP))
        tabs.updateComposer(first, com.cursoragent.settings.AgentMode.AGENT, "", "hello", 5)
        val turn = tabs.beginTurn(first)!!
        assertEquals(com.cursoragent.service.AgentTransport.ACP, turn.transport)
        assertFalse(tabs.selectTransport(first, com.cursoragent.service.AgentTransport.PRINT))
        tabs.finishTurn(turn.token)
        assertFalse(tabs.selectTransport(first, com.cursoragent.service.AgentTransport.PRINT))
        val old = tabs.open("legacy-print")
        assertEquals(com.cursoragent.service.AgentTransport.PRINT, old.transport)
        assertFalse(tabs.selectTransport(old.id, com.cursoragent.service.AgentTransport.ACP))
    }

    @Test
    fun `switch and reorder preserve independent composer values and selection`() {
        val store = SessionTabs(AgentMode.AGENT, "auto")
        val first = store.snapshot().selected.id
        store.updateComposer(first, AgentMode.ASK, "model-a", "未送信の質問", 3)
        val second = store.open().id
        store.updateComposer(second, AgentMode.PLAN, "model-b", "計画", 999)
        store.select(first)
        assertEquals("未送信の質問", store.snapshot().selected.draft)
        assertEquals(AgentMode.ASK, store.snapshot().selected.mode)
        assertEquals("model-a", store.snapshot().selected.modelId)
        assertEquals(3, store.snapshot().selected.caret)
        assertTrue(store.move(first, 1))
        assertEquals(listOf(second, first), store.snapshot().tabs.map { it.id })
        assertEquals(first, store.snapshot().selectedId)
        store.select(second)
        assertEquals(2, store.snapshot().selected.caret)
        assertEquals(AgentMode.PLAN, store.snapshot().selected.mode)
    }

    @Test
    fun `active close chooses right then left and last close creates fresh defaults`() {
        val store = SessionTabs(initialModelId = "default")
        val a = store.snapshot().selected.id
        val b = store.open().id
        val c = store.open().id
        store.select(b)
        store.close(b)
        assertEquals(c, store.snapshot().selectedId)
        store.close(c)
        assertEquals(a, store.snapshot().selectedId)
        store.updateComposer(a, AgentMode.ASK, "other", "draft", 1)
        store.close(a)
        val replacement = store.snapshot().selected
        assertNotEquals(a, replacement.id)
        assertEquals(SessionTab.NEW_AGENT_TITLE, replacement.title)
        assertNull(replacement.chatId)
        assertEquals("default", replacement.modelId)
        assertEquals("", replacement.draft)
    }

    @Test
    fun `inactive close and invalid operations leave selected state alone`() {
        val store = SessionTabs()
        val a = store.snapshot().selected.id
        val b = store.open().id
        store.close(a)
        val before = store.snapshot()
        assertEquals(b, before.selectedId)
        assertFalse(store.select("missing"))
        assertNull(store.close("missing"))
        assertFalse(store.move(b, -1))
        assertFalse(store.move(b, 1))
        assertFalse(store.move(b, 0))
        assertFalse(store.updateComposer("missing", AgentMode.ASK, "", "", 0))
        assertEquals(before, store.snapshot())
    }

    @Test
    fun `turn settings and resume id are frozen before background preparation`() {
        val store = SessionTabs()
        val tab = store.open("cli-session")
        store.updateComposer(tab.id, AgentMode.PLAN, "model-1", "  元の本文\n", 0)
        val turn = store.beginTurn(tab.id)!!
        assertEquals("", store.snapshot().selected.draft)
        store.updateComposer(tab.id, AgentMode.ASK, "model-2", "次の下書き", -1)
        assertEquals(AgentMode.PLAN, turn.mode)
        assertEquals("model-1", turn.modelId)
        assertEquals("cli-session", turn.chatId)
        assertEquals("  元の本文\n", turn.prompt)
        assertNull(store.beginTurn(tab.id))
        store.finishTurn(turn.token)
        assertEquals("次の下書き", store.beginTurn(tab.id)!!.prompt)
    }

    @Test
    fun `late events after stop next turn and close cannot touch another tab`() {
        val store = SessionTabs()
        val a = store.snapshot().selected.id
        val old = start(store, a)
        val b = store.open().id
        val other = start(store, b)
        assertTrue(store.bindChat(old, "chat-a"))
        assertTrue(store.bindChat(other, "chat-b"))
        assertSame(old, store.stop(a))
        val current = start(store, a)
        assertFalse(store.accepts(old))
        assertFalse(store.bindChat(old, "late-chat"))
        assertFalse(store.applyAutomaticTitle(old, "古い名前"))
        assertFalse(store.finishTurn(old))
        assertTrue(store.accepts(current))
        assertTrue(store.accepts(other))
        assertSame(current, store.close(a)!!.run)
        assertFalse(store.accepts(current))
        assertTrue(store.accepts(other))
        assertEquals("chat-b", store.snapshot().selected.chatId)
    }

    @Test
    fun `session identity remains unique when history resumes and callbacks arrive`() {
        val store = SessionTabs()
        val a = store.open("existing", "保存名")
        store.updateComposer(a.id, AgentMode.ASK, "model", "保留", 1)
        val b = store.open().id
        val token = start(store, b)
        assertFalse(store.bindChat(token, "existing"))
        assertFalse(store.bindChat(token, " "))
        assertTrue(store.bindChat(token, "new"))
        assertFalse(store.bindChat(token, "changed"))
        assertEquals(a.id, store.open("existing", "別名").id)
        assertEquals("保留", store.snapshot().selected.draft)
        assertEquals(3, store.snapshot().tabs.size)
        assertThrows(IllegalArgumentException::class.java) { store.open("") }
    }

    @Test
    fun `manual names survive later automatic names and empty names are ignored`() {
        val store = SessionTabs()
        val id = store.snapshot().selected.id
        val token = start(store, id)
        assertTrue(store.applyAutomaticTitle(token, "Cursorの名前"))
        assertTrue(store.rename(id, "  自分の名前  "))
        assertFalse(store.applyAutomaticTitle(token, "後着の自動名"))
        assertFalse(store.rename(id, " \n "))
        assertEquals("自分の名前", store.snapshot().selected.title)
        assertTrue(store.snapshot().selected.renamedByUser)
    }

    @Test
    fun `tokens cannot be used across stores or after stop all`() {
        val store = SessionTabs()
        val a = store.snapshot().selected.id
        val first = start(store, a)
        val second = start(store, store.open().id)
        assertFalse(SessionTabs().accepts(first))
        assertFalse(store.accepts(SessionRunToken(a)))
        assertEquals(listOf(first, second), store.stopAll())
        assertFalse(store.accepts(first))
        assertFalse(store.accepts(second))
        assertTrue(store.stopAll().isEmpty())
        assertFalse(store.finishTurn(second))
    }

    @Test
    fun `old snapshots and detached lists cannot change current state`() {
        val store = SessionTabs()
        val old = store.snapshot()
        val b = store.open().id
        assertEquals(1, old.tabs.size)
        assertNotEquals(b, old.selectedId)
        val detached = store.snapshot().tabs as MutableList<SessionTab>
        detached.clear()
        assertEquals(2, store.snapshot().tabs.size)
    }

    @Test
    fun `empty drafts and unknown ids cannot start a turn`() {
        val store = SessionTabs()
        val id = store.snapshot().selected.id
        assertNull(store.beginTurn(id))
        store.updateComposer(id, AgentMode.AGENT, "", " \n", 0)
        assertNull(store.beginTurn(id))
        assertNull(store.beginTurn("missing"))
        assertNull(store.stop("missing"))
    }

    private fun start(store: SessionTabs, id: String): SessionRunToken {
        store.updateComposer(id, AgentMode.AGENT, "auto", "hello", 0)
        return store.beginTurn(id)!!.token
    }
}
