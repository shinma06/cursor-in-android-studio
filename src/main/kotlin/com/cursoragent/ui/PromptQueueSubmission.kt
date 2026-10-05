package com.cursoragent.ui

import com.cursoragent.service.AgentRun
import com.cursoragent.ui.timeline.RunPhase

/** One explicit Send Now intent. A terminal event alone never authorizes a different row or run. */
internal class PromptQueueSubmission(
    private val queue: PromptQueue,
    private val isCurrent: () -> Boolean,
    private val activeRun: () -> AgentRun?,
    private val generation: () -> Long,
    private val dispatchLater: (() -> Unit) -> Unit,
    private val start: (QueuedPrompt) -> Boolean,
    private val stop: (AgentRun) -> Unit,
    private val changed: () -> Unit,
    private val stopFailed: () -> Unit,
) {
    private data class Pending(val prompt: QueuedPrompt, val run: AgentRun, val generation: Long, val revision: Long, val wasPaused: Boolean)
    private var pending: Pending? = null

    val available: Boolean
        get() = isCurrent() && pending == null && activeRun()?.wasStopped != true

    fun cancel() { pending = null }

    fun submit(entry: QueuedPrompt): Boolean {
        if (!available || queue.snapshot().none { it === entry }) return false
        val wasPaused = queue.paused
        queue.pause()
        val run = activeRun()
        if (run == null) {
            val started = queue.dispatchSelected(entry, isCurrent(), resumeAutomatic = !wasPaused, start = start)
            changed()
            return started
        }
        pending = Pending(entry, run, generation(), queue.version, wasPaused)
        changed()
        try {
            stop(run)
        } catch (_: Exception) {
            cancel()
            stopFailed()
            return false
        }
        return true
    }

    /** Called after controller cleanup. Defer until the old run's finally releases its reservation. */
    fun finished(run: AgentRun?, phase: RunPhase): Boolean {
        val request = pending?.takeIf { it.run === run } ?: return false
        if (phase != RunPhase.COMPLETED && phase != RunPhase.STOPPED) {
            cancel()
            return true
        }
        dispatchLater {
            if (pending !== request) return@dispatchLater
            pending = null
            if (request.generation == generation() && isCurrent() && activeRun() == null) {
                queue.dispatchSelected(request.prompt, true,
                    resumeAutomatic = !request.wasPaused && queue.version == request.revision, start = start)
            }
            changed()
        }
        return true
    }
}
