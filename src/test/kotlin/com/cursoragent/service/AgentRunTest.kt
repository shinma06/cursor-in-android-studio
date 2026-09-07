package com.cursoragent.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AgentRunTest {
    private class RecordingListener : AgentProcessListener {
        val events = mutableListOf<String>()
        override fun onStopped() { events += "stopped" }
        override fun onCompleted(exitCode: Int) { events += "completed:$exitCode" }
        override fun onError(message: String) { events += "error:$message" }
        override fun onAssistantDelta(text: String) { events += text }
    }

    @Test
    fun `intentional stop suppresses shutdown errors and completes once`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        var destroys = 0
        run.attachProcess({ destroys++ }, { false })
        run.emit { it.onAssistantDelta("partial") }
        run.stop()
        run.stop()
        run.reportError("shutdown result error")
        run.emit { it.onAssistantDelta("late output") }
        assertEquals(listOf("partial"), listener.events)
        run.complete(137, "killed")
        run.complete(137)
        assertEquals(1, destroys)
        assertEquals(listOf("partial", "stopped"), listener.events)
    }

    @Test
    fun `exit 137 without a stop request remains a real failure`() {
        val listener = RecordingListener()
        AgentRun(listener).complete(137)
        assertEquals(listOf("completed:137"), listener.events)
    }

    @Test
    fun `real stderr error is delivered once without a second completion notification`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        run.complete(1, "actual error")
        run.complete(1)
        assertEquals(listOf("error:actual error"), listener.events)
    }

    @Test
    fun `error result with zero exit remains an error`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        run.reportError("request failed")
        run.complete(0)
        assertEquals(listOf("error:request failed"), listener.events)
    }

    @Test
    fun `normal completion and late stop do not become cancellation`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        run.complete(0)
        run.stop()
        assertFalse(run.wasStopped)
        assertEquals(listOf("completed:0"), listener.events)
    }

    @Test
    fun `already terminated process wins over late stop even before exit callback`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        run.attachProcess({ fail<Unit>("must not destroy an exited process") }, { true })
        run.stop()
        run.complete(137)
        assertFalse(run.wasStopped)
        assertEquals(listOf("completed:137"), listener.events)
    }

    @Test
    fun `stop during preparation finishes immediately and destroys a late created process`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        run.stop()
        assertFalse(run.isActive)
        assertEquals(listOf("stopped"), listener.events)
        var destroyed = false
        run.attachProcess({ destroyed = true }, { false })
        run.complete(137)
        assertTrue(destroyed)
        assertEquals(listOf("stopped"), listener.events)
    }

    @Test
    fun `a stopped request cannot contaminate the next request`() {
        val first = RecordingListener()
        val second = RecordingListener()
        val old = AgentRun(first)
        val next = AgentRun(second)
        old.attachProcess({}, { false })
        old.stop()
        next.emit { it.onAssistantDelta("next") }
        old.emit { it.onAssistantDelta("stale") }
        old.complete(137, "stale error")
        next.complete(0)
        assertEquals(listOf("stopped"), first.events)
        assertEquals(listOf("next", "completed:0"), second.events)
    }

    @Test
    fun `stop records intent before synchronous destroy callback`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        run.attachProcess({ run.complete(137, "killed") }, { false })
        run.stop()
        assertEquals(listOf("stopped"), listener.events)
    }

    @Test
    fun `stop racing process construction cannot leave a process alive`() {
        val listener = RecordingListener()
        val run = AgentRun(listener)
        val constructing = CountDownLatch(1)
        val resume = CountDownLatch(1)
        var destroyed = false
        val worker = thread {
            constructing.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
            run.attachProcess({ destroyed = true }, { false })
            run.complete(137)
        }
        assertTrue(constructing.await(5, TimeUnit.SECONDS))
        run.stop()
        resume.countDown()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(destroyed)
        assertEquals(listOf("stopped"), listener.events)
    }
}
