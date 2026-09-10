package com.cursoragent.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.border.AbstractBorder
import javax.swing.border.Border

object AgentUiColors {
    const val CORNER_ARC = 12
    val panelBackground: Color get() = JBUI.CurrentTheme.ToolWindow.background()
    val userBubbleBackground: Color get() = JBColor(Color(0xF0F0F0), Color(0x222222))
    val assistantBubbleBackground: Color get() = panelBackground
    val composerBackground: Color get() = JBColor(Color.WHITE, Color(0x202020))
    val bubbleBorder: Color get() = JBColor(Color(0xDDDDDD), Color(0x373737))
    val mutedText: Color get() = JBColor(Color(0x686868), Color(0x969696))

    fun bubbleBorder(padding: Int = 10): Border = javax.swing.border.CompoundBorder(RoundedBorder(), JBUI.Borders.empty(padding))

    class RoundedBorder(private val radius: Int = CORNER_ARC) : AbstractBorder() {
        override fun getBorderInsets(c: Component) = JBUI.insets(1)
        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val copy = g.create() as Graphics2D
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                copy.color = bubbleBorder
                val arc = JBUI.scale(radius)
                copy.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
            } finally {
                copy.dispose()
            }
        }
    }
}

/** Paint the fill with the same corners as the border, without a square opaque backdrop. */
class RoundedSurface(private val fill: Color) : JPanel(java.awt.BorderLayout()) {
    init { isOpaque = false }
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color = fill
            val arc = JBUI.scale(AgentUiColors.CORNER_ARC)
            copy.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
        } finally {
            copy.dispose()
        }
    }
}
