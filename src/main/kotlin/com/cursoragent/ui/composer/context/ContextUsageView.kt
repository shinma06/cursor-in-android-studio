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
    private val status = label("直近の応答")
    private val emptyMessage = label("応答後に表示します")
    private val counterRows = listOf("入力", "出力", "キャッシュ読み取り", "キャッシュ書き込み").mapIndexed { index, title ->
        row(label(title), counters[index].apply {
            font = AgentUiMetrics.textFont()
            foreground = AgentUiColors.mutedText
        })
    }
    val button = SelectorButton().apply {
        icon = TokenCountsIcon()
        horizontalAlignment = SwingConstants.CENTER
        preferredSize = JBUI.size(24, 24)
        border = JBUI.Borders.empty()
        toolTipText = "直近の応答のトークン数"
        accessibleContext.accessibleName = "トークン数"
        accessibleContext.accessibleDescription = "直近の応答のトークン数を表示します。"
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
            toolTipText = "トークン数を閉じる"
            accessibleContext.accessibleName = toolTipText
            addActionListener { setExpanded(false); button.requestFocusInWindow() }
        }
        val content = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 12)
            add(row(label("トークン数"), close))
            add(status.apply { alignmentX = Component.LEFT_ALIGNMENT })
            counterRows.forEach { add(it) }
            add(emptyMessage.apply { alignmentX = Component.LEFT_ALIGNMENT })
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
        button.accessibleContext.accessibleDescription = if (expanded) "トークン数を表示中。もう一度押すと閉じます。" else "直近の応答のトークン数を表示します。"
        panel.parent?.revalidate()
        panel.parent?.repaint()
    }

    private fun refresh() {
        val usage = state.usage
        val values = listOf(usage?.inputTokens, usage?.outputTokens, usage?.cacheReadTokens, usage?.cacheWriteTokens)
        values.forEachIndexed { index, value ->
            counters[index].text = value?.let { NumberFormat.getIntegerInstance(Locale.JAPAN).format(it) }.orEmpty()
            counterRows[index].isVisible = value != null
        }
        val hasCounters = values.any { it != null }
        status.isVisible = hasCounters
        emptyMessage.isVisible = !hasCounters
        status.toolTipText = "応答完了時に報告された値です。入力とキャッシュの重複関係は未確認のため合計しません。"
        panel.revalidate()
        panel.repaint()
        panel.parent?.revalidate()
        panel.parent?.repaint()
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

/** A static document with text lines opens the counts; it never represents a percentage. */
private class TokenCountsIcon : Icon {
    override fun getIconWidth() = JBUI.scale(18)
    override fun getIconHeight() = JBUI.scale(18)
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.stroke = BasicStroke(JBUI.scale(2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            copy.color = AgentUiColors.mutedText
            copy.drawRoundRect(x + JBUI.scale(3), y + JBUI.scale(1), JBUI.scale(12), JBUI.scale(16), JBUI.scale(2), JBUI.scale(2))
            for (lineY in listOf(5, 9, 13)) {
                copy.drawLine(x + JBUI.scale(6), y + JBUI.scale(lineY), x + JBUI.scale(12), y + JBUI.scale(lineY))
            }
        } finally {
            copy.dispose()
        }
    }
}
