package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.ImmediateEditNotice
import com.cursoragent.ui.RoundedSurface
import com.cursoragent.ui.composer.mention.MentionPopupController
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke

class ComposerPanel(private val project: Project) : JPanel(BorderLayout()) {
    var onSend: (String) -> Unit = {}
    var onStop: () -> Unit = {}
    private var isRunning = false

    val inputArea = GrowingPromptField(project)

    private val mentionPopupController = MentionPopupController(project, inputArea)

    private val sendButton = SelectorButton().apply {
        text = "↑"
        horizontalAlignment = javax.swing.SwingConstants.CENTER
        toolTipText = "Send (Enter)"
        preferredSize = JBUI.size(28, 28)
        font = font.deriveFont(18f)
        isBorderPainted = false
        isContentAreaFilled = false
        margin = JBUI.emptyInsets()
        accessibleContext.accessibleName = "Send (Enter)"
    }

    val modeSelector = ModeSelector()
    val modelSelector = ModelSelector()

    /** Reserved for diff review bar etc. */
    val accessoryPanel = JPanel(BorderLayout()).apply {
        isVisible = false
        isOpaque = false
    }

    init {
        border = JBUI.Borders.empty(6, 16, 10, 16)
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
            border = JBUI.Borders.empty(0, 8, 8, 8)
            val selectors = JPanel(SelectorRowLayout()).apply {
                isOpaque = false
                add(modeSelector)
                add(modelSelector)
            }
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(createOverflowButton())
                add(sendButton)
            }
            add(selectors, BorderLayout.CENTER)
            add(actions, BorderLayout.EAST)
        }
        inputWrapper.add(controls, BorderLayout.SOUTH)
        add(accessoryPanel, BorderLayout.NORTH)
        add(inputWrapper, BorderLayout.CENTER)
        add(ImmediateEditNotice().apply {
            foreground = AgentUiColors.mutedText
            font = font.deriveFont(font.size2D - 1f)
            border = JBUI.Borders.empty(6, 2, 0, 2)
        }, BorderLayout.SOUTH)
    }

    fun setInputEnabled(enabled: Boolean) {
        // sendButton is intentionally left enabled here -- setRunning() repurposes
        // it as a Stop button while a turn is in flight, so it must stay clickable.
        inputArea.isEnabled = enabled
        modeSelector.isEnabled = enabled
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        sendButton.text = if (running) "■" else "↑"
        sendButton.toolTipText = if (running) "Stop" else "Send (Enter)"
        sendButton.accessibleContext.accessibleName = sendButton.toolTipText
    }

    fun clearInput() {
        inputArea.text = ""
    }

    fun inputText(): String = inputArea.text.trim()

    private fun submit() {
        val text = inputText()
        if (text.isNotEmpty()) {
            onSend(text)
        }
    }

    private fun createOverflowButton(): JButton {
        return SelectorButton().apply {
            text = "⋯"
            horizontalAlignment = javax.swing.SwingConstants.CENTER
            toolTipText = "More options"
            margin = JBUI.emptyInsets()
            preferredSize = JBUI.size(26, 28)
            isBorderPainted = false
            isContentAreaFilled = false
            foreground = AgentUiColors.mutedText
            addActionListener {
                JPopupMenu().apply {
                    val settings = AgentSettingsState.getInstance()
                    com.cursoragent.settings.PermissionMode.entries.forEach { mode ->
                        add(
                            JCheckBoxMenuItem(mode.label, settings.permissionMode == mode).apply {
                                addActionListener { settings.permissionMode = mode }
                            },
                        )
                    }
                    addSeparator()
                    com.cursoragent.settings.SandboxMode.entries.forEach { mode ->
                        add(
                            JCheckBoxMenuItem(mode.label, settings.sandboxMode == mode).apply {
                                addActionListener { settings.sandboxMode = mode }
                            },
                        )
                    }
                    addSeparator()
                    com.cursoragent.settings.WorktreeMode.entries.forEach { mode ->
                        add(
                            JCheckBoxMenuItem(mode.label, settings.worktreeMode == mode).apply {
                                addActionListener { settings.worktreeMode = mode }
                            },
                        )
                    }
                    addSeparator()
                    add(
                        javax.swing.JMenuItem("Summarize context").apply {
                            addActionListener { onSend("/summarize") }
                        },
                    )
                    add(
                        javax.swing.JMenuItem("MCP Servers…").apply {
                            addActionListener { com.cursoragent.ui.mcp.McpServersDialog(project).show() }
                        },
                    )
                }.show(this, 0, height)
            }
        }
    }
}
