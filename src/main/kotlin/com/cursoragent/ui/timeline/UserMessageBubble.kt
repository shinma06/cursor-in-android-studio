package com.cursoragent.ui.timeline

import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

class UserMessageBubble(text: String) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 48, 4, 4)

        val bubble = JPanel(BorderLayout()).apply {
            background = AgentUiColors.userBubbleBackground
            border = AgentUiColors.bubbleBorder()
            isOpaque = true
            add(
                JBLabel("<html>${escapeHtml(text).replace("\n", "<br>")}</html>").apply {
                    border = JBUI.Borders.empty()
                },
                BorderLayout.CENTER,
            )
        }

        add(bubble, BorderLayout.EAST)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
