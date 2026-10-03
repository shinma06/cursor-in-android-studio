package com.cursoragent.service

import com.cursoragent.parser.StreamEvent

/** Deliberately no data-class toString: diagnostic values must not enter ordinary logs. */
class PrintRequestId(val value: String, val sessionId: String?)

/** One print process only. Result is provisional; physical exit and AgentRun decide completion. */
internal class PrintRequestIdCandidate(resumeId: String?) {
    private var sessionId = resumeId
    private var result: StreamEvent.Result? = null
    private var conflicting = false

    fun session(id: String?) {
        if (id == null) return
        if (sessionId != null && sessionId != id) conflicting = true
        else sessionId = id
    }

    fun accept(next: StreamEvent.Result) {
        session(next.sessionId)
        if (result != null && result != next) conflicting = true
        else result = next
    }

    fun completed(exitCode: Int): PrintRequestId? {
        val final = result ?: return null
        if (exitCode != 0 || conflicting || final.isError || final.subtype != "success") return null
        return final.requestId?.let { PrintRequestId(it, sessionId) }
    }
}
