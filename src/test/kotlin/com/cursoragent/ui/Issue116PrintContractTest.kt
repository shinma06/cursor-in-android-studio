package com.cursoragent.ui

import com.cursoragent.parser.StreamEvent
import com.cursoragent.parser.StreamJsonParser
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Research characterization, not desired product behavior or GUI acceptance. See #116. */
class Issue116PrintContractTest {
    @Test
    fun `live partial tool boundaries reproduce missing and duplicated text in current product`() {
        val lines = fixture("tools")
        val expected = "START-A\nBETWEEN-BDONE-C 46"
        assertEquals(expected, documentedPartialText(lines))
        assertEquals(expected, resultText(lines))
        val displayed = replay(lines).last()
        assertEquals("START-A\nBETWEENBETWEEN-BDONEC 46DONE-C 46", displayed)
        assertNotEquals(expected, displayed)
    }

    @Test
    fun `live interrupted repetition contains legitimate equal deltas without a terminal result`() {
        val lines = fixture("plain")
        assertEquals(
            "red red red blue blue blue\nred red red blue blue blue\n" +
                "red red red blue blue bluered red red blue blue blue\n" +
                "red red red blue blue blue\nred red red blue blue blue",
            documentedPartialText(lines),
        )
        assertEquals("\nred red blue", replay(lines).last())
        assertTrue(lines.none { JsonParser.parseString(it).asJsonObject["type"].asString == "result" })
    }

    @Test
    fun `live nonpartial messages must not be filtered with partial metadata rules`() {
        val lines = fixture("nonpartial")
        assertEquals("", documentedPartialText(lines))
        assertEquals("START-A\nBETWEEN-BDONE-C 46", resultText(lines))
        assertEquals(resultText(lines), replay(lines).last())
    }

    @Test
    fun `synthetic metadata variants all collapse to the same text-only event today`() {
        val events = mutableListOf<StreamEvent>()
        val parser = StreamJsonParser(events::add)
        listOf(
            "", ",\"timestamp_ms\":1", ",\"timestamp_ms\":1,\"model_call_id\":\"call\"",
            ",\"timestamp_ms\":null,\"model_call_id\":null",
            ",\"timestamp_ms\":\"\",\"model_call_id\":\"\"",
            ",\"timestamp_ms\":{},\"model_call_id\":[]",
            ",\"model_call_id\":\"call\"",
        ).forEach { metadata -> parser.parseLine("{\"type\":\"assistant\",\"text\":\"ha\"$metadata}") }
        assertEquals(List(7) { StreamEvent.AssistantDelta("ha") }, events)
    }

    @Test
    fun `synthetic cumulative unknown thought empty malformed result-only and error remain distinct`() {
        val ignored = listOf(
            "{\"type\":\"future\",\"text\":\"not body\"}",
            "{\"type\":\"thinking\",\"subtype\":\"delta\",\"text\":\"not body\"}",
            "{\"type\":\"assistant\",\"text\":\"\"}",
            "{\"type\":\"assistant\",\"text\":{}}",
            "not-json",
        )
        assertEquals(
            listOf("Hello", "Hello world"),
            replay(
                ignored + listOf(
                    "{\"type\":\"assistant\",\"text\":\"Hello\"}",
                    "{\"type\":\"assistant\",\"text\":\"Hello world\"}",
                    "{\"type\":\"assistant\",\"text\":\"Hello world\"}",
                    "{\"type\":\"result\",\"result\":\"ignored fallback\"}",
                ),
            ),
        )
        assertEquals(listOf("result only"), replay(ignored + "{\"type\":\"result\",\"result\":\"result only\"}"))
        assertEquals(emptyList<String>(), replay(ignored + "{\"type\":\"result\",\"is_error\":true,\"result\":\"error\"}"))
        val legacy = javaClass.getResource("/stream-json-fixtures/04_plain_question_success.jsonl")!!.readText().lines()
        assertEquals(listOf("OK"), replay(legacy))
    }

    private fun fixture(name: String): List<String> =
        javaClass.getResource("/issue-116/$name.jsonl")!!.readText().lineSequence().filter(String::isNotBlank).toList()

    // Test-side dispatch matches AgentProcessService's text/fallback branches; no IDE process is launched.
    private fun replay(lines: List<String>): List<String> {
        val updates = mutableListOf<String>()
        val text = TurnAssistantText(updates::add) { error("print has no explicit message boundary") }
        val parser = StreamJsonParser { event ->
            when (event) {
                is StreamEvent.AssistantDelta -> if (event.text.isNotEmpty()) text.printDelta(event.text)
                is StreamEvent.Result -> if (!event.isError && !event.result.isNullOrBlank()) text.printFallback(event.result)
                else -> Unit
            }
        }
        lines.forEach(parser::parseLine)
        return updates
    }

    // Oracle only for the controlled partial fixtures, not a proposed compatibility detector.
    private fun documentedPartialText(lines: List<String>): String = lines.map { JsonParser.parseString(it).asJsonObject }
        .filter { it["type"].asString == "assistant" && it.has("timestamp_ms") && !it.has("model_call_id") }
        .joinToString("") { event ->
            event.getAsJsonObject("message").getAsJsonArray("content").joinToString("") { it.asJsonObject["text"].asString }
        }

    private fun resultText(lines: List<String>): String = lines.map { JsonParser.parseString(it).asJsonObject }
        .single { it["type"].asString == "result" }["result"].asString
}
