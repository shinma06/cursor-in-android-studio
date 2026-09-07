package com.cursoragent.service

/** One request, including preparation before a process exists. Exit codes alone never imply cancellation. */
class AgentRun(private val listener: AgentProcessListener) {
    private val lock = Any()
    private var finished = false
    private var stopAction: (() -> Unit)? = null
    private var hasExited: (() -> Boolean)? = null
    private var error: String? = null

    @Volatile
    var wasStopped: Boolean = false
        private set

    val isActive: Boolean
        get() = synchronized(lock) { !finished && !wasStopped }

    /** A process created after cancellation must still be destroyed, not published as a new run. */
    fun attachProcess(stop: () -> Unit, isTerminated: () -> Boolean) {
        val reject = synchronized(lock) {
            if (finished || wasStopped) true else {
                stopAction = stop
                hasExited = isTerminated
                false
            }
        }
        if (reject) stop()
    }

    fun stop() {
        val action: (() -> Unit)?
        synchronized(lock) {
            // If the OS exit is already observed, let its actual outcome win.
            if (finished || wasStopped || hasExited?.invoke() == true) return
            wasStopped = true
            action = stopAction
        }
        if (action != null) action() else complete(0)
    }

    fun emit(block: (AgentProcessListener) -> Unit) {
        synchronized(lock) {
            if (!finished && !wasStopped) block(listener)
        }
    }

    fun reportError(message: String) {
        synchronized(lock) {
            if (!finished && !wasStopped) error = message
        }
    }

    /** Exactly one terminal callback; intentional stop suppresses shutdown stderr/result errors. */
    fun complete(exitCode: Int, errorOutput: String? = null) {
        val stopped: Boolean
        val failure: String?
        synchronized(lock) {
            if (finished) return
            finished = true
            stopped = wasStopped
            failure = error ?: errorOutput?.takeIf { exitCode != 0 && it.isNotBlank() }
            stopAction = null
            hasExited = null
        }
        when {
            stopped -> listener.onStopped()
            failure != null -> listener.onError(failure)
            else -> listener.onCompleted(exitCode)
        }
    }
}
