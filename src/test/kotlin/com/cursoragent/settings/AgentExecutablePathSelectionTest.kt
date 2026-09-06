package com.cursoragent.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentExecutablePathSelectionTest {
    @Test
    fun `displaying auto detection does not persist a manual override`() {
        val selection = AgentExecutablePathSelection { "/detected/agent" }
        selection.reset("")

        assertEquals("/detected/agent", selection.displayedPath)
        selection.edit(selection.displayedPath)
        assertEquals("", selection.configuredPath)
        selection.reset(selection.configuredPath)
        assertEquals("/detected/agent", selection.displayedPath)
        assertEquals("", selection.configuredPath)
    }

    @Test
    fun `automatic action replaces a manual fixture without pinning the detected path`() {
        var detected = "/first/agent"
        val selection = AgentExecutablePathSelection { detected }
        selection.reset("/fixture/exit-137.sh")
        assertEquals("/fixture/exit-137.sh", selection.displayedPath)

        selection.useAutomatic()
        assertEquals("/first/agent", selection.displayedPath)
        assertEquals("", selection.configuredPath)
        detected = "/updated/agent"
        selection.reset(selection.configuredPath)
        assertEquals("/updated/agent", selection.displayedPath)
    }

    @Test
    fun `manual edits persist exactly and reset discards unsaved changes`() {
        val selection = AgentExecutablePathSelection { "/detected/agent" }
        selection.reset("")
        selection.edit("/fixture with spaces/exit-137.sh")
        assertEquals("/fixture with spaces/exit-137.sh", selection.configuredPath)
        selection.reset("")
        assertEquals("/detected/agent", selection.displayedPath)
        assertEquals("", selection.configuredPath)

        selection.reset("/saved/agent")
        selection.useAutomatic()
        selection.reset("/saved/agent")
        assertEquals("/saved/agent", selection.displayedPath)
        assertEquals("/saved/agent", selection.configuredPath)
    }

    @Test
    fun `clearing the field then applying returns to automatic detection`() {
        val selection = AgentExecutablePathSelection { "/detected/agent" }
        selection.reset("/fixture/exit-137.sh")
        selection.edit("")
        assertEquals("", selection.configuredPath)
        selection.reset(selection.configuredPath)
        assertEquals("/detected/agent", selection.displayedPath)
        assertEquals("", selection.configuredPath)
    }

    @Test
    fun `missing fixed candidates expose unverified PATH fallback without inventing a path`() {
        val selection = AgentExecutablePathSelection { null }
        selection.reset("")
        assertEquals("agent", selection.displayedPath)
        assertEquals("", selection.configuredPath)
        assertTrue(selection.description.contains("未確認"))

        selection.edit("/missing/manual/agent")
        assertEquals("/missing/manual/agent", selection.configuredPath)
        assertEquals("/missing/manual/agent", selection.displayedPath)
    }
}
