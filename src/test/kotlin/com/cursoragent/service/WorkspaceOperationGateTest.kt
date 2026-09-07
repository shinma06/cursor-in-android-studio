package com.cursoragent.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class WorkspaceOperationGateTest {
    @Test
    fun `stopped preparation continues blocking until background work exits`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        val run = AgentRun(object : AgentProcessListener {})
        run.stop()
        assertFalse(run.isActive)
        assertNull(gate.tryRestore())
        assertNull(gate.tryPrepare())
        preparation.close()
        gate.tryRestore()!!.close()
    }

    @Test
    fun `stop while constructing a process blocks restoration through actual exit`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        val run = AgentRun(object : AgentProcessListener {})
        val launching = CountDownLatch(1)
        val resumeConstruction = CountDownLatch(1)
        val waitingForExit = CountDownLatch(1)
        val exit = CountDownLatch(1)
        val destroys = AtomicInteger()
        val worker = thread {
            val process = preparation.launchingProcess()
            launching.countDown()
            check(resumeConstruction.await(5, TimeUnit.SECONDS))
            run.attachProcess({ destroys.incrementAndGet() }, { false })
            preparation.close()
            waitingForExit.countDown()
            check(exit.await(5, TimeUnit.SECONDS))
            process.close()
            run.complete(137)
        }
        try {
            assertTrue(launching.await(5, TimeUnit.SECONDS))
            run.stop() // This may emit onStopped before the process even exists.
            assertFalse(run.isActive)
            assertNull(gate.tryRestore())
            resumeConstruction.countDown()
            assertTrue(waitingForExit.await(5, TimeUnit.SECONDS))
            assertEquals(1, destroys.get())
            assertNull(gate.tryRestore())
            assertNull(gate.tryPrepare()) // New chat cannot hide an old process that is still exiting.
        } finally {
            resumeConstruction.countDown()
            exit.countDown()
            worker.join(5000)
        }
        assertFalse(worker.isAlive)
        gate.tryRestore()!!.close()
    }

    @Test
    fun `process exiting synchronously does not unlock remaining preparation`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        preparation.launchingProcess().close()
        assertNull(gate.tryRestore())
        preparation.close()
        gate.tryRestore()!!.close()
    }

    @Test
    fun `failed process construction and failed restore release reservations`() {
        val gate = WorkspaceOperationGate()
        gate.tryPrepare()!!.use { preparation ->
            assertThrows(IllegalStateException::class.java) {
                preparation.launchingProcess().use { error("constructor failed") }
            }
            assertNull(gate.tryRestore())
        }
        assertThrows(IllegalStateException::class.java) {
            gate.tryRestore()!!.use { error("restore failed") }
        }
        gate.tryPrepare()!!.close()
    }

    @Test
    fun `confirmation does not reserve and rechecks when the user accepts`() {
        val gate = WorkspaceOperationGate()
        // A confirmation dialog is open; a different controller starts a turn.
        val turn = gate.tryPrepare()!!
        assertNull(gate.tryRestore()) // The confirmation callback must check again here.
        turn.close()
        val restore = gate.tryRestore()!!
        assertNull(gate.tryPrepare())
        assertNull(gate.tryRestore())
        restore.close()
        gate.tryPrepare()!!.close()
    }

    @Test
    fun `late duplicate cleanup cannot unlock a newer operation`() {
        val gate = WorkspaceOperationGate()
        val old = gate.tryPrepare()!!
        val oldProcess = old.launchingProcess()
        old.close()
        oldProcess.close()
        val next = gate.tryPrepare()!!
        old.close()
        oldProcess.close()
        assertNull(gate.tryRestore())
        next.close()
        val restore = gate.tryRestore()!!
        restore.close()
        val afterRestore = gate.tryPrepare()!!
        restore.close()
        assertNull(gate.tryRestore())
        afterRestore.close()
    }

    @Test
    fun `closing preparation permanently rejects a later launch`() {
        val gate = WorkspaceOperationGate()
        val preparation = gate.tryPrepare()!!
        preparation.close()
        assertThrows(IllegalStateException::class.java) { preparation.launchingProcess() }
        assertNotNull(gate.tryRestore())
    }
}
