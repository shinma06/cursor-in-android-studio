package com.cursoragent.parser

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ToolCallPayloadParserTest {
    @Test
    fun `parses completed edit tool call with diff metadata`() {
        val line = javaClass.getResource("/stream-json-fixtures/02_edit_completed.jsonl")!!.readText().trim()
        val json = JsonParser.parseString(line).asJsonObject

        val parsed = ToolCallPayloadParser.parse(json)

        assertNotNull(parsed)
        assertEquals("completed", parsed!!.subtype)
        assertEquals("edit", parsed.kind)
        assertNotNull(parsed.fileEdit)
        assertEquals(1, parsed.fileEdit!!.linesAdded)
        assertEquals(1, parsed.fileEdit!!.linesRemoved)
        assertEquals("hello\n", parsed.fileEdit!!.beforeContent)
        assertEquals("world\n", parsed.fileEdit!!.afterContent)
    }

    @Test
    fun `parses completed shell tool call with stdout`() {
        val line = javaClass.getResource("/stream-json-fixtures/03_shell_completed.jsonl")!!.readText().trim()
        val json = JsonParser.parseString(line).asJsonObject

        val parsed = ToolCallPayloadParser.parse(json)

        assertNotNull(parsed)
        assertEquals("shell", parsed!!.kind)
        assertNotNull(parsed.shellResult)
        assertEquals(0, parsed.shellResult!!.exitCode)
        assertEquals("SHELL_OK\n", parsed.shellResult!!.stdout)
    }
}
