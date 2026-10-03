package com.cursoragent.ui.timeline

import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

internal enum class RunPhase(val label: String, val terminal: Boolean = false) {
    PREPARING("送信を準備中"), RUNNING("実行中"), THINKING("考え中"), TOOL("ツール実行中"),
    STOPPING("停止を確認中"), COMPLETED("完了", true), STOPPED("停止しました", true), FAILED("失敗", true),
}

/** EDT-owned, one per conversation. This measures the whole turn, never provider thinking time. */
internal class RunStatusPanel(private val nanoTime: () -> Long = System::nanoTime) : JPanel(BorderLayout()) {
    private val label = JLabel()
    private val timer = Timer(1000) { refresh() }
    private var startedAt: Long? = null
    private var endedAt: Long? = null
    private var disposed = false
    var phase = RunPhase.PREPARING
        private set
    val isTicking: Boolean get() = timer.isRunning
    val statusText: String get() = label.text

    init {
        isOpaque = false
        isVisible = false
        label.toolTipText = "送信準備から終了確認までの経過時間です。思考だけの時間ではありません。"
        add(label, BorderLayout.CENTER)
    }

    fun begin() {
        if (disposed) return
        startedAt = nanoTime()
        endedAt = null
        phase = RunPhase.PREPARING
        isVisible = true
        refresh()
        timer.start()
    }

    fun update(next: RunPhase) {
        if (disposed || startedAt == null || phase.terminal) return
        if (phase == RunPhase.STOPPING && !next.terminal) return
        phase = next
        if (next.terminal) {
            endedAt = nanoTime()
            timer.stop()
        }
        refresh()
    }

    fun dispose() {
        disposed = true
        timer.stop()
    }

    private fun refresh() {
        if (disposed) return
        val start = startedAt ?: return
        val seconds = ((endedAt ?: nanoTime()) - start).coerceAtLeast(0) / 1_000_000_000
        label.text = "${phase.label} · 経過 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}
