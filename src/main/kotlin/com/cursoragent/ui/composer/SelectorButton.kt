package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import javax.swing.JButton
import javax.swing.SwingConstants

/** Compact popup trigger; keeps the standard button focus and keyboard actions. */
open class SelectorButton : JButton() {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = JBUI.Borders.empty(4, 6)
        horizontalAlignment = SwingConstants.LEFT
        minimumSize = JBUI.size(40, 26)
        preferredSize = JBUI.size(160, 26)
    }

    override fun paintComponent(g: Graphics) {
        if (model.isRollover || hasFocus()) {
            g.color = AgentUiColors.userBubbleBackground
            g.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
        }
        super.paintComponent(g)
    }
}
