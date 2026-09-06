package com.cursoragent.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TokenUsageTest {
    private fun result(usage: String): StreamEvent.Result {
        val events = mutableListOf<StreamEvent>()
        StreamJsonParser { events += it }.parseLine("""{"type":"result","result":"OK","usage":$usage}""")
        return events.single() as StreamEvent.Result
    }

    @Test
    fun `reads counters from captured CLI result without summing cache into input`() {
        val events = mutableListOf<StreamEvent>()
        val parser = StreamJsonParser { events += it }
        javaClass.getResource("/stream-json-fixtures/04_plain_question_success.jsonl")!!.readText().lineSequence().forEach(parser::parseLine)
        assertEquals(TokenUsage(21022, 31, 8896, 0), events.filterIsInstance<StreamEvent.Result>().single().usage)
    }

    @Test
    fun `missing or malformed usage cannot discard a successful result`() {
        listOf("null", "[]", "{}", "false", "42", "\"oops\"").forEach {
            assertNull(result(it).usage)
            assertEquals("OK", result(it).result)
        }
    }

    @Test
    fun `rejects invalid counters individually without losing valid zero or large counts`() {
        listOf("-1", "1.5", "9223372036854775808", "\"12\"", "true", "[]", "{}", "null").forEach {
            val usage = result("""{"inputTokens":$it,"outputTokens":0,"cacheReadTokens":3000000000}""").usage!!
            assertNull(usage.inputTokens)
            assertEquals(0L, usage.outputTokens)
            assertEquals(3000000000L, usage.cacheReadTokens)
            assertNull(usage.cacheWriteTokens)
        }
    }
}
