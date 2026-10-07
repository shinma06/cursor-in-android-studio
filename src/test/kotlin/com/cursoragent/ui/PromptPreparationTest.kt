package com.cursoragent.ui

import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.service.WorkspaceOperationGate
import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptPreparationTest {
    @Test
    fun `platform cancellation propagates without failure or provider dispatch and releases preparation`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        val events = mutableListOf<String>()
        val run = AgentRun(object : AgentProcessListener {
            override fun onStopped() {
                assertNull(gate.tryRestore()) // Terminal UI does not release the running worker.
                events += "stopped"
            }

            override fun onError(message: String) { events += "error" }
        })
        val cancellation = ProcessCanceledException()
        var providerStarts = 0
        val flush: () -> Unit = { throw cancellation }

        val thrown = assertThrows(ProcessCanceledException::class.java) {
            runPromptPreparation(run, preparation) {
                assertNull(gate.tryRestore())
                flush()
                providerStarts++
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(0, providerStarts)
        assertTrue(run.wasStopped)
        assertFalse(run.isActive)
        assertEquals(listOf("stopped"), events)
        gate.tryRestore()!!.close()
        run.reportError("late preparation error")
        run.complete(-1)
        assertEquals(listOf("stopped"), events)
    }

    @Test
    fun `a failing cancellation listener does not replace the original platform exception`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        val cancellation = ProcessCanceledException()
        val run = AgentRun(object : AgentProcessListener {
            override fun onStopped() { error("listener cleanup failed") }
        })

        assertSame(cancellation, assertThrows(ProcessCanceledException::class.java) {
            runPromptPreparation(run, preparation) { throw cancellation }
        })

        assertFalse(run.isActive)
        gate.tryRestore()!!.close()
    }

    @Test
    fun `ordinary preparation failure remains an error and releases preparation`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        val events = mutableListOf<String>()
        val run = AgentRun(object : AgentProcessListener {
            override fun onStopped() { events += "stopped" }
            override fun onError(message: String) { events += message }
        })

        runPromptPreparation(run, preparation) { error("context unavailable") }

        assertFalse(run.wasStopped)
        assertFalse(run.isActive)
        assertEquals(listOf("送信の準備に失敗しました: context unavailable"), events)
        gate.tryRestore()!!.close()
    }

    @Test
    fun `late platform cancellation after Stop still propagates without a second terminal`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        var stopped = 0
        val run = AgentRun(object : AgentProcessListener {
            override fun onStopped() { stopped++ }
        })
        run.stop()
        val cancellation = ProcessCanceledException()

        assertSame(cancellation, assertThrows(ProcessCanceledException::class.java) {
            runPromptPreparation(run, preparation) { throw cancellation }
        })

        assertEquals(1, stopped)
        gate.tryRestore()!!.close()
    }

    @Test
    fun `platform cancellation preserves a process reservation until physical exit`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        val process = preparation.launchingProcess()
        var stopped = 0
        var destroyed = 0
        val run = AgentRun(object : AgentProcessListener {
            override fun onStopped() { stopped++ }
        })
        run.attachProcess({ destroyed++ }, { false })
        val cancellation = ProcessCanceledException()

        try {
            assertSame(cancellation, assertThrows(ProcessCanceledException::class.java) {
                runPromptPreparation(run, preparation) { throw cancellation }
            })
            assertEquals(1, destroyed)
            assertEquals(0, stopped)
            assertNull(gate.tryRestore())
        } finally {
            process.close()
            run.complete(137)
        }

        assertEquals(1, stopped)
        gate.tryRestore()!!.close()
    }
}
