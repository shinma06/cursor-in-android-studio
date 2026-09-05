package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.SwingConstants

/** Compact popup trigger; the clickable area follows its label, including when clipped. */
open class SelectorButton : JButton() {
    var pillColor: Color? = null

    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = JBUI.Borders.empty(4, 7)
        horizontalAlignment = SwingConstants.LEFT
        iconTextGap = JBUI.scale(6)
    }

    override fun getPreferredSize(): Dimension = super.getPreferredSize().let {
        if (isPreferredSizeSet) it else Dimension(it.width, maxOf(it.height, JBUI.scale(26)))
    }

    override fun paintComponent(g: Graphics) {
        val fill = pillColor ?: if (model.isRollover || hasFocus()) AgentUiColors.userBubbleBackground else null
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (fill != null) {
                copy.color = fill
                copy.fillRoundRect(0, 0, width, height, height, height)
            }
            if (hasFocus()) {
                copy.color = foreground
                copy.drawRoundRect(1, 1, width - 3, height - 3, height - 3, height - 3)
            }
        } finally {
            copy.dispose()
        }
        super.paintComponent(g)
    }
}
