package com.cursoragent.ui.composer.context

import com.cursoragent.parser.TokenUsage

/** EDT-owned generation gate: a stopped/replaced turn must not restore stale counters. */
class ContextUsageState {
    var usage: TokenUsage? = null
        private set
    private var generation = 0L

    fun clear(): Long {
        usage = null
        return ++generation
    }

    fun accept(ticket: Long, value: TokenUsage?): Boolean {
        if (ticket != generation) return false
        usage = value
        return true
    }
}
