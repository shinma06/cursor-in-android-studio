package com.cursoragent.parser

import com.cursoragent.service.AgentTask
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaskPayloadTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private fun projected(): List<JsonObject> = JsonParser.parseString(javaClass.getResource("/task/issue-118-projection.json")!!.readText())
        .asJsonObject.getAsJsonArray("print").map { it.asJsonObject.deepCopy().apply {
            val payload = remove("taskToolCall")
            add("tool_call", JsonObject().apply { add("taskToolCall", payload) })
        } }

    @Test fun `print public projection separates request IDs results and child failure from parent success`() {
        // Public projection flattens tool_call: restore only that known wire envelope for the parser.
        val states = projected().map { ToolCallPayloadParser.parse(it)!! }
        assertEquals(listOf("print-parent-1"), states.map { it.parentSessionId }.distinct())
        assertEquals("print-argument-agent-1", states[1].task!!.task!!.requestedAgentId)
        assertEquals("print-child-1", states[1].task!!.task!!.agentId)
        assertEquals(8376L, states[1].task!!.task!!.durationMs)
        assertTrue(states[1].task!!.task!!.resultText!!.contains("I118_CHILD|I118_FIXTURE_ALPHA"))
        assertEquals("print-child-1", states[3].task!!.task!!.resumeId)
        assertNull(states[3].task!!.task!!.agentId)
        assertEquals("failed", states[3].task!!.status)
        assertTrue(states[3].task!!.task!!.errorText!!.startsWith("Request blocked"))
        val events = mutableListOf<StreamEvent>()
        val parser = StreamJsonParser(events::add)
        projected().forEach { parser.parseLine(it.toString()) }
        parser.parseLine("""{"type":"result","subtype":"success","is_error":false,"session_id":"print-parent-1","result":"Parent reports child failure"}""")
        assertEquals("failed", events.filterIsInstance<StreamEvent.ToolCallCompleted>().last().payload.task!!.status)
        assertFalse(events.filterIsInstance<StreamEvent.Result>().last().isError)
    }

    @Test fun `Task needs confirmed matching parent and strict untruncated IDs`() {
        val source = projected().first()
        val parsed = ToolCallPayloadParser.parse(source)!!
        assertTrue(parsed.belongsToPrintSession("print-parent-1"))
        assertFalse(parsed.belongsToPrintSession(null))
        assertFalse(parsed.belongsToPrintSession("foreign"))
        for (field in listOf("session_id", "call_id")) for (value in listOf("null", "17", "{}", "\"\"", "\"${"x".repeat(2049)}\"")) {
            assertNull(ToolCallPayloadParser.parse(source.deepCopy().apply { add(field, JsonParser.parseString(value)) }))
        }
        assertNotEquals(parsed.taskKey(), parsed.copy(parentSessionId = "different").taskKey())
    }

    @Test fun `duration validates integer representation zero maximum and malformed types`() {
        for (raw in listOf("0", "\"0\"", "25", "\"25\"", "31536000000")) {
            assertNotNull(taskDuration(json("""{"durationMs":$raw}""")))
        }
        for (raw in listOf("-1", "1.5", "1.0", "true", "null", "[]", "{}", "\"1e3\"", "\"25ms\"", "31536000001", "9223372036854775808")) {
            assertNull(taskDuration(json("""{"durationMs":$raw}""")), raw)
        }
    }

    @Test fun `large result display is bounded with visible truncation and oversized IDs are not altered`() {
        val value = json("""{"agentId":"${"i".repeat(2049)}","conversationSteps":[{"assistantMessage":{"text":"${"x".repeat(40_000)}"}}]}""")
        val task = AgentTask().withTaskOutput(value, true)
        assertNull(task.agentId)
        assertTrue(task.resultText!!.length < 33_000)
        assertTrue(task.resultText.endsWith("（表示上限）"))
    }

    @Test fun `only finite known output and custom shapes are used and missing results are unknown`() {
        for (type in listOf("\"explore\"", "{\"custom\":\"named\"}", "{\"custom\":{\"name\":\"named\"}}", "{\"custom\":{\"custom\":{\"name\":\"named\"}}}")) {
            assertNotNull(AgentTask().withTaskInput(json("""{"subagentType":$type,"prompt":"secret"}""")).name)
        }
        val source = projected().last().deepCopy()
        val payload = source.getAsJsonObject("tool_call").getAsJsonObject("taskToolCall")
        payload.remove("result")
        assertNull(ToolCallPayloadParser.parse(source)!!.task!!.status)
        payload.add("result", json("""{"error":{},"success":{"agentId":"contradictory"}}"""))
        assertEquals("failed", ToolCallPayloadParser.parse(source)!!.task!!.status)
        val task = AgentTask().withTaskOutput(json("""{"conversationSteps":[null,{},7,{"assistantMessage":{"text":"visible"}},{"userMessage":{"text":"secret"}}],"isBackground":"false"}"""), true)
        assertEquals("visible", task.resultText)
        assertNull(task.isBackground)
    }
}
