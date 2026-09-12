package com.cursoragent.ui

import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.service.AgentTurnOutcome
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class PromptQueueTest {
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
}
