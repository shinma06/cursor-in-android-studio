package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.Icon

internal class ModeIcon(private val mode: AgentMode) : Icon {
    override fun getIconWidth() = JBUI.scale(16)
    override fun getIconHeight() = JBUI.scale(16)
    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        val copy = g.create() as Graphics2D
        try {
            copy.translate(x, y)
            copy.scale(iconWidth / 16.0, iconHeight / 16.0)
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color = c.foreground
            copy.stroke = BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            when (mode) {
                AgentMode.AGENT -> copy.draw(Path2D.Double().apply {
                    moveTo(8.0, 8.0)
                    curveTo(3.0, 1.0, 0.0, 5.0, 1.0, 9.0)
                    curveTo(2.0, 14.0, 6.0, 10.0, 8.0, 8.0)
                    curveTo(13.0, 1.0, 16.0, 5.0, 15.0, 9.0)
                    curveTo(14.0, 14.0, 10.0, 10.0, 8.0, 8.0)
                })
                AgentMode.PLAN -> for (row in listOf(2, 7, 12)) {
                    copy.drawOval(1, row, 3, 3)
                    copy.drawLine(7, row + 1, 15, row + 1)
                }
                AgentMode.ASK -> copy.draw(Path2D.Double().apply {
                    moveTo(3.0, 2.0)
                    lineTo(13.0, 2.0)
                    quadTo(15.0, 2.0, 15.0, 4.0)
                    lineTo(15.0, 10.0)
                    quadTo(15.0, 12.0, 13.0, 12.0)
                    lineTo(6.0, 12.0)
                    lineTo(2.0, 15.0)
                    lineTo(2.0, 4.0)
                    quadTo(2.0, 2.0, 3.0, 2.0)
                })
            }
        } finally {
            copy.dispose()
        }
    }
}
