package com.cursoragent.ui.timeline

import com.cursoragent.ui.AgentUiColors
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class UserMessageBubble(text: String) : JPanel(BorderLayout()) {
    var onRollbackRequested: (() -> Unit)? = null

    private val rollbackButton = JButton(AllIcons.Actions.Rollback).apply {
        toolTipText = "この応答の前の状態へ復元"
        isBorderPainted = false
        isContentAreaFilled = false
        isVisible = false
        addActionListener { onRollbackRequested?.invoke() }
    }

    init {
        isOpaque = false
        val bubble = com.cursoragent.ui.RoundedSurface(AgentUiColors.userBubbleBackground).apply {
            border = AgentUiColors.bubbleBorder(8)
            add(MessageTextPane().apply {
                this.text = "<html><body>${escapeHtml(text).replace("\n", "<br>")}</body></html>"
            }, BorderLayout.CENTER)
            add(rollbackButton.apply {
                preferredSize = JBUI.size(24, 24)
                minimumSize = preferredSize
                margin = JBUI.emptyInsets()
            }, BorderLayout.EAST)
        }
        add(bubble, BorderLayout.CENTER)
    }

    fun setCheckpointAvailable(available: Boolean, reason: String? = null) {
        rollbackButton.isVisible = available || reason != null
        rollbackButton.isEnabled = available
        rollbackButton.toolTipText = reason ?: "この応答の前の状態へ復元"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
