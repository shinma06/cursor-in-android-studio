package com.cursoragent.service

import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Checks tab/run routing and restore exclusion with simulated processes; real IDE/CLI QA remains separate. */
class SessionTabsExecutionTest {
    @Test
    fun `send arguments remain a snapshot after changing another tab and application defaults`() {
        val defaults = com.cursoragent.settings.AgentSettingsState()
        val selected = com.cursoragent.settings.AgentSettingsState().apply {
            mode = AgentMode.ASK
            selectedModel = "model-a"
        }
        val turn = TurnSettings(defaults.agentExecutablePath, selected.selectedModel, selected.mode,
            defaults.permissionMode, defaults.sandboxMode)
        selected.mode = AgentMode.PLAN
        selected.selectedModel = "model-b"
        defaults.permissionMode = com.cursoragent.settings.PermissionMode.RUN_EVERYTHING
        defaults.sandboxMode = com.cursoragent.settings.SandboxMode.DISABLED
        assertEquals(listOf("--model", "model-a", "--mode", "ask"), turn.arguments())
    }

    @Test
    fun `switch and close route output only to the original live tab while physical exits hold restore`() {
        val tabs = SessionTabs()
        val gate = WorkspaceOperationGate()
        val events = mutableMapOf<String, MutableList<String>>()
        fun start(id: String): Triple<AgentRun, WorkspaceOperationGate.Preparation, AutoCloseable> {
            tabs.updateComposer(id, AgentMode.AGENT, "model-$id", "prompt", 3)
            val turn = tabs.beginTurn(id)!!
            val preparation = gate.tryPrepare()!!
            val process = preparation.launchingProcess()
            val run = AgentRun(object : AgentProcessListener {
                override fun onAssistantText(text: String) {
                    if (tabs.accepts(turn.token)) events.getOrPut(id) { mutableListOf() }.add(text)
                }
                override fun onCompleted(exitCode: Int) { tabs.finishTurn(turn.token) }
            })
            run.attachProcess({}, { false })
            return Triple(run, preparation, process)
        }
        val a = tabs.snapshot().selectedId
        val (runA, prepA, processA) = start(a)
        val b = tabs.open().id
        val (runB, prepB, processB) = start(b)
        runA.emit { it.onAssistantText("A while B selected") }
        tabs.select(a)
        runB.emit { it.onAssistantText("B while A selected") }
        tabs.close(a)
        runA.detachListener()
        runA.stop()
        runA.emit { it.onAssistantText("late A") }
        prepA.close()
        prepB.close()
        runB.complete(0)
        processB.close()
        assertNull(gate.tryRestore())
        assertEquals(listOf("A while B selected"), events[a])
        assertEquals(listOf("B while A selected"), events[b])
        processA.close()
        runA.complete(137)
        gate.tryRestore()!!.close()
    }

    @Test
    fun `two resumed tabs preserve their own root and mode and conflicts never regain trust`(@TempDir root: Path) {
        val history = SessionWorkspaceHistory()
        val defaultTarget = RestoreTarget.capture(root.toString(), WorktreeMode.DEFAULT)
        val isolatedTarget = RestoreTarget.capture(root.toString(), WorktreeMode.ISOLATED)
        history.record("A", defaultTarget)
        history.record("B", isolatedTarget)
        val a = TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, "A", history.find("A"))
        val b = TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, "B", history.find("B"))
        assertEquals(defaultTarget, a.restoreTarget)
        assertEquals(RestoreTarget.UNKNOWN, b.restoreTarget)
        assertEquals(listOf("--resume", "A"), a.arguments().takeLast(2))
        assertEquals(listOf("--resume", "B"), b.arguments().takeLast(2))
        history.record("A", isolatedTarget)
        history.record("A", defaultTarget)
        assertEquals(RestoreTarget.UNKNOWN, history.find("A"))
        assertEquals(isolatedTarget, history.find("B"))
    }

    @Test
    fun `closed preparation detaches listener but still destroys a late process and cleans up once`() {
        var callbacks = 0
        var cleanup = 0
        var destroy = 0
        val run = AgentRun(object : AgentProcessListener {
            override fun onStopped() { callbacks++ }
        }) { cleanup++ }
        run.detachListener()
        run.stop()
        run.attachProcess({ destroy++ }, { false })
        run.complete(137)
        assertEquals(0, callbacks)
        assertEquals(1, cleanup)
        assertEquals(1, destroy)
    }
}
