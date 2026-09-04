package com.cursoragent.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StreamJsonParserTest {
    @Test
    fun `maps completed edit tool_call to ToolCallCompleted`() {
        val line = javaClass.getResource("/stream-json-fixtures/02_edit_completed.jsonl")!!.readText().trim()
        val events = mutableListOf<StreamEvent>()
        StreamJsonParser { events += it }.parseLine(line)

        assertEquals(1, events.size)
        val completed = events.single() as StreamEvent.ToolCallCompleted
        assertEquals("edit", completed.payload.kind)
        assertEquals("world\n", completed.payload.fileEdit?.afterContent)
    }
}
