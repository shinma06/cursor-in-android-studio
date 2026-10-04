package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.service.AgentRun
import com.cursoragent.service.RestoreTarget
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import com.cursoragent.ui.composer.image.ImageAttachmentStore
import com.cursoragent.ui.composer.image.ImageInput
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.cursoragent.ui.timeline.RunPhase
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.ArrayDeque
import javax.swing.SwingUtilities

class PromptQueueSubmissionTest {
    private class Harness : AutoCloseable {
        val sessions = SessionTabs()
        val owner = sessions.snapshot().selected
        val queue = PromptQueue(owner.conversationId)
        val timeline = ChatTimelinePanel()
        val tasks = ArrayDeque<() -> Unit>()
        val attempts = mutableListOf<QueuedPrompt>()
        val released = mutableSetOf<AgentRun>()
        val phases = mutableListOf<RunPhase>()
        var active: AgentRun? = null
        var generation = 0L
        var current = true
        var canStart = true
        var stopRequests = 0
        var stopThrows = false
        var stopFailures = 0
        var pauseDuringStart = false
        val submission = PromptQueueSubmission(queue,
            { current && sessions.snapshot().selectedId == owner.id }, { active }, { generation }, tasks::add,
            start = { entry ->
                assertNull(active)
                assertTrue(released.containsAll(foregroundRuns), "the old run must release its reservation before starting")
                attempts.add(entry)
                if (pauseDuringStart) queue.pause()
                if (canStart) foreground()
                canStart
            },
            stop = { it.stop() }, changed = {}, stopFailed = { stopFailures++ })
        private val foregroundRuns = mutableListOf<AgentRun>()
        private val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            if (method.name == "isDisposed") false else error("Unexpected project access: ${method.name}")
        } as Project

        fun foreground(exitObserved: Boolean = false): AgentRun {
            sessions.updateComposer(owner.id, AgentMode.AGENT, "exact-model", "running", 0)
            val token = requireNotNull(sessions.beginTurn(owner.id)).token
            generation++
            lateinit var run: AgentRun
            val listener = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, ConversationRecorder(Conversation()) {},
                { phase ->
                    phases.add(phase)
                    sessions.finishTurn(token)
                    assertSame(run, active)
                    active = null
                    submission.finished(run, phase)
                }, ConversationChanges(owner.conversationId), submission::cancel,
                onUsageFinish = { _, _ -> }, onToolNotice = {}, onTerminalNotice = { _, _ -> },
            ).create(1, token.turnId, { sessions.accepts(token) }, { run.wasStopped }, { true }, { RestoreTarget.UNKNOWN })
            run = AgentRun(listener) { released.add(it) }
            active = run
            foregroundRuns.add(run)
            run.attachProcess({ stopRequests++; if (stopThrows) error("synthetic cancellation failure") }, { exitObserved })
            return run
        }

        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst()() }

        override fun close() {
            submission.cancel()
            active?.detachListener()
            active?.stop()
            queue.clear()
            timeline.runStatus.dispose()
        }
    }

    @Test
    fun `selected send waits for the real stopped listener and reservation release and transfers only that snapshot`(@TempDir parent: Path) {
        ImageAttachmentStore(parent).use { store ->
            val image = store.save(ImageInput.clipboard(BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB)))
            SwingUtilities.invokeAndWait {
                Harness().use { h ->
                    val running = h.foreground()
                    h.queue.add("first", AgentMode.AGENT, "one")
                    val context = PromptContextSnapshot(emptyList(), emptyList(), false)
                    h.queue.add(" 東京 ", AgentMode.PLAN, "variant-id", context, "command", image)
                    h.queue.add("last", AgentMode.ASK, "three")
                    val original = h.queue.snapshot()
                    val oldTicket = h.queue.ticket(h.generation)!!
                    h.queue.pause()
                    assertTrue(h.submission.submit(original[1]))
                    assertTrue(h.queue.paused)
                    assertFalse(h.submission.available)
                    assertEquals(1, h.stopRequests)
                    assertSame(running, h.active)
                    assertEquals(original, h.queue.snapshot())
                    assertFalse(h.submission.submit(original[0]))
                    assertTrue(h.tasks.isEmpty(), "a Stop request is not confirmation that a process has finished")
                    running.complete(7, "shutdown stderr")
                    assertEquals(listOf(RunPhase.STOPPED), h.phases)
                    assertTrue(h.attempts.isEmpty(), "start must wait past AgentRun.complete's finally")
                    assertTrue(h.released.contains(running))
                    h.drain()
                    assertEquals(listOf(original[1]), h.attempts)
                    assertSame(context, h.attempts.single().context)
                    assertSame(image, h.attempts.single().image)
                    assertEquals(listOf(original[0], original[2]), h.queue.snapshot())
                    assertTrue(h.queue.paused, "sending one row must not resume all remaining rows")
                    assertFalse(h.queue.dispatch(oldTicket, h.generation, true) { error("stale automatic ticket") })
                    running.complete(0)
                    h.drain()
                    assertEquals(1, h.attempts.size)
                    assertEquals(20, image.width)
                }
            }
            assertTrue(image.bytes().isNotEmpty(), "removing the sent row transfers, rather than closes, its lease")
            image.close()
        }
    }

    @Test
    fun `send now preserves automatic delivery but cannot undo a later pause or unsent recovery`() = SwingUtilities.invokeAndWait {
        for (change in listOf("none", "pause", "recovery", "another row", "during start")) Harness().use { h ->
            val run = h.foreground()
            h.queue.add("first", AgentMode.AGENT, "one")
            h.queue.add("selected", AgentMode.ASK, "two")
            val original = h.queue.snapshot()
            assertFalse(h.queue.paused)
            assertTrue(h.submission.submit(original[1]))
            run.complete(0)
            when (change) {
                "pause" -> h.queue.pause()
                "recovery" -> h.queue.restoreUnsent(QueuedPrompt(text = "unsent previous prompt", mode = AgentMode.PLAN, model = "old"))
                "another row" -> h.queue.edit(original[0].id, "updated")
                "during start" -> h.pauseDuringStart = true
            }
            h.drain()
            assertEquals(listOf(original[1]), h.attempts)
            assertEquals(change != "none", h.queue.paused, change)
            if (change == "none") assertSame(original[0], h.queue.next())
            else assertNull(h.queue.ticket(h.generation), "a later recovery/pause needs an explicit resume")
        }
    }

    @Test
    fun `idle sends preserve failed rows and require another explicit request`() = SwingUtilities.invokeAndWait {
        Harness().use { h ->
            h.queue.add("one", AgentMode.AGENT, "exact")
            val entry = h.queue.snapshot().single()
            h.canStart = false
            assertFalse(h.submission.submit(entry))
            assertEquals(listOf(entry), h.queue.snapshot())
            assertTrue(h.queue.paused)
            assertTrue(h.tasks.isEmpty())
            h.canStart = true
            assertTrue(h.submission.submit(entry))
            assertEquals(listOf(entry, entry), h.attempts)
            assertEquals(0, h.queue.size)
            assertFalse(h.submission.submit(entry))
        }
    }

    @Test
    fun `uncertain and failed terminal outcomes never dispatch while an observed successful exit may proceed`() = SwingUtilities.invokeAndWait {
        for (outcome in listOf("uncertain", "error", "completed")) Harness().use { h ->
            val run = h.foreground(exitObserved = outcome != "uncertain")
            h.queue.add("selected", AgentMode.ASK, "exact")
            val entry = h.queue.snapshot().single()
            assertTrue(h.submission.submit(entry))
            when (outcome) {
                "uncertain" -> run.completeUncertain("synthetic unknown termination")
                "error" -> run.complete(1, "synthetic failure")
                else -> run.complete(0)
            }
            h.drain()
            if (outcome == "completed") {
                assertEquals(listOf(RunPhase.COMPLETED), h.phases)
                assertEquals(listOf(entry), h.attempts)
            } else {
                assertEquals(listOf(RunPhase.FAILED), h.phases)
                assertTrue(h.attempts.isEmpty())
                assertEquals(listOf(entry), h.queue.snapshot())
                assertTrue(h.queue.paused)
            }
        }
    }

    @Test
    fun `late sends cannot outlive cancellation ownership generation editing or their selected row`() = SwingUtilities.invokeAndWait {
        for (change in listOf("cancel", "owner", "unavailable", "generation", "replace", "remove", "editing", "new run")) Harness().use { h ->
            val run = h.foreground()
            h.queue.add("selected", AgentMode.ASK, "exact")
            val entry = h.queue.snapshot().single()
            assertTrue(h.submission.submit(entry))
            assertFalse(h.submission.finished(AgentRun(object : com.cursoragent.service.AgentProcessListener {}), RunPhase.STOPPED))
            run.complete(0)
            assertEquals(1, h.tasks.size)
            when (change) {
                "cancel" -> h.submission.cancel()
                "owner" -> h.sessions.open()
                "unavailable" -> h.current = false
                "generation" -> h.generation++
                "replace" -> h.queue.edit(entry.id, "edited")
                "remove" -> h.queue.remove(entry.id)
                "editing" -> assertTrue(h.queue.beginEdit(entry))
                "new run" -> h.foreground()
            }
            h.drain()
            assertTrue(h.attempts.isEmpty(), change)
            assertTrue(h.queue.paused)
        }
    }

    @Test
    fun `a failed stop clears its intent without deleting the row or retrying`() = SwingUtilities.invokeAndWait {
        Harness().use { h ->
            val run = h.foreground()
            h.queue.add("selected", AgentMode.AGENT, "exact")
            val entry = h.queue.snapshot().single()
            h.stopThrows = true
            assertFalse(h.submission.submit(entry))
            assertEquals(1, h.stopFailures)
            assertTrue(h.queue.paused)
            run.complete(0)
            h.drain()
            assertTrue(h.attempts.isEmpty())
            assertEquals(listOf(entry), h.queue.snapshot())
        }
    }
}
