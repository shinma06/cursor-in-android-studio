package com.cursoragent.ui.header

import com.cursoragent.ui.AgentUiColors
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class AgentHeaderBar : JPanel(BorderLayout()) {
    var onNewChat: () -> Unit = {}
    var onPastChatsClicked: () -> Unit = {}

    val sessionLabel = JBLabel("Ready").apply {
        foreground = AgentUiColors.mutedText
    }

    val pastChatsButton = JButton(AllIcons.Vcs.History).apply {
        toolTipText = "Past Chats"
        isBorderPainted = false
        isContentAreaFilled = false
    }

    init {
        border = JBUI.Borders.empty(4, 12, 4, 12)
        isOpaque = false

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(
                JButton(AllIcons.General.Add).apply {
                    toolTipText = "New Chat"
                    isBorderPainted = false
                    isContentAreaFilled = false
                    addActionListener { onNewChat() }
                },
            )
            add(pastChatsButton.apply { addActionListener { onPastChatsClicked() } })
        }

        add(left, BorderLayout.WEST)
        add(sessionLabel, BorderLayout.EAST)
    }

    fun setSessionStatus(text: String) {
        sessionLabel.text = text
    }
}
