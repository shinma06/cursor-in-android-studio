package com.cursoragent.ui

import com.cursoragent.acp.AcpProtocol
import com.cursoragent.history.*
import com.cursoragent.service.*
import com.cursoragent.ui.timeline.*
import com.google.gson.JsonParser
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AcpContentRecordingTest {
    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap(::descendants) else emptyList()

    @Test fun `text media text retain exact order source and literal metadata after save and restore`(@TempDir dir: Path) {
        val store = ConversationStore(dir)
        val recorder = ConversationRecorder(Conversation(transport = AgentTransport.ACP), store::save)
        recorder.begin(newHistoryId(), "入力")
        val literal = "<img src='https://example.invalid/image'> [link](javascript:synthetic())"
        val p = AcpProtocol()
        SwingUtilities.invokeAndWait {
            val timeline = ChatTimelinePanel()
            val assistant = TurnAssistantText({ timeline.setAssistantText(it); recorder.assistant(it) }, {
                timeline.finalizeAssistantMessage(); recorder.newAssistant()
            })
            for (content in listOf(
                """{"type":"text","text":"前 **raw**\r\n"}""",
                """{"type":"resource","resource":{"uri":"file:///synthetic","text":"$literal","mimeType":"text/html"}}""",
                """{"type":"text","text":"後"}""", """{"type":"text","text":"後"}""",
            )) {
                when (val event = p.update(JsonParser.parseString("""{"sessionUpdate":"agent_message_chunk","messageId":"one","content":$content}""").asJsonObject)) {
                    is AgentEvent.Text -> assistant.acpDelta(event)
                    is AgentEvent.Content -> {
                        assistant.interrupt()
                        timeline.addAssistantContent(event.summary.displayText())
                        recorder.assistantContent(event.summary.displayText())
                    }
                    else -> fail("unexpected event")
                }
            }
            recorder.finish("completed")
            val messages = recorder.conversation.turns.single().messages
            assertEquals(listOf("user", "assistant", "assistant", "assistant"), messages.map { it.role })
            assertEquals(listOf(null, null, "acp_content", null), messages.map { it.presentation })
            assertEquals("前 **raw**\r\n", messages[1].text)
            assertEquals("後後", messages.last().text)
            assertTrue(messages[2].text.contains(literal))
            val restored = ChatTimelinePanel()
            restored.restore(store.load().conversations.single())
            val rows = descendants(restored).filter { it is AssistantMessageBubble || it is AssistantContentRow }
            assertEquals(listOf(AssistantMessageBubble::class.java, AssistantContentRow::class.java, AssistantMessageBubble::class.java), rows.map { it.javaClass })
            val metadataRow = rows[1]
            assertTrue(descendants(metadataRow).filterIsInstance<JTextArea>().single().text.contains(literal))
            assertTrue(descendants(metadataRow).none { it is MessageTextPane || it is JButton })
            metadataRow.setSize(300, 500)
            val graphics = BufferedImage(300, 500, BufferedImage.TYPE_INT_ARGB).createGraphics()
            try { metadataRow.paint(graphics) } finally { graphics.dispose() }
            assertEquals(recorder.conversation, store.load().conversations.single())
        }
    }

    @Test fun `tool card keeps content order exact diff action replacement and independent content state`() = SwingUtilities.invokeAndWait {
        val summary = AgentToolContent.Summary("image", ContentDisplayState.INVALID, "URI: file:///synthetic")
        val diff = AgentToolContent.Diff("a", "b", "c")
        val seen = mutableListOf<AgentToolContent.Diff>()
        val tool = AgentTool("one", status = "completed", content = listOf(AgentToolContent.Text("before"), diff, summary, AgentToolContent.Text("after")))
        val timeline = ChatTimelinePanel()
        timeline.upsertStructuredTool(tool, seen::add)
        val first = descendants(timeline).filterIsInstance<StructuredToolCard>().single()
        val text = descendants(first).filter { it is JTextArea || it is JButton }.map {
            if (it is JTextArea) it.text else (it as JButton).text
        }
        assertTrue(text.first().startsWith("報告完了"))
        assertEquals(listOf("before", "差分を表示", summary.displayText(), "after"), text.drop(1))
        descendants(first).filterIsInstance<JButton>().single().doClick(0)
        assertEquals(listOf(diff), seen)
        timeline.upsertStructuredTool(tool.copy(content = emptyList()), seen::add)
        val replaced = descendants(timeline).filterIsInstance<StructuredToolCard>().single()
        assertNotSame(first, replaced)
        assertTrue(descendants(replaced).filterIsInstance<JButton>().isEmpty())
        assertFalse(descendants(replaced).filterIsInstance<JTextArea>().any { it.text.contains("file:///synthetic") })
        val saved = safeContentSummary(tool.copy(content = listOf(summary, AgentToolContent.Summary("SECRET_TYPE", ContentDisplayState.UNSUPPORTED, "SECRET_DATA"))))
        assertTrue(saved.contains("image: 内容の形式が不正"))
        assertFalse(saved.contains("SECRET"))
        assertFalse(saved.contains("file:"))
    }

    @Test fun `existing version one remains readable and unsupported presentation fails without overwriting`(@TempDir dir: Path) {
        val store = ConversationStore(dir)
        val old = Conversation(turns = listOf(SavedTurn(state = "completed", messages = listOf(ChatMessage(role = "assistant", text = "原文")))))
        store.save(old)
        assertEquals(old, store.load().conversations.single())
        val invalid = old.copy(turns = old.turns.map { it.copy(messages = it.messages.map { m -> m.copy(presentation = "unknown") }) })
        assertThrows(IllegalArgumentException::class.java) { store.save(invalid) }
        assertEquals(old, store.load().conversations.single())
    }
}
