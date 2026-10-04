package com.cursoragent.session

import com.cursoragent.settings.AgentMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionTabsTest {
    @Test
    fun `hiding selected owners chooses a visible neighbor without disposing live work`() {
        val tabs = SessionTabs()
        val first = tabs.snapshot().selectedId
        tabs.updateComposer(first, AgentMode.PLAN, "model", "request", 3)
        val token = tabs.beginTurn(first)!!.token
        tabs.updateComposer(first, AgentMode.ASK, "model", "draft", 4)
        val before = tabs.snapshot().selected
        val second = tabs.open().id
        val third = tabs.open().id
        tabs.select(first)
        assertTrue(tabs.hide(first))
        assertEquals(second, tabs.snapshot().selectedId)
        assertEquals(before.copy(visible = false), tabs.snapshot().tabs.first { it.id == first })
        assertTrue(tabs.accepts(token))
        assertFalse(tabs.hide(first))
        assertFalse(tabs.hide("missing"))
        assertTrue(tabs.hide(third))
        assertEquals(second, tabs.snapshot().selectedId)
        assertTrue(tabs.hide(second))
        val fresh = tabs.snapshot().selectedId
        assertEquals(4, tabs.snapshot().tabs.size)
        assertEquals(listOf(fresh), tabs.snapshot().visibleTabs.map { it.id })
        assertTrue(tabs.finishTurn(token))
        assertEquals(fresh, tabs.snapshot().selectedId)
        assertTrue(tabs.select(first))
        assertEquals(before.copy(run = null), tabs.snapshot().selected)
        assertTrue(tabs.hide(first))
        assertEquals(fresh, tabs.snapshot().selectedId)
    }

    @Test
    fun `replacing a view retains its owner token draft and identity until explicit close`() {
        val store = SessionTabs()
        val first = store.snapshot().selectedId
        store.selectTransport(first, com.cursoragent.service.AgentTransport.ACP)
        store.updateComposer(first, AgentMode.PLAN, "provider-model", "request", 2)
        val token = store.beginTurn(first)!!.token
        store.bindChat(token, "provider-session")
        store.updateComposer(first, AgentMode.ASK, "next-model", "next draft", 4)
        val before = store.snapshot().selected
        val next = store.replaceSelected()
        assertEquals(listOf(next.id), store.snapshot().visibleTabs.map { it.id })
        assertEquals(before.copy(visible = false), store.snapshot().tabs.first { it.id == first })
        assertTrue(store.accepts(token))
        assertTrue(store.applyAcpTitle(first, "provider-session", "background title"))
        assertTrue(store.finishTurn(token))
        assertEquals(next.id, store.snapshot().selectedId)
        val reopened = store.open(conversationId = before.conversationId, transport = before.transport)
        assertEquals(first, reopened.id)
        assertTrue(reopened.visible)
        assertEquals("next draft", reopened.draft)
        assertEquals(4, reopened.caret)
        assertEquals("next-model", reopened.modelId)
        assertEquals("provider-session", reopened.chatId)
        assertEquals(2, store.snapshot().tabs.size)
        store.close(first)
        assertFalse(store.applyAcpTitle(first, "provider-session", "late title"))
    }

    @Test
    fun `hidden owners do not affect visible order close neighbors or last-tab replacement`() {
        val store = SessionTabs()
        val hidden = store.snapshot().selectedId
        store.updateComposer(hidden, AgentMode.AGENT, "model", "running", 0)
        val token = store.beginTurn(hidden)!!.token
        val a = store.replaceSelected().id
        val b = store.open().id
        val c = store.open().id
        assertFalse(store.move(hidden, 0))
        assertTrue(store.move(c, 0))
        assertTrue(store.move(a, 2))
        assertEquals(listOf(c, b, a), store.snapshot().visibleTabs.map { it.id })
        store.select(b)
        store.close(b)
        assertEquals(a, store.snapshot().selectedId)
        store.closeAll(listOf(c, a))
        val fresh = store.snapshot().selected
        assertEquals(listOf(fresh.id), store.snapshot().visibleTabs.map { it.id })
        assertEquals(setOf(hidden, fresh.id), store.snapshot().tabs.map { it.id }.toSet())
        assertTrue(store.accepts(token))
        assertEquals(listOf(token), store.stopAll(), "project cleanup includes owners without visible tabs")
        assertFalse(store.accepts(token))
        assertTrue(store.select(hidden))
        assertEquals(listOf(fresh.id, hidden), store.snapshot().visibleTabs.map { it.id })
    }

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
    fun `closing the final ACP conversation leaves a fresh unlocked tab and rejects late connection metadata`() {
        val store = SessionTabs()
        val old = store.snapshot().selected
        store.selectTransport(old.id, com.cursoragent.service.AgentTransport.ACP)
        store.updateComposer(old.id, AgentMode.ASK, "provider-model", "request", 7)
        val turn = store.beginTurn(old.id)!!
        store.bindChat(turn.token, "provider-session")
        store.applyAcpTitle(old.id, "provider-session", "Provider title")
        store.updateComposer(old.id, AgentMode.ASK, "provider-model", "next draft", 4)

        assertSame(turn.token, store.closeAll(listOf(old.id)).single().run)
        val fresh = store.snapshot().selected
        assertNotEquals(old.id, fresh.id)
        assertNotEquals(old.conversationId, fresh.conversationId)
        assertEquals(SessionTab.NEW_AGENT_TITLE, fresh.title)
        assertNull(fresh.chatId)
        assertNull(fresh.run)
        assertEquals("", fresh.draft)
        assertEquals("", fresh.modelId)
        assertFalse(fresh.transportLocked)
        assertEquals(com.cursoragent.service.AgentTransport.PRINT, fresh.transport)
        assertFalse(store.accepts(turn.token))
        assertFalse(store.finishTurn(turn.token))
        assertFalse(store.applyAcpTitle(old.id, "provider-session", "Late title"))
        assertEquals(fresh, store.snapshot().selected)
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
    fun `ACP metadata updates only its bound tab after completion and preserves drafts selection and manual names`() {
        val store = SessionTabs()
        val a = store.snapshot().selectedId
        store.selectTransport(a, com.cursoragent.service.AgentTransport.ACP)
        assertFalse(store.applyAcpTitle(a, "provider-a", "before binding"))
        val turn = start(store, a)
        store.bindChat(turn, "provider-a")
        store.finishTurn(turn)
        store.updateComposer(a, AgentMode.ASK, "model-a", "編集中の下書き", 3)
        val b = store.open("provider-b", transport = com.cursoragent.service.AgentTransport.ACP)
        val selection = store.snapshot().selectionRevision
        assertFalse(store.applyAcpTitle(a, "provider-b", "foreign"))
        assertTrue(store.applyAcpTitle(a, "provider-a", "確認済みの名前"))
        val updated = store.snapshot().tabs.first { it.id == a }
        assertEquals("確認済みの名前", updated.title)
        assertEquals("編集中の下書き", updated.draft)
        assertEquals(3, updated.caret)
        assertEquals(AgentMode.ASK, updated.mode)
        assertEquals("model-a", updated.modelId)
        assertEquals(b.id, store.snapshot().selectedId)
        assertEquals(selection, store.snapshot().selectionRevision)
        assertEquals(SessionTab.NEW_AGENT_TITLE, store.snapshot().selected.title)
        assertFalse(store.applyAcpTitle(a, "provider-a", "  "))
        assertTrue(store.applyAcpTitle(a, "provider-a", null))
        assertEquals(SessionTab.NEW_AGENT_TITLE, store.snapshot().tabs.first { it.id == a }.title)
        store.rename(a, "自分の名前")
        assertFalse(store.applyAcpTitle(a, "provider-a", "new provider title"))
        assertFalse(store.applyAcpTitle(a, "provider-a", null))
        assertEquals("自分の名前", store.snapshot().tabs.first { it.id == a }.title)
        val print = store.open("provider-a")
        assertFalse(store.applyAcpTitle(print.id, "provider-a", "ACP name"))
        store.close(a)
        assertFalse(store.applyAcpTitle(a, "provider-a", "after close"))
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
