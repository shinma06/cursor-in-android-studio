package com.cursoragent.acp

import com.cursoragent.service.AgentEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaskProtocolTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private fun initial() = json("""{"sessionUpdate":"tool_call","toolCallId":"t","kind":"other","status":"pending","rawInput":{"_toolName":"task","description":"safe description","prompt":"never retain","agentId":"request-id","subagentType":{"custom":{"name":"reader"}}}}""")
    private fun update(extra: String) = json("""{"sessionUpdate":"tool_call_update","toolCallId":"t",$extra}""")

    @Test fun `public ACP projection joins metadata to the same tool without inventing child output`() {
        val data = JsonParser.parseString(javaClass.getResource("/task/issue-118-projection.json")!!.readText()).asJsonObject
        val protocol = AcpProtocol().apply { beginTurn() }
        val states = data.getAsJsonArray("acp").mapNotNull { item ->
            val value = item.asJsonObject
            if (value.has("sessionUpdate")) (protocol.update(value) as AgentEvent.Tool).state
            else if (value.string("method") == "cursor/task") protocol.taskMetadata(value.getAsJsonObject("params"))?.state else null
        }
        assertEquals(listOf("pending", "in_progress", "completed", "completed"), states.map { it.status })
        assertEquals(1, states.map { it.id }.distinct().size)
        val task = states.last().task!!
        assertEquals("issue118-reader", task.name)
        assertEquals("default", task.model)
        assertEquals("acp-child-1", task.reportedAgentId)
        assertNull(task.agentId)
        assertEquals(7303L, task.durationMs)
        assertEquals(false, task.isBackground)
        assertNull(task.resultText)
        assertFalse(protocol.hasUnfinishedTools)
    }

    @Test fun `missing groups retain values but explicit empty null and invalid groups replace them`() {
        val protocol = AcpProtocol().apply { beginTurn(); update(initial()) }
        val first = protocol.update(update(""""rawOutput":{"durationMs":25,"isBackground":false},"content":[{"type":"content","content":{"type":"text","text":"provided tool text"}}]""")) as AgentEvent.Tool
        val retained = (protocol.update(update(""""status":"in_progress"""")) as AgentEvent.Tool).state
        assertEquals(first.state.task, retained.task)
        assertEquals(first.state.content, retained.content)
        for (replacement in listOf("{}", "null", "[]", "7")) {
            val cleared = (protocol.update(update(""""rawInput":$replacement,"rawOutput":$replacement,"content":[],"locations":[]""")) as AgentEvent.Tool).state
            assertNull(cleared.task!!.name)
            assertNull(cleared.task.requestedAgentId)
            assertNull(cleared.task.durationMs)
            assertNull(cleared.task.isBackground)
            assertTrue(cleared.content.isEmpty())
        }
    }

    @Test fun `failed status survives supplementary metadata and contradictory completion`() {
        val protocol = AcpProtocol().apply { beginTurn(); update(initial()) }
        protocol.update(update(""""status":"failed""""))
        val task = protocol.taskMetadata(json("""{"toolCallId":"t","agentId":"reported","model":"default"}"""))!!.state
        assertEquals("failed", task.status)
        assertEquals("request-id", task.task!!.requestedAgentId)
        assertEquals("reported", task.task.reportedAgentId)
        assertEquals("failed", (protocol.update(update(""""status":"completed"""")) as AgentEvent.Tool).state.status)
    }

    @Test fun `unknown malformed unrelated and previous-turn task IDs never create tools`() {
        val protocol = AcpProtocol().apply { beginTurn() }
        for (id in listOf("\"unknown\"", "null", "17", "{}")) {
            assertNull(protocol.taskMetadata(json("""{"toolCallId":$id,"model":"wrong"}""")))
        }
        protocol.update(json("""{"sessionUpdate":"tool_call","toolCallId":"read","kind":"read","status":"completed"}"""))
        assertNull(protocol.taskMetadata(json("""{"toolCallId":"read","model":"wrong"}""")))
        protocol.update(initial())
        protocol.beginTurn()
        assertNull(protocol.taskMetadata(json("""{"toolCallId":"t","model":"old turn"}""")))
        assertFalse(protocol.hasUnfinishedTools)
        protocol.update(initial())
        assertNull(protocol.taskMetadata(json("""{"toolCallId":"t","model":"ambiguous reused ID"}""")))
    }

    @Test fun `metadata identity bound keeps standard tools usable without forgetting ambiguous IDs`() {
        val protocol = AcpProtocol()
        repeat(9) { turn ->
            protocol.beginTurn()
            repeat(512) { index ->
                protocol.update(initial().apply { addProperty("toolCallId", "$turn-$index") })
            }
        }
        protocol.beginTurn()
        protocol.update(initial())
        assertNull(protocol.taskMetadata(json("""{"toolCallId":"t","model":"uncertain identity"}""")))
        assertEquals("completed", (protocol.update(update(""""status":"completed"""")) as AgentEvent.Tool).state.status)
    }

    @Test fun `invalid metadata replacement clears known fields and does not stringify unknown custom objects`() {
        val protocol = AcpProtocol().apply { beginTurn(); update(initial()) }
        protocol.taskMetadata(json("""{"toolCallId":"t","model":"known","agentId":"reported","durationMs":1}"""))
        val task = protocol.taskMetadata(json("""{"toolCallId":"t","model":null,"agentId":[],"durationMs":true,"subagentType":{"custom":{"mystery":"private"}}}"""))!!.state.task!!
        assertNull(task.name)
        assertNull(task.model)
        assertNull(task.reportedAgentId)
        assertNull(task.durationMs)
        assertFalse(task.toString().contains("private"))
        assertFalse(task.toString().contains("never retain"))
    }
}
