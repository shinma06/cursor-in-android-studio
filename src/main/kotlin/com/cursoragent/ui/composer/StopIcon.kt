package com.cursoragent.ui.composer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

/** Cursor-style stop control, independent of font glyphs and text elision. */
internal object StopIcon : Icon {
    override fun getIconWidth() = JBUI.scale(18)
    override fun getIconHeight() = JBUI.scale(18)

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        val copy = g.create() as Graphics2D
        try {
            copy.translate(x, y)
            copy.scale(iconWidth / 18.0, iconHeight / 18.0)
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color = JBColor(Color(0x303030), Color(0xD9D9D9))
            copy.fill(Ellipse2D.Double(0.0, 0.0, 18.0, 18.0))
            copy.color = JBColor(Color.WHITE, Color(0x141414))
            copy.fill(RoundRectangle2D.Double(5.5, 5.5, 7.0, 7.0, 2.0, 2.0))
        } finally {
            copy.dispose()
        }
    }
}
