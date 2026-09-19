package com.cursoragent.service

/** One request, including preparation before a process exists. Exit codes alone never imply cancellation. */
class AgentRun(
    listener: AgentProcessListener,
    private val onFinished: (AgentRun) -> Unit = {},
) {
    private var listener: AgentProcessListener? = listener
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

    /** ACP cancels a turn while its resident connection may remain alive. */
    fun attachCancellation(cancel: () -> Unit) = attachProcess(cancel) { false }

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
            if (!finished && !wasStopped) listener?.let(block)
        }
    }

    /** Closing a tab releases its UI immediately, even if physical process exit is delayed. */
    fun detachListener() {
        synchronized(lock) { listener = null }
    }

    fun reportError(message: String) {
        synchronized(lock) {
            if (!finished && !wasStopped) error = message
        }
    }

    fun completeUncertain(message: String) = complete(-1, uncertain = message)

    /** Exactly one terminal callback; intentional stop suppresses shutdown stderr/result errors. */
    fun complete(exitCode: Int, errorOutput: String? = null, uncertain: String? = null, outcome: AgentTurnOutcome? = null, printRequestId: PrintRequestId? = null) {
        val stopped: Boolean
        val failure: String?
        val receiver: AgentProcessListener?
        synchronized(lock) {
            if (finished) return
            finished = true
            receiver = listener
            listener = null
            stopped = wasStopped
            failure = error ?: errorOutput?.takeIf { exitCode != 0 && it.isNotBlank() }
            stopAction = null
            hasExited = null
        }
        try {
            when {
                uncertain != null -> receiver?.onUncertain(uncertain)
                stopped -> receiver?.onStopped()
                failure != null -> receiver?.onError(failure)
                outcome != null -> receiver?.onTurnOutcome(outcome)
                exitCode == 0 && printRequestId != null -> receiver?.onPrintCompleted(printRequestId)
                else -> receiver?.onCompleted(exitCode)
            }
        } finally {
            onFinished(this)
        }
    }
}
