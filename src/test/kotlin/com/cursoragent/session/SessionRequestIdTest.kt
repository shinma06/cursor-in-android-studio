package com.cursoragent.session

import com.cursoragent.service.AgentTransport
import com.cursoragent.service.PrintRequestId
import com.cursoragent.settings.AgentMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SessionRequestIdTest {
    @Test fun `tab ownership survives reorder selection and resume but never a new turn or close`() {
        val tabs = SessionTabs()
        val owner = tabs.snapshot().selectedId
        val first = begin(tabs, owner)
        assertTrue(tabs.bindChat(first, "session"))
        val other = tabs.open().id
        val id = PrintRequestId("opaque", "session")
        assertTrue(tabs.confirmRequestId(first, id))
        tabs.finishTurn(first)
        tabs.move(owner, 1)
        assertNull(tabs.snapshot().selected.requestId)
        tabs.select(owner)
        assertSame(id, tabs.snapshot().selected.requestId)
        assertFalse(tabs.confirmRequestId(first, PrintRequestId("late", "session")))
        val next = begin(tabs, owner)
        assertNull(tabs.snapshot().selected.requestId)
        assertFalse(tabs.confirmRequestId(first, id))
        tabs.finishTurn(next)
        assertNull(tabs.snapshot().selected.requestId, "Missing or failed next turn cannot reuse the previous ID")
        tabs.close(owner)
        tabs.open("session")
        assertNull(tabs.snapshot().selected.requestId, "Restored session IDs do not restore diagnostics")
        assertTrue(tabs.snapshot().tabs.any { it.id == other })
    }

    @Test fun `ACP mismatched sessions stopped turns and disposal cannot store IDs`() {
        val tabs = SessionTabs()
        val owner = tabs.snapshot().selectedId
        val token = begin(tabs, owner)
        assertTrue(tabs.bindChat(token, "session"))
        assertFalse(tabs.confirmRequestId(token, PrintRequestId("id", "wrong")))
        tabs.stop(owner)
        assertFalse(tabs.confirmRequestId(token, PrintRequestId("id", "session")))
        val next = begin(tabs, owner)
        assertTrue(tabs.confirmRequestId(next, PrintRequestId("id", "session")))
        tabs.finishTurn(next)
        tabs.stopAll()
        assertNull(tabs.snapshot().selected.requestId)
        val acp = tabs.open(transport = AgentTransport.ACP)
        val acpRun = begin(tabs, acp.id)
        assertFalse(tabs.confirmRequestId(acpRun, PrintRequestId("rpc-id", null)))
    }

    @Test fun `same ID new completion has distinct identity and selection revision detects switch back`() {
        val tabs = SessionTabs()
        val owner = tabs.snapshot().selectedId
        val first = begin(tabs, owner)
        val id = PrintRequestId("same", null)
        tabs.confirmRequestId(first, id)
        tabs.finishTurn(first)
        val initial = tabs.snapshot()
        tabs.select(owner)
        assertEquals(initial.selectionRevision, tabs.snapshot().selectionRevision)
        tabs.open()
        tabs.select(owner)
        assertNotEquals(initial.selectionRevision, tabs.snapshot().selectionRevision)
        val next = begin(tabs, owner)
        tabs.confirmRequestId(next, PrintRequestId("same", null))
        tabs.finishTurn(next)
        assertNotSame(id, tabs.snapshot().selected.requestId)
        assertFalse(tabs.snapshot().toString().contains("value=same"))
    }

    private fun begin(tabs: SessionTabs, owner: String): SessionRunToken {
        tabs.updateComposer(owner, AgentMode.AGENT, "", "prompt", 0)
        return tabs.beginTurn(owner)!!.token
    }
}
