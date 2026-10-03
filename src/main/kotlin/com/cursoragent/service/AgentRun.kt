package com.cursoragent.service

/** One print request, including preparation before its OS process exists. */
class AgentRun(listener: AgentProcessListener) {
    private val lock = Any()
    private var listener: AgentProcessListener? = listener
    private var finished = false
    private var stopAction: (() -> Unit)? = null
    private var hasExited: (() -> Boolean)? = null
    private var preparationFinished = false

    @Volatile
    var wasStopped = false
        private set

    val isActive: Boolean
        get() = synchronized(lock) { !finished && !wasStopped }

    val blocksRestore: Boolean
        get() = synchronized(lock) { !preparationFinished || hasExited?.invoke() == false }

    fun finishPreparation() {
        synchronized(lock) { preparationFinished = true }
    }

    /** Adopt a late process only while this request still owns the operation. */
    fun attachProcess(stop: () -> Unit, isTerminated: () -> Boolean) {
        val reject = synchronized(lock) {
            hasExited = isTerminated
            if (finished || wasStopped) true else {
                stopAction = stop
                false
            }
        }
        if (reject) stop()
    }

    fun stop() {
        val action = synchronized(lock) {
            if (finished || wasStopped) return
            wasStopped = true
            listener = null
            stopAction.also { stopAction = null }
        }
        action?.invoke()
    }

    fun emit(block: (AgentProcessListener) -> Unit) {
        synchronized(lock) {
            if (!finished && !wasStopped) listener?.let(block)
        }
    }

    fun complete(exitCode: Int, error: String? = null) {
        val receiver = synchronized(lock) {
            if (finished) return
            finished = true
            stopAction = null
            hasExited = null
            listener.also { listener = null }
        }
        if (error == null) receiver?.onCompleted(exitCode) else receiver?.onError(error)
    }
}
