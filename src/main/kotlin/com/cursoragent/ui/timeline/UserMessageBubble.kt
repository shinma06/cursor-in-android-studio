package com.cursoragent.ui.timeline

import com.cursoragent.ui.AgentUiColors
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class UserMessageBubble(text: String) : JPanel(BorderLayout()) {
    var onRollbackRequested: (() -> Unit)? = null

    private val rollbackButton = JButton(AllIcons.Actions.Rollback).apply {
        toolTipText = "Rollback to before this prompt"
        isBorderPainted = false
        isContentAreaFilled = false
        isVisible = false
        addActionListener { onRollbackRequested?.invoke() }
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 4, 4, 4)

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

        // Bubble stays right-aligned (chat-style) regardless of whether the
        // rollback icon on the WEST edge is currently visible.
        val bubbleWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(44)
            add(bubble, BorderLayout.EAST)
        }

        add(rollbackButton, BorderLayout.WEST)
        add(bubbleWrapper, BorderLayout.CENTER)
    }

    fun setCheckpointAvailable(available: Boolean) {
        rollbackButton.isVisible = available
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
