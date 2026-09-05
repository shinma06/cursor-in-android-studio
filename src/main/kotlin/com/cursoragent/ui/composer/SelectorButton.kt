package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/** Size and paint the same content bounds; IDE button delegates must not add hidden padding. */
open class SelectorButton : JButton() {
    var pillColor: Color? = null
    var showsChevron = false
        set(value) {
            field = value
            revalidate()
            repaint()
        }

    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
        border = JBUI.Borders.empty(3, 6)
        font = AgentUiMetrics.textFont()
        horizontalAlignment = SwingConstants.LEFT
        iconTextGap = JBUI.scale(5)
    }

    private val chevronSpace: Int get() = if (showsChevron) JBUI.scale(13) else 0

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val metrics = getFontMetrics(font)
        val iconWidth = icon?.iconWidth ?: 0
        val labelWidth = metrics.stringWidth(text.orEmpty())
        val gap = if (iconWidth > 0 && labelWidth > 0) iconTextGap else 0
        return Dimension(
            insets.left + iconWidth + gap + labelWidth + chevronSpace + insets.right,
            maxOf(JBUI.scale(24), maxOf(metrics.height, icon?.iconHeight ?: 0) + insets.top + insets.bottom),
        )
    }

    override fun paintComponent(g: Graphics) {
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.font = font
            val fill = pillColor ?: if (model.isRollover || hasFocus()) AgentUiColors.userBubbleBackground else null
            if (fill != null) {
                copy.color = fill
                copy.fillRoundRect(0, 0, width, height, height, height)
            }
            val color = if (isEnabled) foreground else AgentUiColors.mutedText
            copy.color = color
            if (hasFocus()) {
                copy.drawRoundRect(1, 1, width - 3, height - 3, height - 3, height - 3)
            }
            val view = Rectangle(
                insets.left, insets.top,
                (width - insets.left - insets.right - chevronSpace).coerceAtLeast(0),
                (height - insets.top - insets.bottom).coerceAtLeast(0),
            )
            val iconRect = Rectangle()
            val textRect = Rectangle()
            val label = SwingUtilities.layoutCompoundLabel(
                this, getFontMetrics(font), text.orEmpty(), icon, SwingConstants.CENTER, horizontalAlignment,
                SwingConstants.CENTER, SwingConstants.RIGHT, view, iconRect, textRect, iconTextGap,
            )
            val content = copy.create() as Graphics2D
            try {
                content.clip(view)
                icon?.paintIcon(this, content, iconRect.x, iconRect.y)
                content.drawString(label, textRect.x, textRect.y + content.fontMetrics.ascent)
            } finally {
                content.dispose()
            }
            if (showsChevron && width >= insets.left + insets.right + chevronSpace) {
                // A geometric chevron shares the vertical center of the label/icon, not a font baseline.
                val x = (width - insets.right - JBUI.scale(7)).toDouble()
                val y = height / 2.0
                val halfHeight = JBUI.scale(3) / 2.0
                copy.stroke = BasicStroke(JBUI.scale(6) / 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                copy.draw(Path2D.Double().apply {
                    moveTo(x, y - halfHeight)
                    lineTo(x + JBUI.scale(3), y + halfHeight)
                    lineTo(x + JBUI.scale(6), y - halfHeight)
                })
            }
        } finally {
            copy.dispose()
        }
    }
}
