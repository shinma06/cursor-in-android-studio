package com.cursoragent.ui.composer.context

import com.cursoragent.parser.TokenUsage
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.cursoragent.ui.RoundedSurface
import com.cursoragent.ui.composer.SelectorButton
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.text.NumberFormat
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/** Embedded content has no focus-dismiss listener; selector popups appear above it. */
class ContextUsageView {
    private val state = ContextUsageState()
    private val counters = List(4) { JLabel() }
    private val status = label("応答完了時に更新")
    val button = SelectorButton().apply {
        icon = UnknownUsageRing()
        horizontalAlignment = SwingConstants.CENTER
        preferredSize = JBUI.size(24, 24)
        border = JBUI.Borders.empty()
        toolTipText = "Show Context Usage — コンテキスト使用量（使用率は取得不可）"
        accessibleContext.accessibleName = "Show Context Usage"
        accessibleContext.accessibleDescription = "コンテキスト使用量を表示。使用率は取得不可。"
    }
    val panel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(8)
        isVisible = false
    }

    init {
        val close = SelectorButton().apply {
            text = "×"
            preferredSize = JBUI.size(24, 24)
            horizontalAlignment = SwingConstants.CENTER
            toolTipText = "コンテキスト使用量を閉じる"
            accessibleContext.accessibleName = toolTipText
            addActionListener { setExpanded(false); button.requestFocusInWindow() }
        }
        val content = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 12)
            add(row(label("Context Usage"), close))
            add(row(label("使用率・残量"), label("取得不可")))
            add(JPanel().apply {
                background = AgentUiColors.bubbleBorder
                preferredSize = JBUI.size(0, 4)
                maximumSize = java.awt.Dimension(Int.MAX_VALUE, JBUI.scale(4))
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(row(label("種別別内訳"), label("取得不可")))
            add(row(label("直近の応答のトークン数"), status))
            listOf("入力", "出力", "キャッシュ読み取り", "キャッシュ書き込み").forEachIndexed { index, title ->
                add(row(label(title), counters[index].apply { font = AgentUiMetrics.textFont(); foreground = AgentUiColors.mutedText }))
            }
        }
        panel.add(RoundedSurface(AgentUiColors.panelBackground).apply {
            border = AgentUiColors.RoundedBorder()
            add(content, BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        button.addActionListener { setExpanded(!panel.isVisible) }
        refresh()
    }

    fun beginTurn(): Long = state.clear().also { refresh() }
    fun reset() { state.clear(); refresh() }
    fun update(ticket: Long, usage: TokenUsage?) {
        if (state.accept(ticket, usage)) refresh()
    }

    private fun setExpanded(expanded: Boolean) {
        panel.isVisible = expanded
        button.accessibleContext.accessibleDescription = if (expanded) "コンテキスト使用量を表示中。もう一度押すと閉じます。" else "コンテキスト使用量を表示。使用率は取得不可。"
        panel.parent?.revalidate()
        panel.parent?.repaint()
    }

    private fun refresh() {
        val usage = state.usage
        val values = listOf(usage?.inputTokens, usage?.outputTokens, usage?.cacheReadTokens, usage?.cacheWriteTokens)
        values.forEachIndexed { index, value -> counters[index].text = value?.let { NumberFormat.getIntegerInstance(Locale.JAPAN).format(it) } ?: "取得不可" }
        status.text = if (usage == null) "応答完了時に更新" else "CLI報告値"
        status.toolTipText = "現在のコンテキスト使用量ではありません。入力とキャッシュの重複関係は未確認のため合計しません。"
        panel.revalidate()
        panel.repaint()
    }

    private fun label(text: String) = JLabel(text).apply {
        font = AgentUiMetrics.textFont()
        foreground = AgentUiColors.mutedText
    }

    private fun row(left: Component, right: Component) = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(4, 0)
        add(left, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)
    }
}

/** A neutral broken ring means unknown, never a fabricated progress arc. */
private class UnknownUsageRing : Icon {
    override fun getIconWidth() = JBUI.scale(18)
    override fun getIconHeight() = JBUI.scale(18)
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.stroke = BasicStroke(JBUI.scale(2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            copy.color = AgentUiColors.mutedText
            val inset = JBUI.scale(2)
            copy.drawArc(x + inset, y + inset, iconWidth - inset * 2, iconHeight - inset * 2, 65, -310)
        } finally {
            copy.dispose()
        }
    }
}
