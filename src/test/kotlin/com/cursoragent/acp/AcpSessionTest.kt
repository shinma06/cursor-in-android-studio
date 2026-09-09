package com.cursoragent.acp

import com.cursoragent.service.*
import com.cursoragent.settings.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AcpSessionTest {
    @TempDir lateinit var temp: Path

    private class Harness(val root: Path, scenario: String) : AutoCloseable {
        val gate = WorkspaceOperationGate()
        val processes = CopyOnWriteArrayList<Process>()
        val events = CopyOnWriteArrayList<AgentEvent>()
        val outcomes = CopyOnWriteArrayList<String>()
        val received = CountDownLatch(1)
        val session: AcpSession
        var worker: Thread? = null
        lateinit var run: AgentRun

        init {
            Files.createDirectories(root)
            val server = root.resolve("fake.py")
            Files.writeString(server, AcpSessionTest::class.java.getResource("/acp/fake_agent.py")!!.readText())
            session = AcpSession({ _, _ -> ProcessBuilder("python3", server.toString(), scenario, root.toString())
                .directory(root.toFile()).start().also(processes::add) }, gate::markUncertain, 2)
        }

        fun send(model: String = "", mode: AgentMode = AgentMode.AGENT) {
            val preparation = gate.tryPrepare()!!
            run = AgentRun(object : AgentProcessListener {
                override fun onStructuredEvent(event: AgentEvent) {
                    events += event
                    if (event !is AgentEvent.Configuration) received.countDown()
                }
                override fun onCompleted(exitCode: Int) { outcomes += "completed:$exitCode" }
                override fun onTurnOutcome(outcome: AgentTurnOutcome) {
                    if (outcome == AgentTurnOutcome.COMPLETED) onCompleted(0) else outcomes += "outcome:$outcome"
                }
                override fun onStopped() { outcomes += "stopped" }
                override fun onError(message: String) { outcomes += "error:$message" }
                override fun onUncertain(message: String) { outcomes += "uncertain" }
            })
            val turn = PreparedAgentTurn(run, TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, null), preparation,
                TurnSettings("synthetic", model, mode, PermissionMode.ASK_EVERY_TIME, SandboxMode.DEFAULT))
            worker = thread { preparation.use { session.send("synthetic prompt", turn) } }
        }

        fun finish() {
            worker!!.join(8000)
            assertFalse(worker!!.isAlive, "turn must finish")
        }

        fun wire() = Files.readAllLines(root.resolve("wire.jsonl")).map { com.google.gson.JsonParser.parseString(it).asJsonObject }

        override fun close() {
            session.close()
            processes.forEach {
                if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
            worker?.join(8000)
        }
    }

    @Test
    fun `two turns reuse session and preserve repeated text and confirmed settings while idle allows restore`() {
        Harness(temp, "normal").use { h ->
            h.send(mode = AgentMode.ASK)
            h.finish()
            assertEquals(listOf("はい", "はい"), h.events.filterIsInstance<AgentEvent.Text>().map { it.text })
            assertEquals("ask", h.events.filterIsInstance<AgentEvent.Configuration>().last().mode)
            h.gate.tryRestore()!!.close()
            assertTrue(h.processes.single().isAlive)
            h.send(model = "small")
            h.finish()
            assertEquals(1, h.processes.size)
            assertEquals(1, h.wire().count { it.string("method") == "session/new" })
            assertEquals(2, h.wire().count { it.string("method") == "session/prompt" })
            assertEquals("small", h.events.filterIsInstance<AgentEvent.Configuration>().last().model)
            assertEquals(listOf("completed:0", "completed:0"), h.outcomes)
        }
    }

    @Test
    fun `cancel response cannot release restore until observed child exits and another tab stays usable`() {
        Harness(temp.resolve("a"), "child").use { a ->
            Harness(temp.resolve("b"), "normal").use { b ->
                a.send()
                assertTrue(a.received.await(5, TimeUnit.SECONDS))
                awaitFile(a.root.resolve("child-ready"))
                // Synchronize on a sampled child, not elapsed time: the child must be tracked before cancel.
                val tree = AcpProcessTree(a.processes.single())
                assertFalse(tree.isQuiet())
                a.run.stop()
                awaitFile(a.root.resolve("cancel-response"))
                assertNull(a.gate.tryRestore())
                assertTrue(a.worker!!.isAlive)
                b.send()
                b.finish()
                assertEquals(listOf("completed:0"), b.outcomes)
                Files.createFile(a.root.resolve("release-child"))
                a.finish()
                assertEquals(listOf("stopped"), a.outcomes)
                assertFalse(a.gate.isUncertain)
                a.gate.tryRestore()!!.close()
            }
        }
    }

    @Test
    fun `EOF after prompt latches uncertainty rejects same session retry and releases pending once`() {
        Harness(temp, "eof").use { h ->
            h.send()
            h.finish()
            assertEquals(listOf("uncertain"), h.outcomes)
            assertTrue(h.gate.isUncertain)
            assertNull(h.gate.tryRestore())
            h.send()
            h.finish()
            assertTrue(h.outcomes.last().startsWith("error:"))
            assertEquals(1, h.processes.size)
            assertEquals(1, h.wire().count { it.string("method") == "session/prompt" })
        }
    }

    @Test
    fun `unconfirmed config fails before prompt and permission replies once with exact numeric zero`() {
        Harness(temp.resolve("config"), "bad-config").use { h ->
            h.send(mode = AgentMode.PLAN)
            h.finish()
            assertTrue(h.outcomes.single().startsWith("error:"))
            assertEquals(0, h.wire().count { it.string("method") == "session/prompt" })
            assertFalse(h.gate.isUncertain)
        }
        Harness(temp.resolve("permission"), "permission").use { h ->
            h.send()
            assertTrue(h.received.await(5, TimeUnit.SECONDS))
            val request = h.events.filterIsInstance<AgentEvent.Input>().single().request
            assertTrue(request.answer(AgentAnswer.Permission("reject")))
            assertFalse(request.answer(AgentAnswer.Permission("allow")))
            h.finish()
            val reply = h.wire().single { !it.has("method") }
            assertEquals("0", reply["id"].toString())
            assertEquals("reject", reply.getAsJsonObject("result").getAsJsonObject("outcome").string("optionId"))
        }
    }

    @Test
    fun `unsupported request returns error and stop resolves outstanding permission`() {
        Harness(temp.resolve("unknown"), "unknown").use { h ->
            h.send()
            h.finish()
            assertEquals(-32601, h.wire().single { !it.has("method") }.getAsJsonObject("error")["code"].asInt)
        }
        Harness(temp.resolve("stop"), "permission").use { h ->
            h.send()
            assertTrue(h.received.await(5, TimeUnit.SECONDS))
            val request = h.events.filterIsInstance<AgentEvent.Input>().single().request
            h.run.stop()
            h.finish()
            assertFalse(request.isPending)
            assertFalse(request.answer(AgentAnswer.Permission("allow")))
            val reply = h.wire().single { !it.has("method") }
            assertEquals("cancelled", reply.getAsJsonObject("result").getAsJsonObject("outcome").string("outcome"))
        }
    }

    @Test
    fun `persistent child after cancellation latches uncertainty instead of claiming stopped`() {
        Harness(temp, "child").use { h ->
            h.send()
            assertTrue(h.received.await(5, TimeUnit.SECONDS))
            awaitFile(h.root.resolve("child-ready"))
            h.run.stop()
            h.finish()
            assertEquals(listOf("uncertain"), h.outcomes)
            assertTrue(h.gate.isUncertain)
            assertNull(h.gate.tryRestore())
        }
    }

    @Test
    fun `provider refusal limits and cancellation retain their actual terminal outcomes`() {
        for ((reason, expected) in mapOf("refusal" to AgentTurnOutcome.REFUSED, "max_tokens" to AgentTurnOutcome.TOKEN_LIMIT,
            "max_turn_requests" to AgentTurnOutcome.REQUEST_LIMIT, "cancelled" to AgentTurnOutcome.CANCELLED)) {
            Harness(temp.resolve(reason), reason).use { h ->
                h.send()
                h.finish()
                assertEquals(listOf("outcome:$expected"), h.outcomes)
                assertFalse(h.gate.isUncertain)
            }
        }
    }

    @Test
    fun `noninteger and string protocol versions fail before creating session`() {
        for (scenario in listOf("version-fraction", "version-string")) {
            Harness(temp.resolve(scenario), scenario).use { h ->
                h.send()
                h.finish()
                assertTrue(h.outcomes.single().startsWith("error:"))
                assertEquals(listOf("initialize"), h.wire().map { it.string("method") })
                assertFalse(h.gate.isUncertain)
            }
        }
    }

    private fun awaitFile(path: Path) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(Files.exists(path))
    }
}
