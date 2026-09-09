package com.cursoragent.acp

import java.time.Instant
import java.util.concurrent.TimeUnit

/** Track observed children across reparenting. This cannot discover a child that escaped between samples. */
internal class AcpProcessTree(private val process: Process) {
    private data class Child(val handle: ProcessHandle, val started: Instant) {
        fun alive(): Boolean {
            if (!handle.isAlive) return false
            val actual = handle.info().startInstant().orElse(null)
                ?: throw AcpException("ACP子プロセスの終了を識別できません")
            return actual == started
        }
    }
    private val children = linkedMapOf<Pair<Long, Instant>, Child>()

    @Synchronized
    fun sample() {
        children.entries.removeIf { !it.value.alive() }
        process.toHandle().descendants().use { descendants ->
            descendants.forEach { handle ->
                if (handle.isAlive) {
                    val start = handle.info().startInstant().orElse(null)
                        ?: throw AcpException("ACP子プロセスの識別情報を確認できません")
                    require(children.size < 512 || handle.pid() to start in children)
                    children.putIfAbsent(handle.pid() to start, Child(handle, start))
                }
            }
        }
    }

    @Synchronized
    fun isQuiet(): Boolean {
        sample()
        return children.values.none { it.alive() }
    }

    fun awaitQuiet(timeoutSeconds: Long, connectionAlive: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            if (!connectionAlive()) throw AcpException("ACPの接続が失われたため停止を確認できません")
            if (isQuiet()) return
            if (System.nanoTime() >= deadline) throw AcpException("ACP子プロセスの終了を確認できません")
            Thread.sleep(25) // Poll the exit condition; elapsed time by itself never establishes quiescence.
        }
    }

    @Synchronized
    fun destroyObserved(force: Boolean) {
        children.values.toList().asReversed().filter { it.alive() }.forEach {
            if (force) it.handle.destroyForcibly() else it.handle.destroy()
        }
    }
}
