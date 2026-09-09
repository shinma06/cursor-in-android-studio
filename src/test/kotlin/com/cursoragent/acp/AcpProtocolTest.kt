package com.cursoragent.acp

import com.cursoragent.service.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AcpProtocolTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    @Test
    fun `text deltas repeat exactly and tool thought message IDs create boundaries`() {
        val protocol = AcpProtocol()
        fun text(value: String, id: String? = null): AgentEvent.Text {
            val update = jsonObject("sessionUpdate" to "agent_message_chunk").apply {
                add("content", jsonObject("type" to "text", "text" to value))
                id?.let { addProperty("messageId", it) }
            }
            return protocol.update(update) as AgentEvent.Text
        }
        assertEquals(AgentEvent.Text("はい", null, true), text("はい"))
        assertEquals(AgentEvent.Text("はい", null, false), text("はい"))
        protocol.update(json("""{"sessionUpdate":"agent_thought_chunk","content":{"type":"text","text":"考える"}}"""))
        assertEquals(AgentEvent.Text("はい", null, true), text("はい"))
        assertEquals(AgentEvent.Text("次", "next", true), text("次", "next"))
        protocol.beginTurn()
        assertEquals(AgentEvent.Text("はい", null, true), text("はい"))
    }

    @Test
    fun `opaque multiline tool IDs and partial fields merge but explicit arrays replace`() {
        val protocol = AcpProtocol()
        val id = "read\nfile\n"
        fun update(vararg fields: Pair<String, String>): AgentTool =
            (protocol.update(jsonObject("sessionUpdate" to "tool_call_update", "toolCallId" to id, *fields)) as AgentEvent.Tool).state
        assertEquals("実行", update("title" to "実行").title)
        assertTrue(protocol.hasUnfinishedTools)
        assertEquals("実行", update("status" to "in_progress").title)
        val withContent = jsonObject("sessionUpdate" to "tool_call_update", "toolCallId" to id).apply {
            add("content", JsonParser.parseString("""[{"type":"diff","path":"a","oldText":"before","newText":"after"},{"type":"content","content":{"type":"text","text":"exit 1"}}]"""))
            add("locations", JsonParser.parseString("""[{"path":"a"}]"""))
        }
        val tool = (protocol.update(withContent) as AgentEvent.Tool).state
        assertEquals(id, tool.id)
        assertEquals(2, tool.content.size)
        assertEquals(tool.content, update("status" to "completed").content)
        assertFalse(protocol.hasUnfinishedTools)
        withContent.add("content", JsonParser.parseString("[]"))
        withContent.add("locations", JsonParser.parseString("[]"))
        val cleared = (protocol.update(withContent) as AgentEvent.Tool).state
        assertTrue(cleared.content.isEmpty())
        assertTrue(cleared.locations.isEmpty())
        assertEquals("completed", cleared.status)
    }

    @Test
    fun `permission uses known tool target and exact option IDs while missing target fails closed`() {
        val protocol = AcpProtocol()
        val params = json("""{"toolCall":{"toolCallId":"0","title":"Unknown target"},"options":[{"optionId":"allow","kind":"allow_once","name":"allow"},{"optionId":"deny","kind":"reject_once","name":"deny"}]}""")
        val answers = mutableListOf<AgentAnswer>()
        val pending = AgentInputRequest(protocol.input("session/request_permission", params)!!, { answer -> answers.add(answer); answer })
        assertFalse(pending.answer(AgentAnswer.Permission("allow")))
        assertFalse(pending.answer(AgentAnswer.Permission("invented")))
        assertTrue(pending.answer(AgentAnswer.Permission("deny")))
        assertFalse(pending.answer(AgentAnswer.Cancel))
        protocol.update(json("""{"sessionUpdate":"tool_call","toolCallId":"0","rawInput":{"command":"pwd"}}"""))
        val known = AgentInputRequest(protocol.input("session/request_permission", params)!!, { answer -> answers.add(answer); answer })
        assertTrue(known.answer(AgentAnswer.Permission("allow")))
    }

    @Test
    fun `questions and plan use exact extension responses and invalid selections stay pending`() {
        val protocol = AcpProtocol()
        val question = protocol.input("cursor/ask_question", json("""{"toolCallId":"q","questions":[{"id":"a","prompt":"選択","options":[{"id":"yes","label":"はい"},{"id":"no","label":"いいえ"}],"allowMultiple":false}]}"""))!!
        var response = JsonObject()
        val pending = AgentInputRequest(question) { response = answerJson(it); it }
        assertFalse(pending.answer(AgentAnswer.Questions(mapOf("wrong" to listOf("yes")))))
        assertFalse(pending.answer(AgentAnswer.Questions(mapOf("a" to listOf("yes", "no")))))
        assertTrue(pending.answer(AgentAnswer.Questions(mapOf("a" to listOf("yes")))))
        assertEquals(json("""{"outcome":{"outcome":"answered","answers":[{"questionId":"a","selectedOptionIds":["yes"]}]}}"""), response)
        val plan = protocol.input("cursor/create_plan", json("""{"toolCallId":"p","plan":"# Plan"}"""))!!
        assertTrue(AgentInputRequest(plan) { response = answerJson(it); it }.answer(AgentAnswer.Reject))
        assertEquals(json("""{"outcome":{"outcome":"rejected"}}"""), response)
        assertNull(protocol.input("unknown", JsonObject()))
    }

    @Test
    fun `configuration replaces list and rejects missing required models instead of guessing`() {
        val config = AcpConfiguration()
        config.replace(json("""{"configOptions":[{"id":"mode","type":"select","currentValue":"agent","options":[{"value":"agent","name":"Agent"}]},{"id":"model","type":"select","currentValue":"default[]","options":[{"group":"Auto","options":[{"value":"default[]","name":"Auto"}]}]}]}"""))
        assertEquals(listOf(ModelOption("default[]", "Auto")), config.state().models)
        assertFalse(config.accepts("model", "auto"))
        config.replace(json("""{"configOptions":[]}"""))
        assertThrows(AcpException::class.java) { config.state() }
    }
}
