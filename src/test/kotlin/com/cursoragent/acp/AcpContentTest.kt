package com.cursoragent.acp

import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import com.cursoragent.service.ContentDisplayState.*
import com.cursoragent.service.displayText
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AcpContentTest {
    private fun json(text: String) = JsonParser.parseString(text)
    private fun tool(protocol: AcpProtocol, fields: String): AgentTool =
        (protocol.update(json("""{"sessionUpdate":"tool_call_update","toolCallId":"same",$fields}""").asJsonObject) as AgentEvent.Tool).state
    private fun chunk(protocol: AcpProtocol, value: String): AgentEvent? = protocol.update(
        json("""{"sessionUpdate":"agent_message_chunk","messageId":"same","content":$value}""").asJsonObject,
    )

    @Test fun `all standard nontext blocks show finite metadata without retaining binary or inferring actual sizes`() {
        val blocks = listOf(
            """{"type":"image","mimeType":"image/png","data":"SYNTHETIC_BINARY","uri":"https://example.invalid/i"}""",
            """{"type":"audio","mimeType":"audio/wav","data":"SYNTHETIC_BINARY"}""",
            """{"type":"resource_link","name":"日本語","uri":"file:///synthetic","size":0,"title":"見出し","description":"説明","mimeType":"text/html"}""",
            """{"type":"resource","resource":{"uri":"javascript:synthetic()","text":"<img src='file:///synthetic'>","mimeType":"text/html"}}""",
            """{"type":"resource","resource":{"uri":"data:synthetic","blob":"SYNTHETIC_BINARY"}}""",
        )
        val content = AcpContent().tool(json(blocks.joinToString(prefix = "[", postfix = "]") { """{"type":"content","content":$it}""" }))
        assertEquals(listOf("image", "audio", "resource_link", "resource (text)", "resource (blob)"), content.map { (it as AgentToolContent.Summary).type })
        assertTrue(content.all { (it as AgentToolContent.Summary).state == METADATA })
        val displayed = content.joinToString { (it as AgentToolContent.Summary).displayText() }
        assertFalse(displayed.contains("SYNTHETIC_BINARY"))
        assertTrue(displayed.contains("申告サイズ: 0 bytes（未検証）"))
        assertTrue(displayed.contains("<img src='file:///synthetic'>"))
        assertTrue(displayed.contains("サイズ: 不明"))
        assertFalse(AgentTool("id", content = content).hasPermissionTarget)
    }

    @Test fun `mixed content isolates invalid siblings and leaves status and native diff intact`() {
        val protocol = AcpProtocol()
        val state = tool(protocol, """"status":"completed","content":[null,3,{}, {"type":"content","content":{"type":"text","text":"good"}}, {"type":"diff","path":"a","oldText":"before","newText":"after"}, {"type":"terminal","terminalId":"synthetic"}, {"type":"content","content":{"type":"future","raw":"SECRET"}}, {"type":"content","content":{"type":"text","text":false}}]""")
        assertEquals(8, state.content.size)
        assertEquals(AgentToolContent.Text("good"), state.content[3])
        assertEquals(AgentToolContent.Diff("a", "before", "after"), state.content[4])
        assertEquals(AgentToolContent.Unsupported("terminal"), state.content[5])
        assertEquals(UNSUPPORTED, (state.content[6] as AgentToolContent.Summary).state)
        assertEquals(INVALID, (state.content.last() as AgentToolContent.Summary).state)
        assertFalse(state.toString().contains("SECRET"))
        assertFalse(protocol.hasUnfinishedTools)
        tool(protocol, """"status":"in_progress"""")
        assertTrue(protocol.hasUnfinishedTools)
    }

    @Test fun `missing retains but empty clears and invalid supplied arrays replace stale content and locations`() {
        for (replacement in listOf("null", "false", "{}", "\"wrong\"")) {
            val p = AcpProtocol()
            val initial = tool(p, """"content":[{"type":"content","content":{"type":"text","text":"old"}}],"locations":[{"path":"old"}]""")
            val omitted = tool(p, """"status":"completed"""")
            assertEquals(initial.content, omitted.content)
            assertEquals(initial.locations, omitted.locations)
            val invalid = tool(p, """"content":$replacement,"locations":$replacement""")
            assertEquals(INVALID, (invalid.content.single() as AgentToolContent.Summary).state)
            assertTrue(invalid.locations.isEmpty())
            assertNotNull(invalid.locationsNotice)
            assertFalse(invalid.hasPermissionTarget)
            val empty = tool(p, """"content":[],"locations":[]""")
            assertTrue(empty.content.isEmpty())
            assertTrue(empty.locations.isEmpty())
            assertNull(empty.locationsNotice)
        }
        val mixed = tool(AcpProtocol(), """"locations":[null,{"path":3},{"path":"a"},{"path":"b"}]""")
        assertEquals(listOf("a", "b"), mixed.locations)
        assertNotNull(mixed.locationsNotice)
    }

    @Test fun `missing required metadata and invalid optional sizes are visible without inventing validity`() {
        val invalid = listOf("null", "[]", "{}", """{"type":""}""", """{"type":"image","data":"x","mimeType":42}""",
            """{"type":"audio","mimeType":"audio/wav","data":null}""",
            """{"type":"resource_link","name":"n"}""",
            """{"type":"resource","resource":{"uri":"x","text":"t","blob":"b"}}""") +
            listOf("-1", "1.5", "1e4", "\"2\"", "null", "9223372036854775808").map {
                """{"type":"resource_link","name":"n","uri":"u","size":$it}"""
            }
        for (value in invalid) {
            val result = chunk(AcpProtocol(), value) as AgentEvent.Content
            assertEquals(INVALID, result.summary.state, value)
        }
    }

    @Test fun `item metadata text diff and cumulative turn limits are explicit and bounded`() {
        val items = JsonArray().apply { repeat(AcpContent.MAX_ITEMS + 1) { add(json("""{"type":"content","content":{"type":"text","text":"ok"}}""")) } }
        val result = AcpContent().tool(items)
        assertEquals(AcpContent.MAX_ITEMS + 1, result.size)
        assertEquals(LIMITED, (result.last() as AgentToolContent.Summary).state)
        val text = "x".repeat(AcpContent.MAX_TEXT + 1)
        val reader = AcpContent()
        val longText = reader.tool(json("""[{"type":"content","content":{"type":"text","text":"$text"}}]""")).single() as AgentToolContent.Summary
        assertEquals(LIMITED, longText.state)
        assertTrue(longText.details.length < text.length + 30)
        val diff = reader.tool(json("""[{"type":"diff","path":"a","newText":"$text"}]""")).single()
        assertEquals(LIMITED, (diff as AgentToolContent.Summary).state)
        val path = "p".repeat(AcpContent.MAX_METADATA + 1)
        assertTrue(reader.locations(json("""[{"path":"$path"},{"path":"good"}]""")).first == listOf("good"))
        val image = chunk(AcpProtocol(), """{"type":"image","mimeType":"image/png","uri":"$path","data":"x"}""") as AgentEvent.Content
        assertEquals(LIMITED, image.summary.state)
        val budget = AcpContent()
        val full = json("""[{"type":"content","content":{"type":"text","text":"${"x".repeat(AcpContent.MAX_TEXT)}"}}]""")
        repeat(AcpContent.MAX_TURN_TEXT / AcpContent.MAX_TEXT) { assertTrue(budget.tool(full).single() is AgentToolContent.Text) }
        assertEquals(LIMITED, (budget.tool(full).single() as AgentToolContent.Summary).state)
    }

    @Test fun `assistant nontext separates exact text deltas including after item limit and resets next turn`() {
        val p = AcpProtocol()
        assertEquals(AgentEvent.Text("はい", "same", true), chunk(p, """{"type":"text","text":"はい"}"""))
        assertEquals(AgentEvent.Text("はい", "same", false), chunk(p, """{"type":"text","text":"はい"}"""))
        val media = """{"type":"audio","data":"synthetic","mimeType":"audio/wav"}"""
        repeat(AcpContent.MAX_ASSISTANT_ITEMS) { assertEquals(METADATA, (chunk(p, media) as AgentEvent.Content).summary.state) }
        assertEquals(LIMITED, (chunk(p, media) as AgentEvent.Content).summary.state)
        assertNull(chunk(p, media))
        assertEquals(AgentEvent.Text("後", "same", true), chunk(p, """{"type":"text","text":"後"}"""))
        p.beginTurn()
        assertEquals(METADATA, (chunk(p, media) as AgentEvent.Content).summary.state)
        assertNull(p.update(json("""{"sessionUpdate":"agent_thought_chunk","content":$media}""").asJsonObject))
    }
    @Test fun `existing turn and tool ceilings remain hard failures`() {
        val p = AcpProtocol()
        repeat(512) {
            p.update(jsonObject("sessionUpdate" to "tool_call", "toolCallId" to "$it", "status" to "completed"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            p.update(jsonObject("sessionUpdate" to "tool_call", "toolCallId" to "overflow"))
        }
        val payload = AcpProtocol()
        val large = jsonObject("sessionUpdate" to "unrecognized", "padding" to "x".repeat(1024 * 1024 - 100))
        repeat(4) { assertNull(payload.update(large)) }
        assertThrows(IllegalArgumentException::class.java) { payload.update(large) }
    }

}
