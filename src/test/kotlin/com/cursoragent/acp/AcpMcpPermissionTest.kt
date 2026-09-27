package com.cursoragent.acp

import com.cursoragent.service.AgentAnswer
import com.cursoragent.service.AgentInput
import com.cursoragent.service.AgentInputRequest
import com.cursoragent.ui.timeline.AgentRequestCard
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class AcpMcpPermissionTest {
    private fun fixture(): JsonObject = javaClass.getResourceAsStream("/acp/mcp-project-permission.json")!!.bufferedReader().use {
        JsonParser.parseReader(it).asJsonObject
    }

    private fun permission(fixture: JsonObject, protocol: AcpProtocol = AcpProtocol()): AgentInput.Permission {
        protocol.update(fixture.getAsJsonObject("update"))
        return protocol.input("session/request_permission", fixture.getAsJsonObject("request")) as AgentInput.Permission
    }

    private fun replaceContent(fixture: JsonObject, text: String) {
        fixture.getAsJsonObject("request").getAsJsonObject("toolCall")
            .getAsJsonArray("content")[0].asJsonObject.getAsJsonObject("content").addProperty("text", text)
    }

    @Test
    fun `CLI shaped request shows complete target and sends exactly one explicit answer`() {
        val input = permission(fixture())
        val answers = mutableListOf<AgentAnswer>()
        val pending = AgentInputRequest(input) { answers.add(it); it }
        SwingUtilities.invokeAndWait {
            val card = AgentRequestCard(pending)
            val text = card.components.filterIsInstance<JTextArea>().joinToString("\n") { it.text }
            assertTrue(text.contains("MCPサーバー: fixture-ide"))
            assertTrue(text.contains("操作: get_project_modules"))
            assertTrue(text.contains("対象project: /fixture/android"))
            assertFalse(text.contains("許可できません"))
            val allow = card.components.filterIsInstance<JButton>().first { it.text.startsWith("今回だけ許可") }
            assertTrue(allow.isEnabled)
            allow.doClick(0)
            allow.doClick(0)
        }
        assertEquals(listOf(AgentAnswer.Permission("allow-once")), answers)
        assertFalse(pending.answer(AgentAnswer.Permission("allow-always")))
    }

    @Test
    fun `unknown missing malformed and changed targets remain unapprovable`() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.getAsJsonObject("update").remove("rawInput") },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").addProperty("toolName", "execute_terminal_command") },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").addProperty("providerIdentifier", "") },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").addProperty("providerIdentifier", "a".repeat(257)) },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").getAsJsonObject("args").addProperty("secret", "never authorize") },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").getAsJsonObject("args").addProperty("projectPath", "relative") },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").getAsJsonObject("args").addProperty("projectPath", "/" + "x".repeat(2048)) },
            { it.getAsJsonObject("update").getAsJsonObject("rawInput").getAsJsonObject("args").addProperty("projectPath", "/one\n/two") },
            { it.getAsJsonObject("request").getAsJsonObject("toolCall").addProperty("kind", "execute") },
            { replaceContent(it, "```json\n{\"projectPath\":\"/other\"}\n```") },
            { replaceContent(it, "```json\n{\"projectPath\":\"/fixture/android\",\"secret\":\"hidden\"}\n```") },
            { replaceContent(it, "```json\n{\"projectPath\":\"/other\",\"projectPath\":\"/fixture/android\"}\n```") },
            { replaceContent(it, "```json\n{projectPath:'/fixture/android'}\n```") },
            { replaceContent(it, "```json\n{\"projectPath\":\"/fixture/android\"} {}\n```") },
            { replaceContent(it, "<html>get_project_modules /fixture/android</html>") },
            { replaceContent(it, "x".repeat(4097)) },
        )
        mutations.forEachIndexed { index, mutate ->
            val data = fixture().also(mutate)
            val input = permission(data)
            assertFalse(input.tool.hasPermissionTarget, "mutation $index")
            val pending = AgentInputRequest(input) { it }
            assertFalse(pending.answer(AgentAnswer.Permission("allow-once")), "mutation $index")
            assertTrue(pending.answer(AgentAnswer.Permission("reject-once")))
        }
    }

    @Test
    fun `explicit invalid raw input clears target and turn reset never reuses it`() {
        val protocol = AcpProtocol()
        val data = fixture()
        permission(data, protocol)
        val request = data.getAsJsonObject("request")
        val idOnly = JsonParser.parseString("""{"toolCall":{"toolCallId":"mcp-1"},"options":[{"optionId":"a","kind":"allow_once","name":"Allow"}]}""").asJsonObject
        assertTrue((protocol.input("session/request_permission", idOnly) as AgentInput.Permission).tool.hasPermissionTarget)
        protocol.update(JsonParser.parseString("""{"sessionUpdate":"tool_call_update","toolCallId":"mcp-1","rawInput":null}""").asJsonObject)
        assertFalse((protocol.input("session/request_permission", request) as AgentInput.Permission).tool.hasPermissionTarget)
        permission(data, protocol)
        protocol.beginTurn()
        assertFalse((protocol.input("session/request_permission", request) as AgentInput.Permission).tool.hasPermissionTarget)
    }

    @Test
    fun `stop cancellation disables MCP approval without changing exactly once replies`() {
        val replies = mutableListOf<AgentAnswer>()
        val request = AgentInputRequest(permission(fixture())) { replies.add(it); it }
        assertTrue(request.answer(AgentAnswer.Cancel))
        SwingUtilities.invokeAndWait {
            val card = AgentRequestCard(request)
            assertTrue(card.components.filterIsInstance<JButton>().none { it.isEnabled })
        }
        assertFalse(request.answer(AgentAnswer.Permission("allow-once")))
        assertEquals(listOf(AgentAnswer.Cancel), replies)
    }
}
