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
        val commands = CopyOnWriteArrayList<CommandCatalog>()
        val received = CountDownLatch(1)
        val session: AcpSession
        var worker: Thread? = null
        lateinit var run: AgentRun
        lateinit var lastTurn: PreparedAgentTurn
        val bindings = CopyOnWriteArrayList<String?>()

        init {
            Files.createDirectories(root)
            val server = root.resolve("fake.py")
            Files.writeString(server, AcpSessionTest::class.java.getResource("/acp/fake_agent.py")!!.readText())
            session = AcpSession({ _, _ -> ProcessBuilder("python3", server.toString(), scenario, root.toString())
                .directory(root.toFile()).start().also(processes::add) }, gate::markUncertain, 2)
            session.observeCommands { commands.add(it) }
        }

        fun send(model: String = "", mode: AgentMode = AgentMode.AGENT, prompt: String = "synthetic prompt", commandText: String? = null) {
            val preparation = gate.tryPrepare()!!
            run = AgentRun(object : AgentProcessListener {
                override fun onStructuredEvent(event: AgentEvent) {
                    events += event
                    if (event !is AgentEvent.Configuration) received.countDown()
                }
                override fun onSessionUpdated(chatId: String?, model: String?) { bindings.add(chatId) }
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
            lastTurn = turn
            worker = thread { preparation.use { session.send(prompt, turn, commandText, commandText?.substringBefore(' ')?.removePrefix("/")) } }
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
    fun `Task reopened before parent end_turn cannot bypass unfinished tool and restore guards`() {
        Harness(temp, "task-reopened").use { h ->
            h.send()
            h.finish()
            assertEquals(listOf("uncertain"), h.outcomes)
            assertNull(h.gate.tryRestore())
            assertEquals("in_progress", h.events.filterIsInstance<AgentEvent.Tool>().last().state.status)
        }
    }

    @Test
    fun `late standard Task activity still marks the workspace uncertain and rejects restore`() {
        Harness(temp, "task-late-standard").use { h ->
            h.send()
            h.finish()
            assertEquals(listOf("uncertain"), h.outcomes)
            assertNull(h.gate.tryRestore())
            assertEquals("completed", h.events.filterIsInstance<AgentEvent.Tool>().last().state.status)
        }
    }

    @Test
    fun `task request metadata keeps unsupported reply once and cannot change failed status`() {
        for (scenario in listOf("task-request", "task-failed")) Harness(temp.resolve(scenario), scenario).use { h ->
            h.send()
            h.finish()
            val tools = h.events.filterIsInstance<AgentEvent.Tool>().map { it.state }
            assertEquals(4, tools.size)
            assertEquals("reported-child", tools.last().task!!.reportedAgentId)
            assertEquals(if (scenario == "task-failed") "failed" else "completed", tools.last().status)
            assertEquals(1, tools.map { it.id }.distinct().size)
            val responses = h.wire().filter { !it.has("method") && it["id"]?.asInt == 7001 }
            assertEquals(1, responses.size)
            assertEquals(-32601, responses.single().getAsJsonObject("error")["code"].asInt)
            assertFalse(responses.single().has("result"))
            assertEquals(listOf("completed:0"), h.outcomes)
        }
    }

    @Test
    fun `task notifications have no reply ignore foreign unknown and terminal metadata and reset on next turn`() {
        Harness(temp, "task-notify").use { h ->
            repeat(2) {
                h.send()
                h.finish()
            }
            val tools = h.events.filterIsInstance<AgentEvent.Tool>().map { it.state }
            assertEquals(8, tools.size)
            assertEquals(2, tools.count { it.status == "pending" && it.task!!.reportedAgentId == null })
            assertTrue(tools.mapNotNull { it.task?.reportedAgentId }.all { it == "reported-child" })
            assertTrue(h.wire().none { !it.has("method") })
            assertEquals(listOf("completed:0", "completed:0"), h.outcomes)
        }
    }

    @Test
    fun `task metadata after Stop is not delivered and cancellation keeps actual parent outcome`() {
        Harness(temp, "task-stop").use { h ->
            h.send()
            assertTrue(h.received.await(5, TimeUnit.SECONDS))
            h.run.stop()
            h.finish()
            assertTrue(h.events.filterIsInstance<AgentEvent.Tool>().all { it.state.task?.reportedAgentId == null })
            assertEquals(listOf("stopped"), h.outcomes)
            h.gate.tryRestore()!!.close()
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

    @Test
    fun `input preparation advertises without prompt and later sends exact command separately from context`() {
        Harness(temp, "commands").use { h ->
            h.session.prepare(temp.toRealPath().toString(), "synthetic")
            awaitCondition { h.commands.any { it.containsCommand("Mixed-日本語") } }
            assertEquals(listOf("initialize", "session/new"), h.wire().map { it.string("method") })
            h.gate.tryRestore()!!.close()
            val exact = commandPrompt("Mixed-日本語", " 東京  alpha beta\n ")
            h.send(model = "small", mode = AgentMode.ASK, prompt = "Explicit context: synthetic", commandText = exact)
            h.finish()
            val blocks = h.wire().single { it.string("method") == "session/prompt" }.getAsJsonObject("params").getAsJsonArray("prompt")
            assertEquals(listOf(exact, "Explicit context: synthetic"), blocks.map { it.asJsonObject.string("text") })
            assertEquals("small", h.events.filterIsInstance<AgentEvent.Configuration>().last().model)
            h.send()
            h.finish()
            assertEquals(1, h.processes.size)
            val next = h.wire().last { it.string("method") == "session/prompt" }.getAsJsonObject("params").getAsJsonArray("prompt")
            assertEquals(listOf("synthetic prompt"), next.map { it.asJsonObject.string("text") })
        }
    }

    @Test
    fun `idle command updates replace empty invalid and recovered catalogs and reject another session`() {
        Harness(temp.resolve("a"), "commands").use { a ->
            Harness(temp.resolve("b"), "commands").use { b ->
                a.session.prepare(a.root.toRealPath().toString(), "synthetic")
                b.session.prepare(b.root.toRealPath().toString(), "synthetic")
                awaitCondition { a.commands.any { it.containsCommand("Mixed-日本語") } && b.commands.any { it.containsCommand("Mixed-日本語") } }
                Files.createFile(a.root.resolve("replace-commands"))
                awaitCondition { a.commands.last().containsCommand("replacement") }
                assertTrue(a.commands.contains(CommandCatalog.Ready(emptyList())))
                assertTrue(a.commands.contains(CommandCatalog.Invalid))
                assertFalse(a.commands.any { it.containsCommand("foreign") })
                assertFalse(a.commands.last().containsCommand("Mixed-日本語"))
                assertTrue(b.commands.last().containsCommand("Mixed-日本語"))
                a.session.close()
                val count = a.commands.size
                a.session.prepare(a.root.toRealPath().toString(), "synthetic")
                assertEquals(count, a.commands.size)
                assertEquals(1, a.processes.size)
            }
        }
    }

    @Test
    fun `close before preparation cannot launch and failed metadata connection sends no prompt`() {
        Harness(temp.resolve("closed"), "normal").use { h ->
            h.session.close()
            h.session.prepare(h.root.toRealPath().toString(), "synthetic")
            assertTrue(h.processes.isEmpty())
        }
        Harness(temp.resolve("failure"), "version-string").use { h ->
            h.session.prepare(h.root.toRealPath().toString(), "synthetic")
            assertEquals(CommandCatalog.Failed, h.commands.last())
            assertEquals(listOf("initialize"), h.wire().map { it.string("method") })
            assertFalse(h.gate.isUncertain)
        }
    }

    @Test
    fun `a concurrent first send waits for metadata handshake without creating a second session`() {
        Harness(temp, "commands-delayed").use { h ->
            val preparing = thread { h.session.prepare(h.root.toRealPath().toString(), "synthetic") }
            awaitCondition { Files.exists(h.root.resolve("wire.jsonl")) && h.wire().any { it.string("method") == "session/new" } }
            h.send()
            Files.createFile(h.root.resolve("release-new"))
            h.finish()
            preparing.join(5000)
            assertFalse(preparing.isAlive)
            assertEquals(1, h.wire().count { it.string("method") == "initialize" })
            assertEquals(1, h.wire().count { it.string("method") == "session/new" })
            assertEquals(1, h.wire().count { it.string("method") == "session/prompt" })
            assertEquals(listOf("completed:0"), h.outcomes)
        }
    }

    @Test
    fun `close during metadata handshake suppresses late catalog and terminates preparation`() {
        Harness(temp, "commands-delayed").use { h ->
            val preparing = thread { h.session.prepare(h.root.toRealPath().toString(), "synthetic") }
            awaitCondition { Files.exists(h.root.resolve("wire.jsonl")) && h.wire().any { it.string("method") == "session/new" } }
            h.session.close()
            val count = h.commands.size
            preparing.join(5000)
            assertFalse(preparing.isAlive)
            assertEquals(count, h.commands.size)
            assertFalse(h.wire().any { it.string("method") == "session/prompt" })
        }
    }

    @Test
    fun `removed command is rejected at wire boundary without submitting or marking a prompt dispatched`() {
        Harness(temp, "commands").use { h ->
            h.session.prepare(h.root.toRealPath().toString(), "synthetic")
            awaitCondition { h.commands.last().containsCommand("Mixed-日本語") }
            Files.createFile(h.root.resolve("replace-commands"))
            awaitCondition { h.commands.last().containsCommand("replacement") }
            h.send(commandText = "/Mixed-日本語 東京")
            h.finish()
            assertFalse(h.wire().any { it.string("method") == "session/prompt" })
            assertTrue(h.outcomes.single().startsWith("error:"))
            assertFalse(h.gate.isUncertain)
        }
    }

    @Test
    fun `oversized command context is unsent with no chat binding or workspace uncertainty`() {
        Harness(temp, "commands").use { h ->
            h.session.prepare(h.root.toRealPath().toString(), "synthetic")
            awaitCondition { h.commands.last().containsCommand("Mixed-日本語") }
            h.send(prompt = "x".repeat(AcpJsonRpc.MAX_FRAME_BYTES), commandText = "/Mixed-日本語 東京")
            h.finish()
            assertFalse(h.wire().any { it.string("method") == "session/prompt" })
            assertFalse(h.lastTurn.promptDispatched)
            assertTrue(h.bindings.isEmpty())
            assertFalse(h.gate.isUncertain)
            assertTrue(h.outcomes.single().startsWith("error:"))
        }
    }

    @Test
    fun `nontext siblings survive malformed input while foreign terminal and stopped content are gated`() {
        Harness(temp.resolve("normal"), "content-normal").use { h ->
            h.send()
            h.finish()
            assertEquals(listOf("completed:0"), h.outcomes)
            assertEquals(listOf("before", "after"), h.events.filterIsInstance<AgentEvent.Text>().map { it.text })
            val media = h.events.filterIsInstance<AgentEvent.Content>().single().summary
            assertTrue(media.details.contains("image/png"))
            assertEquals(2, h.events.filterIsInstance<AgentEvent.Tool>().single().state.content.size)
            h.gate.tryRestore()!!.close()
        }
        Harness(temp.resolve("stop"), "content-stop").use { h ->
            h.send()
            assertTrue(h.received.await(5, TimeUnit.SECONDS))
            h.run.stop()
            h.finish()
            assertEquals(listOf("stopped"), h.outcomes)
            assertTrue(h.events.filterIsInstance<AgentEvent.Content>().none { it.summary.details.contains("after-stop") })
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }

    private fun awaitFile(path: Path) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(Files.exists(path))
    }
}
