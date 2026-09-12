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
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.text.NumberFormat
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/** Embedded content has no focus-dismiss listener; selector popups appear above it. */
class ContextUsageView {
    private val state = ContextUsageState()
    private val counters = List(4) { JLabel() }
    private val status = label("直近の応答").apply {
        font = font.deriveFont(font.size2D * 0.9f)
        border = JBUI.Borders.empty(2, 0, 10, 0)
    }
    private val modelLabel = label("").apply {
        putClientProperty("html.disable", true)
        minimumSize = JBUI.emptySize()
        border = JBUI.Borders.emptyBottom(4)
    }
    private val emptyMessage = label("まだ応答を開始していません").apply {
        border = JBUI.Borders.empty(8, 0, 2, 0)
    }
    private val counterRows = listOf("入力", "出力", "キャッシュ読み取り", "キャッシュ書き込み").mapIndexed { index, title ->
        row(label(title), counters[index].apply {
            font = AgentUiMetrics.textFont().deriveFont(Font.BOLD)
            horizontalAlignment = SwingConstants.RIGHT
        })
    }
    private val cacheDivider = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(7, 0)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createMatteBorder(JBUI.scale(1), 0, 0, 0, AgentUiColors.bubbleBorder)
        }, BorderLayout.CENTER)
    }
    val button: JButton = TokenCountsButton().apply {
        icon = TokenCountsIcon()
        horizontalAlignment = SwingConstants.CENTER
        preferredSize = JBUI.size(28, 28)
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
            border = JBUI.Borders.empty(10, 12, 12, 12)
            add(row(JLabel("トークン数").apply {
                font = AgentUiMetrics.textFont().let { it.deriveFont(Font.BOLD, it.size2D * 1.08f) }
            }, close).apply { border = JBUI.Borders.empty() })
            add(status.apply { alignmentX = Component.LEFT_ALIGNMENT })
            add(modelLabel.apply { alignmentX = Component.LEFT_ALIGNMENT })
            counterRows.forEachIndexed { index, row ->
                if (index == 2) add(cacheDivider)
                add(row)
            }
            add(emptyMessage.apply { alignmentX = Component.LEFT_ALIGNMENT })
        }
        panel.add(RoundedSurface(AgentUiColors.panelBackground).apply {
            border = AgentUiColors.RoundedBorder()
            add(content, BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        button.addActionListener { setExpanded(!panel.isVisible) }
        refresh()
    }

    fun beginTurn(model: String = ""): Long = state.begin(model).also { refresh() }
    fun reset() { state.clear(); refresh() }
    fun update(ticket: Long, usage: TokenUsage?) {
        if (state.accept(ticket, usage)) refresh()
    }

    fun finish(ticket: Long, outcome: UsagePhase) {
        if (state.finish(ticket, outcome)) refresh()
    }
    fun stop() { if (state.stop()) refresh() }

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
        cacheDivider.isVisible = values.take(2).any { it != null } && values.drop(2).any { it != null }
        status.text = when (state.phase) {
            UsagePhase.NOT_STARTED -> "直近の応答"
            UsagePhase.RUNNING -> "応答を準備・実行中"
            UsagePhase.STOPPING -> "停止処理中の応答"
            UsagePhase.COMPLETED -> "完了した応答"
            UsagePhase.STOPPED -> "停止した応答"
            UsagePhase.FAILED -> "失敗した応答"
        }
        status.isVisible = state.phase != UsagePhase.NOT_STARTED
        modelLabel.text = "送信時のモデル: ${state.model.ifEmpty { "接続先の既定" }}"
        modelLabel.toolTipText = "送信時の選択です。Autoや既定から実際のモデルは推定しません。${state.model}"
        modelLabel.isVisible = state.phase != UsagePhase.NOT_STARTED
        emptyMessage.text = when (state.phase) {
            UsagePhase.NOT_STARTED -> "まだ応答を開始していません"
            UsagePhase.RUNNING -> "トークン数の報告待ちです"
            UsagePhase.COMPLETED -> "この応答では情報未提供です"
            UsagePhase.STOPPING, UsagePhase.STOPPED, UsagePhase.FAILED -> "トークン数は未取得です"
        }
        emptyMessage.isVisible = !hasCounters
        status.toolTipText = "この応答で受信した値です。入力とキャッシュの重複関係は未確認のため合計しません。"
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

/** Share the hover background for keyboard focus, without an extra outline around the glyph. */
internal open class TokenCountsButton : JButton() {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
    }

    override fun paintComponent(g: Graphics) {
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val inset = JBUI.scale(1).toDouble()
            val arc = JBUI.scale(6).toDouble()
            // Background and glyph share width/2, height/2 at odd sizes and fractional scales.
            val outline = RoundRectangle2D.Double(
                inset, inset, (width - inset * 2).coerceAtLeast(0.0),
                (height - inset * 2).coerceAtLeast(0.0), arc, arc,
            )
            if (model.isRollover || model.isPressed || hasFocus()) {
                copy.color = JBUI.CurrentTheme.ActionButton.pressedBackground()
                copy.fill(outline)
            }
            icon?.let {
                copy.translate((width - it.iconWidth) / 2.0, (height - it.iconHeight) / 2.0)
                it.paintIcon(this, copy, 0, 0)
            }
        } finally {
            copy.dispose()
        }
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
            copy.translate(x.toDouble(), y.toDouble())
            copy.scale(iconWidth / 18.0, iconHeight / 18.0)
            copy.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            copy.color = AgentUiColors.mutedText
            copy.draw(RoundRectangle2D.Double(3.0, 1.0, 12.0, 16.0, 2.0, 2.0))
            for (lineY in listOf(5.0, 9.0, 13.0)) {
                copy.draw(Line2D.Double(6.0, lineY, 12.0, lineY))
            }
        } finally {
            copy.dispose()
        }
    }
}
