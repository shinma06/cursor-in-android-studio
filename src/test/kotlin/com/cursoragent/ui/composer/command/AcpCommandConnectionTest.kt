package com.cursoragent.ui.composer.command

import com.cursoragent.service.AgentCommand
import com.cursoragent.service.CommandCatalog
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AcpCommandConnectionTest {
    private val key = AcpCommandKey("/project", "agent", PermissionMode.ASK_EVERY_TIME, SandboxMode.DEFAULT, WorktreeMode.DEFAULT)
    private val ready = CommandCatalog.Ready(listOf(AgentCommand("one", "server")))

    @Test
    fun `unchanged settings reuse connection while changed settings and explicit retry invalidate old callbacks`() {
        val state = AcpCommandConnection()
        var ticket = state.replace(key)!!
        assertTrue(state.update(ticket, ready))
        assertNull(state.replace(key))
        for (next in listOf(key.copy(root = "/other"), key.copy(executable = "other-agent"),
            key.copy(permission = PermissionMode.entries.first { it != key.permission }),
            key.copy(sandbox = SandboxMode.entries.first { it != key.sandbox }),
            key.copy(workspace = WorktreeMode.ISOLATED))) {
            val current = state.replace(next)!!
            assertFalse(state.update(ticket, ready))
            assertEquals(CommandCatalog.Loading, state.catalog)
            ticket = current
        }
        val retry = state.replace(key, true)!!
        assertFalse(state.update(ticket, ready))
        assertTrue(state.update(retry, CommandCatalog.Failed))
        assertEquals(CommandCatalog.Failed, state.catalog)
    }

    @Test
    fun `transport switch or dispose clears catalog and late completion cannot leak across tabs`() {
        val a = AcpCommandConnection()
        val b = AcpCommandConnection()
        val ticket = a.replace(key)!!
        b.replace(key)
        a.clear()
        assertFalse(a.update(ticket, ready))
        assertEquals(CommandCatalog.Unavailable, a.catalog)
        assertEquals(CommandCatalog.Loading, b.catalog)
    }
}
