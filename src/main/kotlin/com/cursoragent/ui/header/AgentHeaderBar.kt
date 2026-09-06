package com.cursoragent.ui.header

import com.cursoragent.PluginBrand
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.composer.SelectorButton
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

    val optionsButton = SelectorButton().apply {
        text = "⋯"
        horizontalAlignment = javax.swing.SwingConstants.CENTER
        toolTipText = "チャット設定"
        accessibleContext.accessibleName = toolTipText
        foreground = AgentUiColors.mutedText
        preferredSize = JBUI.size(26, 26)
        margin = JBUI.emptyInsets()
    }

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

        val title = JBLabel("Agent").apply {
            font = font.deriveFont(java.awt.Font.PLAIN)
            toolTipText = PluginBrand.NAME
        }
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(sessionLabel.apply { font = font.deriveFont(font.size2D - 1f) })
            add(JButton(AllIcons.General.Add).apply {
                toolTipText = "New Chat"
                isBorderPainted = false
                isContentAreaFilled = false
                preferredSize = JBUI.size(26, 26)
                margin = JBUI.emptyInsets()
                addActionListener { onNewChat() }
            })
            add(pastChatsButton.apply {
                preferredSize = JBUI.size(26, 26)
                margin = JBUI.emptyInsets()
                addActionListener { onPastChatsClicked() }
            })
            add(optionsButton)
        }
        sessionLabel.isVisible = false
        add(title, BorderLayout.CENTER)
        add(actions, BorderLayout.EAST)
    }

    fun setSessionStatus(text: String) {
        sessionLabel.text = text
        sessionLabel.isVisible = text != "Ready" && !text.startsWith("session=")
        toolTipText = text
    }
}
