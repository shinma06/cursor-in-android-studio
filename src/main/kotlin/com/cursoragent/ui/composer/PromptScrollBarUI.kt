package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicScrollBarUI

/** A visible, narrow thumb even when macOS is configured to auto-hide overlay scrollbars. */
internal class PromptScrollBarUI : BasicScrollBarUI() {
    override fun createDecreaseButton(orientation: Int): JButton = emptyButton()
    override fun createIncreaseButton(orientation: Int): JButton = emptyButton()
    override fun getMinimumThumbSize(): Dimension = JBUI.size(8, 24)
    override fun paintTrack(g: Graphics, c: JComponent, bounds: Rectangle) = Unit
    override fun paintThumb(g: Graphics, c: JComponent, bounds: Rectangle) {
        if (bounds.isEmpty || !scrollbar.isEnabled) return
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color = AgentUiColors.bubbleBorder
            val inset = JBUI.scale(2)
            copy.fillRoundRect(bounds.x + inset, bounds.y, bounds.width - inset * 2, bounds.height, JBUI.scale(6), JBUI.scale(6))
        } finally {
            copy.dispose()
        }
    }

    private fun emptyButton() = JButton().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        maximumSize = Dimension(0, 0)
        isFocusable = false
    }
}
