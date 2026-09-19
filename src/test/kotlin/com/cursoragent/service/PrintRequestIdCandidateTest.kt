package com.cursoragent.service

import com.cursoragent.parser.StreamEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrintRequestIdCandidateTest {
    private val result = StreamEvent.Result("session", "model", "body", false, requestId = "Opaque-ID", subtype = "success")

    @Test fun `only a successful terminal Result and actual zero exit produce a candidate`() {
        val pending = PrintRequestIdCandidate(null)
        assertNull(pending.completed(0))
        pending.session("session")
        pending.accept(result)
        assertNull(pending.completed(1))
        assertEquals("Opaque-ID", pending.completed(0)?.value)
        assertEquals("session", pending.completed(0)?.sessionId)
        for (bad in listOf(result.copy(requestId = null), result.copy(isError = true), result.copy(subtype = null), result.copy(subtype = "error"))) {
            val candidate = PrintRequestIdCandidate("session")
            candidate.accept(bad)
            assertNull(candidate.completed(0))
        }
    }

    @Test fun `exact retries are accepted but conflicts permanently poison that turn only`() {
        val repeated = PrintRequestIdCandidate("session")
        repeated.accept(result)
        repeated.accept(result.copy())
        assertEquals("Opaque-ID", repeated.completed(0)?.value)
        for (conflict in listOf(result.copy(requestId = "other"), result.copy(requestId = null), result.copy(result = "different body"), result.copy(sessionId = "other"), result.copy(isError = true))) {
            val candidate = PrintRequestIdCandidate("session")
            candidate.accept(result)
            candidate.accept(conflict)
            candidate.accept(result)
            assertNull(candidate.completed(0))
        }
        val resumed = PrintRequestIdCandidate("session")
        resumed.accept(result.copy(requestId = "next"))
        assertEquals("next", resumed.completed(0)?.value)
    }

    @Test fun `session contradictions before or after result suppress diagnostics without changing result`() {
        for (later in listOf(false, true)) {
            val candidate = PrintRequestIdCandidate("other")
            if (!later) candidate.session("session")
            candidate.accept(result)
            if (later) candidate.session("session")
            assertNull(candidate.completed(0))
            assertEquals("body", result.result)
        }
    }

    @Test fun `AgentRun delivers confirmed metadata only on one nonstopped nonerror physical completion`() {
        for (ending in listOf("success", "stop", "error", "abnormal", "uncertain", "detached")) {
            val events = mutableListOf<String>()
            val listener = object : AgentProcessListener {
                override fun onPrintCompleted(requestId: PrintRequestId) { events += requestId.value }
                override fun onCompleted(exitCode: Int) { events += "exit:$exitCode" }
                override fun onStopped() { events += "stopped" }
                override fun onError(message: String) { events += "error" }
            }
            val run = AgentRun(listener)
            run.attachProcess({}, { false })
            val candidate = PrintRequestIdCandidate("session").apply { accept(result) }
            assertTrue(events.isEmpty(), "Result alone cannot complete")
            when (ending) {
                "stop" -> run.stop()
                "error" -> run.reportError("failed")
                "uncertain" -> run.completeUncertain("unknown")
                "detached" -> run.detachListener()
            }
            run.complete(if (ending == "abnormal") 1 else 0, printRequestId = candidate.completed(0))
            run.complete(0, printRequestId = candidate.completed(0))
            assertEquals(when (ending) {
                "success" -> listOf("Opaque-ID")
                "stop" -> listOf("stopped")
                "abnormal" -> listOf("exit:1")
                "detached" -> emptyList()
                else -> listOf("error")
            }, events, ending)
        }
    }
}
