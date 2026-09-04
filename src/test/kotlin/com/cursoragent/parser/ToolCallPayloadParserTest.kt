package com.cursoragent.parser

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `an unexpected non-object ToolCall payload returns null instead of throwing`() {
        val json = JsonParser.parseString(
            """{"type":"tool_call","subtype":"completed","call_id":"1","tool_call":{"editToolCall":"not-an-object"}}""",
        ).asJsonObject

        assertNull(ToolCallPayloadParser.parse(json))
    }

    @Test
    fun `an unexpected non-object result value returns null instead of throwing`() {
        val json = JsonParser.parseString(
            """{"type":"tool_call","subtype":"completed","call_id":"1","tool_call":{"editToolCall":{"args":{"path":"a.txt"},"result":"not-an-object"}}}""",
        ).asJsonObject

        assertNull(ToolCallPayloadParser.parse(json))
    }
}
