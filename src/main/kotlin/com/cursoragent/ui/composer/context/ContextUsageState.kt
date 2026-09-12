package com.cursoragent.ui.composer.context

import com.cursoragent.parser.TokenUsage

enum class UsagePhase { NOT_STARTED, RUNNING, STOPPING, COMPLETED, STOPPED, FAILED }

/** EDT-owned generation gate. Finishing seals counters without discarding the response's values. */
class ContextUsageState {
    var usage: TokenUsage? = null
        private set
    var phase = UsagePhase.NOT_STARTED
        private set
    var model = ""
        private set
    private var generation = 0L

    fun begin(model: String = ""): Long {
        clear()
        this.model = model
        phase = UsagePhase.RUNNING
        return generation
    }

    fun clear() {
        usage = null
        model = ""
        phase = UsagePhase.NOT_STARTED
        generation++
    }

    fun accept(ticket: Long, value: TokenUsage?): Boolean {
        if (ticket != generation || phase != UsagePhase.RUNNING) return false
        usage = value
        return true
    }

    fun finish(ticket: Long, outcome: UsagePhase): Boolean {
        require(outcome in setOf(UsagePhase.COMPLETED, UsagePhase.STOPPED, UsagePhase.FAILED))
        if (ticket != generation || phase !in setOf(UsagePhase.RUNNING, UsagePhase.STOPPING)) return false
        phase = outcome
        generation++
        return true
    }

    fun stop(): Boolean {
        if (phase != UsagePhase.RUNNING) return false
        phase = UsagePhase.STOPPING
        return true
    }
}
