package com.cursoragent.ui.timeline

import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

class AssistantMessageBubble(initialText: String = "") : JPanel(BorderLayout()) {
    private val contentLabel = JBLabel().apply {
        border = JBUI.Borders.empty()
    }
    private val contentBuilder = StringBuilder(initialText)

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 4, 4, 48)

        val bubble = JPanel(BorderLayout()).apply {
            background = AgentUiColors.assistantBubbleBackground
            border = AgentUiColors.bubbleBorder()
            isOpaque = true
            add(contentLabel, BorderLayout.CENTER)
        }

        add(bubble, BorderLayout.WEST)
        if (initialText.isNotEmpty()) {
            setContent(initialText)
        }
    }

    fun appendContent(text: String) {
        contentBuilder.append(text)
        refreshLabel()
    }

    fun setContent(text: String) {
        contentBuilder.clear()
        contentBuilder.append(text)
        refreshLabel()
    }

    private fun refreshLabel() {
        val html = escapeHtml(contentBuilder.toString()).replace("\n", "<br>")
        contentLabel.text = "<html>$html</html>"
        revalidate()
        repaint()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
