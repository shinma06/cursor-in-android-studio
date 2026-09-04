package com.cursoragent.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class McpListParserTest {
    @Test
    fun `parses id status lines`() {
        val raw = """
            gitlab: ready
            github: disabled
        """.trimIndent()

        assertEquals(
            listOf(McpServerEntry("gitlab", "ready"), McpServerEntry("github", "disabled")),
            McpListParser.parse(raw),
        )
    }

    @Test
    fun `parses the live fixture`() {
        val fixture = File("src/test/resources/cli-output-fixtures/mcp-list.txt").readText()
        val entries = McpListParser.parse(fixture)

        assertEquals(9, entries.size)
        assertTrue(entries.any { it.id == "github" && it.status == "ready" })
    }

    @Test
    fun `format produces aligned table`() {
        val formatted = McpListParser.format(
            listOf(McpServerEntry("gitlab", "ready"), McpServerEntry("github", "ready")),
        )

        assertTrue(formatted.contains("ID"))
        assertTrue(formatted.contains("gitlab"))
        assertTrue(formatted.contains("github"))
    }

    @Test
    fun `returns empty message for unparseable output`() {
        assertEquals("(no MCP servers listed)", McpListParser.format(McpListParser.parse("(agent mcp list failed)")))
    }
}
