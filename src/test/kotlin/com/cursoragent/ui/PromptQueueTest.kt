package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.parser.ToolCallPayloadParser
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.service.AgentTurnOutcome
import com.cursoragent.service.RestoreTarget
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.WorktreeMode
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

class PromptQueueTest {
    @org.junit.jupiter.api.Test
    fun `explicit attachment snapshot belongs to queue item across draft edits and dispatch`() {
        val draft = com.cursoragent.ui.composer.context.PromptContextDraft()
        val selection = com.cursoragent.ui.composer.context.SelectionContext("file:///A.kt", "A.kt", 0, 3, 1, 1, "old", 1)
        draft.add(selection)
        val queue = PromptQueue("owner")
        queue.add("queued", AgentMode.ASK, "auto", draft.snapshot())
        val id = queue.next()!!.id
        draft.replaceSelection(selection.key, selection.copy(text = "new", documentStamp = 2))
        draft.clearExplicit()
        queue.edit(id, "edited queued text")
        queue.resume()
        val ticket = queue.ticket(1)!!
        assertTrue(queue.dispatch(ticket, 1, true) { item ->
            assertEquals("edited queued text", item.text)
            assertEquals(listOf(selection), item.context!!.selections)
            assertTrue(draft.snapshot().selections.isEmpty())
            true
        })
    }

    @Test
    fun `unsent command recovery retains identity and context and requires explicit queue resume`() {
        val queue = PromptQueue("conversation")
        val context = com.cursoragent.ui.composer.context.PromptContextSnapshot(emptyList(), emptyList(), false)
        queue.add("東京  alpha", AgentMode.AGENT, "model", context, "command")
        val saved = queue.snapshot().single()
        queue.remove(saved.id)
        queue.restoreUnsent(saved)
        queue.restoreUnsent(saved)
        assertEquals(listOf(saved), queue.snapshot())
        assertNull(queue.next())
        queue.resume()
        assertEquals(saved, queue.next())
    }

    @Test
    fun `command identity and raw arguments belong to each queued snapshot through edit and reorder`() {
        val queue = PromptQueue("conversation")
        assertTrue(queue.add(" 東京  alpha beta ", AgentMode.AGENT, "", command = "Mixed-日本語"))
        assertTrue(queue.add("", AgentMode.AGENT, "", command = "second"))
        val first = queue.snapshot().first()
        queue.move(first.id, 1)
        queue.edit(first.id, " 大阪  beta ")
        assertEquals("Mixed-日本語", queue.snapshot().last().command)
        assertEquals("/Mixed-日本語  大阪  beta ", com.cursoragent.service.commandPrompt(queue.snapshot().last().command, queue.snapshot().last().text))
        assertEquals("/second", com.cursoragent.service.commandPrompt(queue.snapshot().first().command, queue.snapshot().first().text))
    }

    @Test
    fun `only explicitly registered immutable prompts are sent in order with their chosen mode and model`() {
        val queue = PromptQueue("conversation")
        assertFalse(queue.add(" ", AgentMode.AGENT, ""))
        queue.add("first", AgentMode.ASK, "model-a")
        queue.add("second", AgentMode.PLAN, "model-b")
        val snapshot = queue.snapshot()
        val sent = mutableListOf<QueuedPrompt>()
        repeat(2) { generation ->
            val ticket = queue.ticket(generation.toLong())!!
            assertTrue(queue.dispatch(ticket, generation.toLong(), true) { sent.add(it); true })
            assertFalse(queue.dispatch(ticket, generation.toLong(), true) { error("duplicate send") })
        }
        assertEquals(snapshot, sent)
        assertEquals(listOf(AgentMode.ASK, AgentMode.PLAN), sent.map { it.mode })
        assertEquals(listOf("model-a", "model-b"), sent.map { it.model })
        assertEquals(2, snapshot.size)
        assertEquals(0, queue.size)
        assertTrue(PromptQueue("conversation").snapshot().isEmpty()) // No restart replay.
    }

    @Test
    fun `editing moving deleting and pause resume invalidate scheduled sends and preserve stable IDs`() {
        for (change in listOf("edit", "move", "delete", "stop-resume", "close", "new-turn", "selection", "running")) {
            val queue = PromptQueue("owner")
            queue.add("first", AgentMode.AGENT, "m1")
            queue.add("second", AgentMode.ASK, "m2")
            val snapshot = queue.snapshot()
            val ticket = queue.ticket(1)!!
            var generation = 1L
            var idleOwner = true
            when (change) {
                "edit" -> { assertTrue(queue.edit(snapshot[0].id, "updated")); queue.resume() }
                "move" -> { queue.move(snapshot[0].id, 1); queue.resume() }
                "delete" -> queue.remove(snapshot[0].id)
                "stop-resume" -> { queue.pause(); queue.resume() }
                "close" -> queue.clear()
                "new-turn" -> generation++
                "selection", "running" -> idleOwner = false
            }
            assertFalse(queue.dispatch(ticket, generation, idleOwner) { error("stale $change") }, change)
            if (change == "edit") {
                assertEquals(snapshot[0].id, queue.snapshot()[0].id)
                assertEquals("updated", queue.snapshot()[0].text)
                assertEquals("m1", queue.snapshot()[0].model)
            }
            if (change == "move") assertEquals(listOf("second", "first"), queue.snapshot().map { it.text })
        }
    }

    @Test
    fun `failed preparation leaves an identifiable paused entry and started failure never retries`() {
        val queue = PromptQueue("owner")
        queue.add("first", AgentMode.AGENT, "")
        queue.add("remaining", AgentMode.AGENT, "")
        val first = queue.ticket(1)!!
        assertFalse(queue.dispatch(first, 1, true) { false })
        assertEquals(listOf("first", "remaining"), queue.snapshot().map { it.text })
        assertTrue(queue.paused)
        queue.resume()
        val accepted = queue.ticket(1)!!
        assertTrue(queue.dispatch(accepted, 1, true) { queue.pause(); true }) // A synchronous started-run failure.
        assertEquals(listOf("remaining"), queue.snapshot().map { it.text })
        assertNull(queue.ticket(2))
        assertFalse(queue.edit(queue.snapshot().single().id, ""))
        assertEquals("remaining", queue.snapshot().single().text)
    }

    @Test
    fun `real terminal classification advances only success after owner token ends and leaves other tab running`() {
        for (end in listOf("success", "result-only", "error-zero", "exit-failure", "stop", "uncertain") + AgentTurnOutcome.entries.map { it.name }) {
            val tabs = SessionTabs()
            val owner = tabs.snapshot().selected
            val queue = PromptQueue(owner.conversationId)
            tabs.updateComposer(owner.id, AgentMode.AGENT, "", "initial", 0)
            val token = tabs.beginTurn(owner.id)!!.token
            val other = tabs.open()
            tabs.updateComposer(other.id, AgentMode.AGENT, "", "parallel", 0)
            val otherToken = tabs.beginTurn(other.id)!!.token
            tabs.select(owner.id)
            queue.add("follow-up", AgentMode.ASK, "queued-model")
            val pending = mutableListOf<QueueDispatch>()
            val run = AgentRun(object : AgentProcessListener {
                private fun finish(success: Boolean) {
                    if (!tabs.finishTurn(token)) return
                    if (success) queue.ticket(1)?.let(pending::add) else queue.pause()
                }
                override fun onCompleted(exitCode: Int) = finish(exitCode == 0)
                override fun onStopped() = finish(false)
                override fun onError(message: String) = finish(false)
                override fun onUncertain(message: String) = finish(false)
                override fun onTurnOutcome(outcome: AgentTurnOutcome) = finish(outcome == AgentTurnOutcome.COMPLETED)
            })
            when (end) {
                "success" -> run.complete(0)
                "result-only" -> run.emit { it.onResultFallback("result is not physical exit") }
                "error-zero" -> { run.reportError("failure"); run.complete(0) }
                "exit-failure" -> run.complete(137)
                "stop" -> run.stop()
                "uncertain" -> run.completeUncertain("unknown")
                else -> run.complete(0, outcome = AgentTurnOutcome.valueOf(end))
            }
            val sent = mutableListOf<String>()
            SwingUtilities.invokeAndWait {
                pending.forEach { ticket ->
                    val selected = tabs.snapshot().selected
                    queue.dispatch(ticket, 1, selected.conversationId == queue.conversationId && selected.run == null) {
                        tabs.updateComposer(owner.id, it.mode, it.model, it.text, 0)
                        assertNotNull(tabs.beginTurn(owner.id))
                        sent.add(it.text)
                        true
                    }
                }
            }
            assertEquals(if (end in listOf("success", "COMPLETED")) listOf("follow-up") else emptyList<String>(), sent, end)
            assertTrue(tabs.accepts(otherToken), end)
            if (end == "result-only") assertTrue(tabs.accepts(token))
            run.complete(0) // duplicate/late terminal cannot submit another prompt.
            assertTrue(queue.size <= 1)
        }
    }

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
        queue.add("registered", AgentMode.ASK, "model")
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
            val factory = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, recorder, {}, changes, queue::pause, { _, _ -> })
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
        assertTrue(queue.dispatch(queue.ticket(1)!!, 1, true) { assertEquals(original.single(), it); true })
        assertEquals("synthetic-turn", changes.snapshot().edits.single().turnId)
    }

}
