package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.RoundedSurface
import com.cursoragent.ui.composer.mention.MentionPopupController
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.KeyStroke

class ComposerPanel(private val project: Project) : JPanel(BorderLayout()) {
    var onSend: (String) -> Unit = {}
    var onStop: () -> Unit = {}
    var onRunningChanged: (Boolean) -> Unit = {}
    private var isRunning = false

    val contextUsage = com.cursoragent.ui.composer.context.ContextUsageView()

    val inputArea = GrowingPromptField(project)

    private val mentionPopupController = MentionPopupController(project, inputArea)

    private val sendButton = SelectorButton().apply {
        text = "↑"
        horizontalAlignment = javax.swing.SwingConstants.CENTER
        toolTipText = "送信（Enter）"
        preferredSize = JBUI.size(24, 24)
        // Reserve 18px for the icon instead of the selector's 12px text area.
        border = JBUI.Borders.empty(3)
        font = font.deriveFont(font.size2D * 4f / 3f)
        isBorderPainted = false
        isContentAreaFilled = false
        margin = JBUI.emptyInsets()
        accessibleContext.accessibleName = "送信（Enter）"
    }

    // Detached selector state: application settings supply defaults only for a new tab.
    val selection = com.cursoragent.settings.AgentSettingsState().apply {
        val defaults = com.cursoragent.settings.AgentSettingsState.getInstance()
        mode = defaults.mode
        selectedModel = defaults.selectedModel
    }
    val modeSelector = ModeSelector(selection)
    val modelSelector = ModelSelector(selection)

    /** Reserved for diff review bar etc. */
    val accessoryPanel = JPanel(BorderLayout()).apply {
        isVisible = false
        isOpaque = false
    }

    init {
        border = JBUI.Borders.empty(5, 12, 8, 12)
        isOpaque = false

        val inputWrapper = RoundedSurface(AgentUiColors.composerBackground).apply {
            border = AgentUiColors.RoundedBorder()
            add(inputArea, BorderLayout.CENTER)
        }

        mentionPopupController.install()

        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = submit()
        }.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)), inputArea)

        sendButton.addActionListener { if (isRunning) onStop() else submit() }

        val controls = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 6, 6)
            val selectors = JPanel(SelectorRowLayout()).apply {
                isOpaque = false
                add(modeSelector)
                add(modelSelector)
            }
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(contextUsage.button)
                add(sendButton)
            }
            add(selectors, BorderLayout.CENTER)
            add(actions, BorderLayout.EAST)
        }
        inputWrapper.add(controls, BorderLayout.SOUTH)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(accessoryPanel, BorderLayout.NORTH)
            add(contextUsage.panel, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(inputWrapper, BorderLayout.CENTER)
    }

    fun setInputEnabled(enabled: Boolean) {
        // sendButton is intentionally left enabled here -- setRunning() repurposes
        // it as a Stop button while a turn is in flight, so it must stay clickable.
        inputArea.isEnabled = enabled
        modeSelector.isEnabled = enabled
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        onRunningChanged(running)
        sendButton.text = if (running) "" else "↑"
        sendButton.icon = if (running) StopIcon else null
        sendButton.toolTipText = if (running) "停止" else "送信（Enter）"
        sendButton.accessibleContext.accessibleName = sendButton.toolTipText
    }

    fun clearInput() {
        inputArea.text = ""
    }

    fun inputText(): String = inputArea.text.trim()

    private fun submit() {
        if (isRunning) return
        val text = inputText()
        if (text.isNotEmpty()) {
            onSend(text)
        }
    }
}
