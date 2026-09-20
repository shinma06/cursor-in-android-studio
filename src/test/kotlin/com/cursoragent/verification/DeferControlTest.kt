package com.cursoragent.verification

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.service.WorkspaceOperationGate
import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.PromptQueue
import com.cursoragent.ui.updateCurrentTurnOnEdt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DeferControlTest {
    @TempDir lateinit var directory: Path
    private val gson = Gson()
    private val run = UUID.randomUUID().toString()
    private val owner = "${UUID.randomUUID()}/${UUID.randomUUID()}"

    private fun control(timeout: Long = 5_000): DeferControl {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        Files.writeString(directory.resolve("manifest.json"), gson.toJson(mapOf("run" to run)))
        return DeferControl(directory, timeout)
    }

    private fun command(control: DeferControl, op: String, vararg fields: Pair<String, String>) {
        Files.writeString(directory.resolve("command.json"), gson.toJson(mapOf("run" to run,
            "id" to UUID.randomUUID().toString(), "op" to op) + fields))
        control.poll()
    }

    private fun arm(control: DeferControl, point: String, token: String = "next") =
        command(control, "arm", "point" to point, "owner" to owner, "token" to token)

    private fun state(): JsonObject = gson.fromJson(Files.readString(directory.resolve("state.json")), JsonObject::class.java)

    private fun pending(): String {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            if (Files.exists(directory.resolve("state.json"))) state().get("pending")?.let { return it.asString }
            Thread.sleep(1)
        }
        error("Target callback did not reach the gate")
    }

    @Test
    fun `queue release rechecks real ticket revision owner and generation once`() {
        control().use { control ->
            val queue = PromptQueue("conversation")
            queue.add("captured prompt", AgentMode.AGENT, "model")
            val ticket = queue.ticket(1)!!
            arm(control, "queue")
            var started = 0
            var dispatched: Boolean? = null
            val id = control.hold("queue", owner, "1:0:item", {
                SwingUtilities.invokeLater { dispatched = queue.dispatch(ticket, 1, true) { started++; true } }
            }, {})!!
            assertNull(control.hold("queue", "$owner-other", "other", { fail("wrong owner") }, {}))
            queue.pause()
            command(control, "release", "pending" to id)
            SwingUtilities.invokeAndWait {}
            assertEquals(false, dispatched)
            assertEquals(0, started)
            assertEquals(1, queue.size)
            command(control, "release", "pending" to id)
            assertEquals("rejected", state().get("result").asString)
            assertFalse(state().has("pending"))
        }
    }

    @Test
    fun `old chunks are rejected while allowed Stop terminal bypasses the held callback`() {
        control().use { control ->
            arm(control, "edt-chunk", "turn-1")
            var current = true
            var stopped = false
            val deliveries = mutableListOf<String>()
            fun callback(terminal: Boolean) = {
                updateCurrentTurnOnEdt({ false }, { current }, { stopped }, allowStopped = terminal) {
                    assertTrue(SwingUtilities.isEventDispatchThread())
                    deliveries.add(if (terminal) "terminal" else "chunk")
                }
            }
            val id = control.hold("edt-chunk", owner, "turn-1", { SwingUtilities.invokeLater(callback(false)) }, {})!!
            stopped = true
            SwingUtilities.invokeAndWait(callback(true))
            assertEquals(listOf("terminal"), deliveries)
            current = false
            command(control, "release", "pending" to id)
            SwingUtilities.invokeAndWait {}
            assertEquals(listOf("terminal"), deliveries)
            // An obsolete terminal is rejected by the same existing ownership guard.
            SwingUtilities.invokeAndWait(callback(true))
            assertEquals(listOf("terminal"), deliveries)
        }
    }

    @Test
    fun `stopped preparation never launches and releases its original reservation on original worker`() {
        control().use { control ->
            arm(control, "preparation")
            val gate = WorkspaceOperationGate()
            val reservation = gate.tryPrepare()!!
            val run = AgentRun(object : AgentProcessListener {})
            val launches = AtomicInteger()
            val task = java.util.concurrent.FutureTask {
                val originalThread = Thread.currentThread()
                try {
                    control.awaitPreparation(owner, "turn")
                    assertSame(originalThread, Thread.currentThread())
                    if (run.isActive) launches.incrementAndGet()
                } finally { reservation.close() }
            }
            val worker = Thread(task).apply { start() }
            val id = pending()
            SwingUtilities.invokeAndWait { run.stop() }
            assertNull(gate.tryRestore())
            command(control, "release", "pending" to id)
            worker.join(5000)
            assertFalse(worker.isAlive)
            task.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(0, launches.get())
            gate.tryRestore()!!.close()
        }
    }

    @Test
    fun `close timeout and interruption abort preparation and reclaim reservation`() {
        for (reason in listOf("close", "timeout", "interruption")) {
            Files.deleteIfExists(directory.resolve("command.json"))
            Files.deleteIfExists(directory.resolve("state.json"))
            control(if (reason == "timeout") 50 else 5000).use { control ->
                arm(control, "preparation")
                val gate = WorkspaceOperationGate()
                val reservation = gate.tryPrepare()!!
                val aborted = AtomicInteger()
                val launches = AtomicInteger()
                val worker = thread {
                    try {
                        control.awaitPreparation(owner, "turn")
                        launches.incrementAndGet()
                    } catch (_: Exception) { aborted.incrementAndGet() }
                    finally { reservation.close() }
                }
                pending()
                when (reason) {
                    "close" -> control.close()
                    "interruption" -> worker.interrupt()
                }
                worker.join(5000)
                assertFalse(worker.isAlive, reason)
                assertEquals(1, aborted.get(), reason)
                assertEquals(0, launches.get(), reason)
                assertFalse(state().has("pending"), reason)
                assertEquals(when (reason) {
                    "close" -> "closed"
                    "interruption" -> "interrupted"
                    else -> "timeout"
                }, state().get("result").asString, reason)
                gate.tryRestore()!!.close()
            }
        }
    }

    @Test
    fun `poll timeout clears published pending without executing the held callback`() {
        control(1).use { control ->
            arm(control, "popup")
            var aborted = 0
            control.hold("popup", owner, "popup", { fail("timed out callback must not run") }, { aborted++ })
            Thread.sleep(5)
            control.poll()
            assertEquals(1, aborted)
            assertFalse(state().has("pending"))
            assertEquals("timeout", state().get("result").asString)
        }
    }

    @Test
    fun `arm targets one owner and next matching token without retaining nonmatching callbacks`() {
        control().use { control ->
            arm(control, "popup", "popup-1/2")
            assertNull(control.hold("popup", owner, "popup-1/1", { fail("old query") }, {}))
            assertNull(control.hold("queue", owner, "popup-1/2", { fail("wrong point") }, {}))
            var aborted = 0
            assertNotNull(control.hold("popup", owner, "popup-1/2", { fail("closed trial must not deliver") }, { aborted++ }))
            command(control, "arm", "point" to "queue", "owner" to owner, "token" to "next")
            assertEquals("rejected", state().get("result").asString)
            control.close()
            assertEquals(1, aborted)
            assertFalse(state().has("pending"))
        }
    }

    @Test
    fun `control refuses symlinks and other run commands`() {
        control().use { control ->
            Files.writeString(directory.resolve("command.json"), gson.toJson(mapOf("run" to UUID.randomUUID().toString(), "op" to "close")))
            assertThrows(IllegalArgumentException::class.java) { control.poll() }
            Files.delete(directory.resolve("command.json"))
            Files.createSymbolicLink(directory.resolve("command.json"), directory.resolve("manifest.json"))
            assertThrows(IllegalArgumentException::class.java) { control.poll() }
        }
    }

    @Test
    fun `disabled verification wrapper returns immediately on the original EDT`() {
        assertNull(System.getProperty("cursor.verification.directory"))
        var delivered = false
        SwingUtilities.invokeAndWait {
            VerificationDefer.edt("queue", owner, "token") { delivered = true }
            assertTrue(delivered)
        }
        assertFalse(Files.exists(directory.resolve("events.jsonl")))
    }
}
