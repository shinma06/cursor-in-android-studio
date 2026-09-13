package com.cursoragent.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrintAssistantTextTest {
    @Test fun `verified partial repeats short text newline Japanese and emoji without heuristic removal`() {
        val text = PrintAssistantText(PrintAssistantText.VERIFIED_VERSION, true)
        val parts = listOf("はい", "はい", "-", "-", "\n", "😀", "😀", "a", "a")
        val expected = StringBuilder()
        parts.forEach { part ->
            expected.append(part)
            assertEquals(expected.toString(), text.accept(StreamEvent.AssistantDelta(part, PrintAssistantKind.DELTA)))
        }
        assertNull(text.accept(StreamEvent.AssistantDelta(expected.toString(), PrintAssistantKind.TOOL_FLUSH)))
        assertNull(text.accept(StreamEvent.AssistantDelta(expected.toString(), PrintAssistantKind.FINAL_FLUSH)))
    }

    @Test fun `nonpartial complete messages concatenate even when same while unknown versions stay legacy`() {
        val complete = PrintAssistantText(PrintAssistantText.VERIFIED_VERSION, false)
        assertEquals("same", complete.accept(StreamEvent.AssistantDelta("same", PrintAssistantKind.TOOL_FLUSH)))
        assertEquals("samesame", complete.accept(StreamEvent.AssistantDelta("same", PrintAssistantKind.FINAL_FLUSH)))
        for (version in listOf(null, "2026.09.02-c22c1a3", "2026.09.11-unverified")) {
            val legacy = PrintAssistantText(version, true)
            assertEquals("Hello", legacy.accept(StreamEvent.AssistantDelta("Hello", PrintAssistantKind.FINAL_FLUSH)))
            assertEquals("Hello world", legacy.accept(StreamEvent.AssistantDelta("Hello world", PrintAssistantKind.DELTA)))
            assertNull(legacy.accept(StreamEvent.AssistantDelta("Hello world", PrintAssistantKind.DELTA)))
        }
    }

    @Test fun `invalid metadata retreats once and seeds legacy with the already displayed full text`() {
        val normalizer = PrintAssistantText(PrintAssistantText.VERIFIED_VERSION, true)
        val values = mutableListOf<String>()
        val kinds = mutableListOf<PrintAssistantKind>()
        val parser = StreamJsonParser { event -> if (event is StreamEvent.AssistantDelta) {
            kinds.add(event.kind)
            normalizer.accept(event)?.let(values::add)
        } }
        listOf(
            """{"type":"assistant","text":"Hello","timestamp_ms":1}""",
            """{"type":"assistant","text":" world","timestamp_ms":null}""",
            """{"type":"assistant","text":"Hello world!","timestamp_ms":2,"model_call_id":"flush"}""",
            """{"type":"assistant","text":"Hello world!","timestamp_ms":3}""",
        ).forEach(parser::parseLine)
        assertEquals(listOf("Hello", "Hello world", "Hello world!"), values)
        assertEquals(listOf(PrintAssistantKind.DELTA, PrintAssistantKind.UNRECOGNIZED, PrintAssistantKind.TOOL_FLUSH, PrintAssistantKind.DELTA), kinds)
    }

    @Test fun `malformed metadata never disguises itself as a verified delta`() {
        val kinds = mutableListOf<PrintAssistantKind>()
        val parser = StreamJsonParser { if (it is StreamEvent.AssistantDelta) kinds.add(it.kind) }
        listOf("null", "true", "\"1\"", "-1", "1.5", "{}", "[]").forEach {
            parser.parseLine("""{"type":"assistant","text":"valid body","timestamp_ms":$it}""")
        }
        assertEquals(List(7) { PrintAssistantKind.UNRECOGNIZED }, kinds)
    }
}
