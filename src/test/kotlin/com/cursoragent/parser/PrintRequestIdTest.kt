package com.cursoragent.parser

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrintRequestIdTest {
    @Test fun `optional invalid IDs never discard successful text or usage`() {
        val invalid = listOf("null", "23", "true", "{}", "[]", "\"\"", "\" \"", "\" leading\"", "\"trailing \"", "\"a\\u0000b\"", "\"a\\u007fb\"", "\"a\\u0085b\"", "\"\\ud800\"")
        for (encoded in invalid + listOf(null)) {
            val json = JsonParser.parseString("""{"type":"result","subtype":"success","result":"answer","is_error":false,"usage":{"inputTokens":7}}""").asJsonObject
            if (encoded != null) json.add("request_id", JsonParser.parseString(encoded))
            val event = parse(json)
            assertNull(event.requestId, encoded)
            assertEquals("answer", event.result)
            assertFalse(event.isError)
            assertEquals(7L, event.usage?.inputTokens)
        }
    }

    @Test fun `opaque IDs preserve case Unicode spaces inside and exact UTF8 boundary`() {
        for (id in listOf("Opaque AbC:/-+_", "診断ID🌿", "a".repeat(1024), "é".repeat(512), "🌿".repeat(256))) {
            val json = JsonObject().apply { addProperty("type", "result"); addProperty("request_id", id) }
            assertEquals(id, parse(json).requestId)
        }
        for (id in listOf("a".repeat(1025), "é".repeat(513), "🌿".repeat(257), "\u00a0id", "id\u3000")) {
            val json = JsonObject().apply { addProperty("type", "result"); addProperty("request_id", id) }
            assertNull(parse(json).requestId)
        }
    }

    @Test fun `other identifiers and body text never substitute for terminal request ID`() {
        val json = JsonParser.parseString("""{"type":"result","session_id":"session","id":"rpc","model_call_id":"call","result":"body-uuid"}""").asJsonObject
        assertNull(parse(json).requestId)
        val events = mutableListOf<StreamEvent>()
        StreamJsonParser { events += it }.parseLine("""{"type":"assistant","text":"body","request_id":"not-terminal"}""")
        assertTrue(events.single() is StreamEvent.AssistantDelta)
    }

    private fun parse(json: JsonObject): StreamEvent.Result {
        val events = mutableListOf<StreamEvent>()
        StreamJsonParser { events += it }.parseLine(json.toString())
        return events.single() as StreamEvent.Result
    }
}
