package com.cursoragent.ui

import com.cursoragent.acp.AcpProtocol
import com.cursoragent.parser.StreamEvent
import com.cursoragent.parser.StreamJsonParser
import com.cursoragent.service.AgentEvent
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TurnAssistantTextTest {
    @Test
    fun `print parser cumulative resend and tool interruption replace text without duplicate fallback`() {
        val updates = mutableListOf<String>()
        val text = TurnAssistantText({ updates.add("replace:$it") }, { updates.add("start") })
        val parser = StreamJsonParser { event ->
            when (event) {
                is StreamEvent.AssistantDelta -> text.printDelta(event.text)
                is StreamEvent.Result -> if (!event.isError) event.result?.let(text::printFallback)
                else -> Unit
            }
        }
        listOf(
            """{"type":"assistant","text":"Hello"}""",
            """{"type":"tool_call","name":"read"}""",
            """{"type":"assistant","text":"Hello, world"}""",
            """{"type":"assistant","text":"Hello, world"}""",
            """{"type":"result","result":"ignored fallback"}""",
        ).forEach(parser::parseLine)
        assertEquals(listOf("replace:Hello", "replace:Hello, world"), updates)
    }

    @Test
    fun `print fallback is shown only once without assistant text and does not seed delta buffer`() {
        val updates = mutableListOf<String>()
        val text = TurnAssistantText(updates::add) { error("print has no message boundary") }
        text.printDelta("")
        text.printFallback("result only")
        text.printFallback("duplicate")
        // Result is not physical exit; preserve the existing late-delta replacement behavior.
        text.printDelta("late delta")
        text.printFallback("duplicate after delta")
        assertEquals(listOf("result only", "late delta"), updates)
    }

    @Test
    fun `ACP protocol repeats and tool thought message boundaries reach explicit display operations`() {
        val updates = mutableListOf<String>()
        val text = TurnAssistantText({ updates.add("replace:$it") }, { updates.add("start") })
        val protocol = AcpProtocol()
        fun receive(json: String) {
            val event = protocol.update(JsonParser.parseString(json).asJsonObject)
            if (event is AgentEvent.Text) text.acpDelta(event)
        }
        val repeat = """{"sessionUpdate":"agent_message_chunk","messageId":"a","content":{"type":"text","text":"はい"}}"""
        receive(repeat)
        receive(repeat)
        receive("""{"sessionUpdate":"tool_call","toolCallId":"t","title":"read"}""")
        receive(repeat)
        receive("""{"sessionUpdate":"agent_thought_chunk","content":{"type":"text","text":"考える"}}""")
        receive(repeat)
        receive("""{"sessionUpdate":"agent_message_chunk","messageId":"b","content":{"type":"text","text":"次"}}""")
        assertEquals(listOf("start", "replace:はい", "replace:はいはい", "start", "replace:はい",
            "start", "replace:はい", "start", "replace:次"), updates)
    }

    @Test
    fun `captured print fixture flows through existing parser and real display state`() {
        val updates = mutableListOf<String>()
        val text = TurnAssistantText(updates::add) { error("print has no message boundary") }
        val parser = StreamJsonParser { event ->
            when (event) {
                is StreamEvent.AssistantDelta -> text.printDelta(event.text)
                is StreamEvent.Result -> if (!event.isError) event.result?.let(text::printFallback)
                else -> Unit
            }
        }
        javaClass.getResource("/stream-json-fixtures/04_plain_question_success.jsonl")!!.readText()
            .lineSequence().forEach(parser::parseLine)
        assertEquals(listOf("OK"), updates)
    }
}
