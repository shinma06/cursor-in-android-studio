package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentSettingsConfigurable
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.RoundedSurface
import com.cursoragent.ui.composer.mention.MentionPopupController
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke

class ComposerPanel(private val project: Project) : JPanel(BorderLayout()) {
    var onSend: (String) -> Unit = {}
    var onStop: () -> Unit = {}
    private var isRunning = false
    private var optionsPanel: ComposerOptionsPanel? = null

    val contextUsage = com.cursoragent.ui.composer.context.ContextUsageView()

    val inputArea = GrowingPromptField(project)

    private val mentionPopupController = MentionPopupController(project, inputArea)

    private val sendButton = SelectorButton().apply {
        text = "↑"
        horizontalAlignment = javax.swing.SwingConstants.CENTER
        toolTipText = "送信（Enter）"
        preferredSize = JBUI.size(24, 24)
        font = font.deriveFont(font.size2D * 4f / 3f)
        isBorderPainted = false
        isContentAreaFilled = false
        margin = JBUI.emptyInsets()
        accessibleContext.accessibleName = "送信（Enter）"
    }

    val modeSelector = ModeSelector()
    val modelSelector = ModelSelector()

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
                add(createOverflowButton())
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
        optionsPanel?.setRunning(running)
        sendButton.text = if (running) "■" else "↑"
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

    private fun createOverflowButton(): JButton {
        return SelectorButton().apply {
            text = "⋯"
            horizontalAlignment = javax.swing.SwingConstants.CENTER
            toolTipText = "チャット設定"
            margin = JBUI.emptyInsets()
            preferredSize = JBUI.size(24, 24)
            isBorderPainted = false
            isContentAreaFilled = false
            foreground = AgentUiColors.mutedText
            addActionListener {
                var popup: JBPopup? = null
                val content = ComposerOptionsPanel(
                    settings = AgentSettingsState.getInstance(),
                    isRunning = isRunning,
                    onSummarize = { if (!isRunning) onSend("/summarize") },
                    onMcp = { com.cursoragent.ui.mcp.McpServersDialog(project).show() },
                    onSettings = { ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java) },
                    onClose = { popup?.cancel() },
                )
                popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content.permissionChoice)
                    .setFocusable(true)
                    .setRequestFocus(true)
                    .setCancelOnClickOutside(true)
                    .setCancelKeyEnabled(true)
                    .createPopup()
                optionsPanel = content
                popup.addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                        if (optionsPanel === content) optionsPanel = null
                    }
                })
                popup.showUnderneathOf(this)
            }
        }
    }
}
