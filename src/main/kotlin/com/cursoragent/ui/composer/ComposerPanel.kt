package com.cursoragent.ui.composer

import com.cursoragent.service.AgentEvent
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
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
    var onEnqueue: (String) -> Unit = {}
    var onShowQueue: () -> Unit = {}
    var isRunning = false
        private set
    private var acp = false

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

    private val enqueueButton = javax.swing.JButton("予約に追加").apply {
        isVisible = false
        toolTipText = "入力を次のターンに予約します。mode/modelは登録時、contextと実行設定は送信開始時です。"
        addActionListener { if (isRunning && inputArea.isEnabled) inputText().takeIf { it.isNotBlank() }?.let(onEnqueue) }
    }
    private val queueButton = javax.swing.JButton().apply {
        isVisible = false
        toolTipText = "予約を一時停止して一覧・編集・削除・順序を確認します。"
        addActionListener { onShowQueue() }
    }

    fun showQueueState(count: Int, paused: Boolean) {
        queueButton.text = "予約 $count 件" + if (paused) "（停止中）" else ""
        queueButton.isVisible = count > 0
        accessoryPanel.isVisible = isRunning || count > 0
        revalidate()
        repaint()
    }

    // Detached selector state: application settings supply defaults only for a new tab.
    val selection = AgentSettingsState().apply {
        val defaults = AgentSettingsState.getInstance()
        mode = defaults.mode
        selectedModel = defaults.selectedModel
    }
    val modeSelector = ModeSelector(selection)
    val modelSelector = ModelSelector(selection)

    /** Transient queue actions live above the input to preserve the compact selector row. */
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

        accessoryPanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(queueButton)
            add(enqueueButton)
        })
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

    fun useAcp() {
        acp = true
        selection.selectedModel = ""
        modelSelector.waitForAcp()
    }

    fun usePrint() { acp = false }

    fun showAcpConfiguration(state: AgentEvent.Configuration) {
        val mode = AgentMode.entries.firstOrNull { it.name.lowercase() == state.mode } ?: return
        modeSelector.selectMode(mode)
        modelSelector.setAcpModels(state.models, state.model)
        modeSelector.isEnabled = !isRunning
        modelSelector.isEnabled = !isRunning && state.models.isNotEmpty()
    }

    fun setInputEnabled(enabled: Boolean) {
        // sendButton is intentionally left enabled here -- setRunning() repurposes
        // it as a Stop button while a turn is in flight, so it must stay clickable.
        inputArea.isEnabled = enabled
        modeSelector.isEnabled = enabled
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        enqueueButton.isVisible = running
        accessoryPanel.isVisible = running || queueButton.isVisible
        if (acp) {
            modeSelector.isEnabled = !running
            modelSelector.isEnabled = !running && selection.selectedModel.isNotEmpty()
        }
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
