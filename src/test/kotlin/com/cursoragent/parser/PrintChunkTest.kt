package com.cursoragent.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrintChunkTest {
    @Test fun `line limit accepts its boundary and rejects overflow before appending or parsing again`() {
        val valid = """{"type":"assistant","text":"ok"}"""
        val boundary = valid + " ".repeat(StreamJsonParser.MAX_LINE_CHARS - valid.length)
        for (route in listOf("chunk", "line", "split")) {
            val events = mutableListOf<StreamEvent>()
            val parser = StreamJsonParser(events::add)
            when (route) {
                "chunk" -> parser.parseChunk(boundary + "\n")
                "line" -> parser.parseLine(boundary)
                else -> {
                    parser.parseChunk(boundary)
                    parser.finish()
                }
            }
            assertEquals(listOf(StreamEvent.AssistantDelta("ok", PrintAssistantKind.FINAL_FLUSH)), events, route)
            when (route) {
                "chunk" -> parser.parseChunk(boundary + "x\n" + valid + "\n")
                "line" -> parser.parseLine(boundary + "x")
                else -> {
                    parser.parseChunk(boundary)
                    parser.parseChunk("x")
                }
            }
            parser.parseChunk(valid + "\n")
            parser.parseLine(valid)
            parser.finish()
            assertEquals(2, events.size, route)
            assertEquals(StreamEvent.OutputLimitExceeded, events.last(), route)
        }
    }

    @Test fun `every split including surrogate pairs and final line without newline preserves live fixture`() {
        val fixture = javaClass.getResource("/issue-116/tools.jsonl")!!.readText().trimEnd()
        val expected = mutableListOf<StreamEvent>()
        fixture.lineSequence().forEach(StreamJsonParser(expected::add)::parseLine)
        for (size in listOf(1, 2, 17, fixture.length)) {
            val actual = mutableListOf<StreamEvent>()
            val parser = StreamJsonParser(actual::add)
            fixture.chunked(size).forEach(parser::parseChunk)
            parser.finish()
            parser.finish()
            assertEquals(expected, actual, "chunk size $size")
        }
        val events = mutableListOf<StreamEvent>()
        val parser = StreamJsonParser(events::add)
        """{"type":"assistant","text":"はい😀😀","timestamp_ms":1}""".chunked(1).forEach(parser::parseChunk)
        parser.finish()
        assertEquals("はい😀😀", (events.single() as StreamEvent.AssistantDelta).text)
    }

    @Test fun `incomplete EOF and invalid line cannot consume following valid JSON`() {
        val events = mutableListOf<StreamEvent>()
        val parser = StreamJsonParser(events::add)
        parser.parseChunk("broken\r\n{\"type\":\"assistant\",\"text\":\"ok\"}\r\n{\"type\":")
        parser.finish()
        assertEquals(listOf(StreamEvent.AssistantDelta("ok", PrintAssistantKind.FINAL_FLUSH)), events)
    }
}
