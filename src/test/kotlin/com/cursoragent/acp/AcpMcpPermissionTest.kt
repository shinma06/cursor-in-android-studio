package com.cursoragent.acp

import com.cursoragent.service.AgentAnswer
import com.cursoragent.service.AgentInput
import com.cursoragent.service.AgentInputRequest
import com.cursoragent.ui.timeline.AgentRequestCard
import com.google.gson.JsonNull
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
    fun `rejected permission updates cannot revive a stale target through an ID only request`() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.getAsJsonObject("toolCall").getAsJsonObject("rawInput").addProperty("toolName", "execute_terminal_command") },
            { it.getAsJsonObject("toolCall").add("rawInput", JsonNull.INSTANCE) },
            { it.getAsJsonObject("toolCall").getAsJsonObject("rawInput").getAsJsonObject("args").addProperty("secret", "hidden") },
            { it.getAsJsonObject("toolCall").getAsJsonObject("rawInput").getAsJsonObject("args").addProperty("projectPath", "/other") },
            { it.getAsJsonObject("toolCall").addProperty("kind", "execute") },
            { it.getAsJsonObject("toolCall").add("content", JsonNull.INSTANCE) },
        )
        mutations.forEachIndexed { index, mutate ->
            val protocol = AcpProtocol()
            val data = fixture()
            assertTrue(permission(data, protocol).tool.hasPermissionTarget)
            val changed = data.getAsJsonObject("request").deepCopy().apply {
                getAsJsonObject("toolCall").add("rawInput", data.getAsJsonObject("update")["rawInput"].deepCopy())
                mutate(this)
            }
            val idOnly = data.getAsJsonObject("request").deepCopy().apply {
                add("toolCall", JsonObject().apply { addProperty("toolCallId", "mcp-1") })
            }
            for (request in listOf(changed, idOnly)) {
                val input = protocol.input("session/request_permission", request) as AgentInput.Permission
                assertNull(input.tool.mcpTarget, "mutation $index")
                val replies = mutableListOf<AgentAnswer>()
                val pending = AgentInputRequest(input) { replies.add(it); it }
                SwingUtilities.invokeAndWait {
                    val buttons = AgentRequestCard(pending).components.filterIsInstance<JButton>()
                    val allow = buttons.filter { it.text.contains("許可") }
                    assertEquals(2, allow.size)
                    assertTrue(allow.all { !it.isEnabled }, "mutation $index")
                }
                assertFalse(pending.answer(AgentAnswer.Permission("allow-once")), "mutation $index")
                assertFalse(pending.answer(AgentAnswer.Permission("allow-always")), "mutation $index")
                assertTrue(pending.answer(AgentAnswer.Permission("reject-once")))
                assertEquals(listOf(AgentAnswer.Permission("reject-once")), replies)
            }
        }
    }

    @Test
    fun `valid permission updates retain only the current target without changing execution state`() {
        val protocol = AcpProtocol()
        val data = fixture()
        data.getAsJsonObject("update").addProperty("status", "completed")
        permission(data, protocol)
        val request = data.getAsJsonObject("request")
        request.getAsJsonObject("toolCall").add("rawInput", data.getAsJsonObject("update")["rawInput"].deepCopy())
        request.getAsJsonObject("toolCall").getAsJsonObject("rawInput").getAsJsonObject("args")
            .addProperty("projectPath", "/new-project")
        replaceContent(data, "```json\n{\"projectPath\":\"/new-project\"}\n```")
        val updated = protocol.input("session/request_permission", request) as AgentInput.Permission
        assertEquals("/new-project", updated.tool.mcpTarget?.projectPath)
        val idOnly = request.deepCopy().apply {
            add("toolCall", JsonObject().apply { addProperty("toolCallId", "mcp-1") })
        }
        val retained = protocol.input("session/request_permission", idOnly) as AgentInput.Permission
        assertEquals(updated.tool.mcpTarget, retained.tool.mcpTarget)
        assertFalse(protocol.hasUnfinishedTools)
        request.getAsJsonObject("toolCall").addProperty("toolCallId", "permission-only")
        assertTrue((protocol.input("session/request_permission", request) as AgentInput.Permission).tool.hasPermissionTarget)
        assertFalse(protocol.hasUnfinishedTools)
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
