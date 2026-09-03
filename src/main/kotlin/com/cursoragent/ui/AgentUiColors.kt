package com.cursoragent.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

object AgentUiColors {
    val userBubbleBackground: Color
        get() = JBColor(
            Color(0xE8EEF7),
            Color(0x2B3140),
        )

    val assistantBubbleBackground: Color
        get() = JBColor(
            Color(0xF4F4F5),
            Color(0x1E1E1E),
        )

    val bubbleBorder: Color
        get() = JBColor(
            Color(0xD0D7DE),
            Color(0x3C3F41),
        )

    val mutedText: Color
        get() = JBColor(
            Color(0x6E7781),
            Color(0x8C8C8C),
        )

    fun bubbleBorder(padding: Int = 10): Border {
        val radius = JBUI.scale(8)
        return CompoundBorder(
            LineBorder(bubbleBorder, 1, true),
            EmptyBorder(padding, padding, padding, padding),
        )
    }

    fun sectionPadding(): Border = JBUI.Borders.empty(8, 12)
}
