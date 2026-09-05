package com.cursoragent.ui.timeline

import com.cursoragent.PluginBrand
import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class EmptyStatePanel : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        border = JBUI.Borders.empty(24)

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false

            val title = JBLabel(PluginBrand.NAME).apply {
                alignmentX = CENTER_ALIGNMENT
                font = font.deriveFont(Font.BOLD, font.size + 2f)
            }
            val subtitle = JBLabel("プロンプトを入力して開始").apply {
                alignmentX = CENTER_ALIGNMENT
                foreground = AgentUiColors.mutedText
            }

            add(Box.createVerticalGlue())
            add(title)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(subtitle)
            add(Box.createVerticalGlue())
        }

        add(content, BorderLayout.CENTER)
    }
}
