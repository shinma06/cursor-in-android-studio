package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.parser.ToolCallPayloadParser
import com.cursoragent.service.*
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.WorktreeMode
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.cursoragent.ui.timeline.FileEditCard
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import java.awt.Component
import java.awt.Container
import java.lang.reflect.Proxy
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class QueuedRevertDispatchTest {
    private class RestoreBoundary : RuntimeException()
    private fun descendants(value: Component): List<Component> = listOf(value) +
        if (value is Container) value.components.flatMap(::descendants) else emptyList()

    @Test fun `real listener card action pauses queued EDT ticket before restore reservation and preserves other tab`(@TempDir root: Path) {
        val sessions = SessionTabs()
        val owner = sessions.snapshot().selected
        val other = sessions.open()
        sessions.updateComposer(other.id, AgentMode.ASK, "", "other", 0)
        val otherRun = sessions.beginTurn(other.id)!!.token
        sessions.select(owner.id)
        val queue = PromptQueue(owner.conversationId)
        val context = PromptContextSnapshot(emptyList(), emptyList(), false)
        queue.add("registered", AgentMode.ASK, "model", context)
        val original = queue.snapshot()
        val ticket = queue.ticket(1)!!
        var boundary = 0
        var sent = 0
        val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            when (method.name) {
                "isDisposed" -> false
                "getService" -> {
                    assertTrue(queue.paused, "pause must precede the real restore-service lookup")
                    assertFalse(queue.dispatch(ticket, 1, true) { sent++; true })
                    boundary++
                    throw RestoreBoundary() // Stop at the service boundary; no IDE or filesystem mutation.
                }
                else -> error("Unexpected Project access: ${method.name}")
            }
        } as Project
        val recorder = ConversationRecorder(Conversation(id = owner.conversationId)) {}
        val changes = ConversationChanges(owner.conversationId)
        val target = RestoreTarget.capture(root.toString(), WorktreeMode.DEFAULT)
        val completed = ToolCallPayloadParser.parse(JsonParser.parseString(
            javaClass.getResource("/stream-json-fixtures/02_edit_completed.jsonl")!!.readText().trim(),
        ).asJsonObject)!!
        SwingUtilities.invokeAndWait {
            val timeline = ChatTimelinePanel()
            val factory = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, recorder, {}, changes, queue::pause)
            recorder.begin("synthetic-turn", "prompt")
            val listener = factory.create(0, "synthetic-turn", { true }, { false }, { true }, { target })
            listener.onToolCallCompleted(completed)
            val card = descendants(timeline).filterIsInstance<FileEditCard>().single()
            val revert = descendants(card).filterIsInstance<JButton>().single { it.text == "Revert" }
            SwingUtilities.invokeLater { queue.dispatch(ticket, 1, true) { sent++; true } }
            assertThrows(RestoreBoundary::class.java) { revert.doClick(0) }
        }
        SwingUtilities.invokeAndWait {}
        assertEquals(1, boundary)
        assertEquals(0, sent)
        assertTrue(sessions.accepts(otherRun))
        assertEquals(original, queue.snapshot())
        assertNull(queue.ticket(1))
        queue.resume()
        assertFalse(queue.dispatch(ticket, 1, true) { error("old ticket cannot revive") })
        assertTrue(queue.dispatch(queue.ticket(1)!!, 1, true) { assertEquals(context, it.context); true })
        assertEquals("synthetic-turn", changes.snapshot().edits.single().turnId)
    }

    @Test fun `listener changes preserve supplied actual turn IDs and exact text`() = SwingUtilities.invokeAndWait {
        val sessions = SessionTabs()
        val owner = sessions.snapshot().selected
        val queue = PromptQueue(owner.conversationId)
        val recorder = ConversationRecorder(Conversation(id = owner.conversationId)) {}
        val changes = ConversationChanges(owner.conversationId)
        val timeline = ChatTimelinePanel()
        val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            if (method.name == "isDisposed") false else error("Unexpected project access")
        } as Project
        val turns = mutableListOf<String>()
        val factory = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, recorder, {}, changes, queue::pause)
        fun start(text: String): Boolean {
            sessions.updateComposer(owner.id, AgentMode.AGENT, "", text, 0)
            val token = sessions.beginTurn(owner.id)!!.token
            turns.add(token.turnId)
            recorder.begin(token.turnId, text)
            changes.beginTurn(token.turnId)
            val listener = factory.create(0, token.turnId, { sessions.accepts(token) }, { false }, { true }, { RestoreTarget.UNKNOWN })
            listener.onAssistantText("exact-$text")
            listener.onStructuredEvent(AgentEvent.Tool(AgentTool("reused", status = "completed", content = listOf(AgentToolContent.Diff("a", "before", text)))))
            assertEquals("exact-$text", recorder.conversation.turns.last().messages.first { it.role == "assistant" }.text)
            recorder.finish("completed")
            assertTrue(sessions.finishTurn(token))
            return true
        }
        start("manual")
        queue.add("queued", AgentMode.ASK, "")
        assertTrue(queue.dispatch(queue.ticket(1)!!, 1, sessions.snapshot().selected.run == null) { start(it.text) })
        assertEquals(2, turns.toSet().size)
        assertEquals(turns, changes.snapshot().turns)
        assertEquals(turns, changes.snapshot().edits.map { it.turnId })
        assertEquals(listOf("exact-manual", "exact-queued"), recorder.conversation.turns.map { it.messages.first { message -> message.role == "assistant" }.text })
    }
}
