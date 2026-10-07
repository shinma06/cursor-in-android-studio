package com.cursoragent.ui

import com.cursoragent.parser.StreamEvent
import com.cursoragent.parser.StreamJsonParser
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** #116 live projections replayed through the #254 product normalization; not GUI acceptance. */
class Issue116PrintContractTest {
    @Test
    fun `live partial tool boundaries preserve all delta text without duplicate flushes`() {
        val lines = fixture("tools")
        val expected = "START-A\nBETWEEN-BDONE-C 46"
        assertEquals(expected, documentedPartialText(lines))
        assertEquals(expected, resultText(lines))
        val displayed = replay(lines).last()
        assertEquals(expected, displayed)
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
        assertEquals(documentedPartialText(lines), replay(lines).last())
        assertTrue(lines.none { JsonParser.parseString(it).asJsonObject["type"].asString == "result" })
    }

    @Test
    fun `live nonpartial messages must not be filtered with partial metadata rules`() {
        val lines = fixture("nonpartial")
        assertEquals("", documentedPartialText(lines))
        assertEquals("START-A\nBETWEEN-BDONE-C 46", resultText(lines))
        assertEquals(resultText(lines), replay(lines, partial = false).last())
    }

    @Test
    fun `synthetic metadata is validated without losing assistant text`() {
        val events = mutableListOf<StreamEvent>()
        val parser = StreamJsonParser(events::add)
        listOf(
            "", ",\"timestamp_ms\":1", ",\"timestamp_ms\":1,\"model_call_id\":\"call\"",
            ",\"timestamp_ms\":null,\"model_call_id\":null",
            ",\"timestamp_ms\":\"\",\"model_call_id\":\"\"",
            ",\"timestamp_ms\":{},\"model_call_id\":[]",
            ",\"model_call_id\":\"call\"",
        ).forEach { metadata -> parser.parseLine("{\"type\":\"assistant\",\"text\":\"ha\"$metadata}") }
        assertEquals(List(7) { "ha" }, events.map { (it as StreamEvent.AssistantDelta).text })
        assertEquals(listOf("FINAL_FLUSH", "DELTA", "TOOL_FLUSH", "UNRECOGNIZED", "UNRECOGNIZED", "UNRECOGNIZED", "UNRECOGNIZED"), events.map { (it as StreamEvent.AssistantDelta).kind.name })
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
                ), version = null,
            ),
        )
        assertEquals(listOf("result only"), replay(ignored + "{\"type\":\"result\",\"result\":\"result only\"}"))
        assertEquals(emptyList<String>(), replay(ignored + "{\"type\":\"result\",\"is_error\":true,\"result\":\"error\"}"))
        val legacy = javaClass.getResource("/stream-json-fixtures/04_plain_question_success.jsonl")!!.readText().lines()
        assertEquals(listOf("OK"), replay(legacy, version = "2026.09.02-c22c1a3"))
    }

    private fun fixture(name: String): List<String> =
        javaClass.getResource("/issue-116/$name.jsonl")!!.readText().lineSequence().filter(String::isNotBlank).toList()

    // Test-side dispatch matches AgentProcessService's text/fallback branches; no IDE process is launched.
    private fun replay(lines: List<String>, partial: Boolean = true, version: String? = com.cursoragent.parser.PrintAssistantText.VERIFIED_VERSION): List<String> {
        val updates = mutableListOf<String>()
        val text = TurnAssistantText(updates::add) { error("print has no explicit message boundary") }
        val printText = com.cursoragent.parser.PrintAssistantText(version, partial)
        val parser = StreamJsonParser { event ->
            when (event) {
                is StreamEvent.AssistantDelta -> printText.accept(event)?.let(text::printText)
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
