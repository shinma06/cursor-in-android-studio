package com.cursoragent.service

/** Project-wide exclusion spanning preparation, process construction and physical exit, not UI completion. */
class WorkspaceOperationGate {
    private val lock = Any()
    private var preparations = 0
    private var restoring = false

    fun tryPrepare(): Preparation? = synchronized(lock) {
        if (restoring) null else {
            preparations++
            Preparation()
        }
    }

    fun tryRestore(): AutoCloseable? = synchronized(lock) {
        if (restoring || preparations != 0) null else {
            restoring = true
            once { synchronized(lock) { restoring = false } }
        }
    }

    inner class Preparation internal constructor() : AutoCloseable {
        private var preparing = true
        private var processes = 0
        private var released = false

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
            if (!preparing && processes == 0 && !released) {
                released = true
                preparations--
            }
        }
    }

    private fun once(action: () -> Unit): AutoCloseable {
        val closed = java.util.concurrent.atomic.AtomicBoolean()
        return AutoCloseable { if (closed.compareAndSet(false, true)) action() }
    }
}
