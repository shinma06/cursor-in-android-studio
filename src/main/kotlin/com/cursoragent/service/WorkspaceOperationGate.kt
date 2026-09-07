package com.cursoragent.service

/** Project-wide exclusion spanning preparation, process construction and physical exit, not UI completion. */
class WorkspaceOperationGate {
    private val lock = Any()
    private var occupied = false

    fun tryPrepare(): Preparation? = synchronized(lock) {
        if (occupied) null else {
            occupied = true
            Preparation()
        }
    }

    fun tryRestore(): AutoCloseable? = synchronized(lock) {
        if (occupied) null else {
            occupied = true
            once { synchronized(lock) { occupied = false } }
        }
    }

    inner class Preparation internal constructor() : AutoCloseable {
        private var preparing = true
        private var processes = 0

        /** Reserve before OSProcessHandler construction; cancellation must not release this reservation. */
        fun launchingProcess(): AutoCloseable = synchronized(lock) {
            check(preparing) { "Preparation already ended" }
            processes++
            once {
                synchronized(lock) {
                    processes--
                    releaseIfIdle()
                }
            }
        }

        override fun close() = synchronized(lock) {
            if (!preparing) return@synchronized
            preparing = false
            releaseIfIdle()
        }

        private fun releaseIfIdle() {
            if (!preparing && processes == 0) occupied = false
        }
    }

    private fun once(action: () -> Unit): AutoCloseable {
        val closed = java.util.concurrent.atomic.AtomicBoolean()
        return AutoCloseable { if (closed.compareAndSet(false, true)) action() }
    }
}
