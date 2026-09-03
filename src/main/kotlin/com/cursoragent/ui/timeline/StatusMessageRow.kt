package com.cursoragent.ui.timeline

import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel

class StatusMessageRow(text: String) : JPanel(BorderLayout()) {
    private val label = JBLabel(text).apply {
        foreground = AgentUiColors.mutedText
        font = font.deriveFont(Font.ITALIC, font.size - 1f)
        border = JBUI.Borders.empty(4, 4, 4, 48)
    }

    init {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(4)
        add(label, BorderLayout.WEST)
    }

    fun updateText(text: String) {
        label.text = text
    }
}
