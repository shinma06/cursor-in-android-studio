package com.cursoragent.acp

import com.cursoragent.service.CommandCatalog
import com.cursoragent.service.commandPrompt
import com.cursoragent.service.containsCommand
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AvailableCommandsTest {
    private fun parse(value: String) = availableCommands(JsonParser.parseString(value).asJsonObject)

    @Test
    fun `replacement is typed and exact while empty invalid and unavailable remain distinct`() {
        val state = parse("""{"availableCommands":[{"name":"Mixed-日本語","description":"server description","input":{"hint":"東京 alpha beta"}}]}""")
        assertTrue(state.containsCommand("Mixed-日本語"))
        assertFalse(state.containsCommand("mixed-日本語"))
        assertEquals("東京 alpha beta", (state as CommandCatalog.Ready).commands.single().hint)
        assertEquals(CommandCatalog.Ready(emptyList()), parse("""{"availableCommands":[]}"""))
        for (wire in listOf("{}", """{"availableCommands":null}""", """{"availableCommands":[{}]}""",
            """{"availableCommands":[{"name":"bad name","description":"x"}]}""",
            """{"availableCommands":[{"name":"/bad","description":"x"}]}""",
            """{"availableCommands":[{"name":"ok","description":7}]}""",
            """{"availableCommands":[{"name":"ok","description":"x","input":{}}]}""",
            """{"availableCommands":[{"name":"ok","description":"x"},{"name":"ok","description":"y"}]}""")) {
            assertEquals(CommandCatalog.Invalid, parse(wire), wire)
        }
        assertFalse(CommandCatalog.Invalid.containsCommand("Mixed-日本語"))
    }

    @Test
    fun `explicit invocation preserves names and every argument character including blank arguments`() {
        assertEquals("/Mixed-日本語  東京  alpha beta\n ", commandPrompt("Mixed-日本語", " 東京  alpha beta\n "))
        assertEquals("/Mixed-日本語", commandPrompt("Mixed-日本語", ""))
        assertEquals(" /manual 東京 ", commandPrompt(null, " /manual 東京 "))
    }
}
