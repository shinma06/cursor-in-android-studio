package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.mention.MentionPopupController
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke

class ComposerPanel(project: Project) : JPanel(BorderLayout()) {
    var onSend: (String) -> Unit = {}

    val inputArea = object : EditorTextField(project, PlainTextFileType.INSTANCE) {
        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.settings.isUseSoftWraps = true
            editor.settings.isLineNumbersShown = false
            editor.setVerticalScrollbarVisible(false)
            editor.setHorizontalScrollbarVisible(false)
            return editor
        }
    }.apply {
        setOneLineMode(false)
        setPlaceholder("Plan, @ for context")
        border = JBUI.Borders.empty(8)
        preferredSize = Dimension(preferredSize.width, JBUI.scale(88))
    }

    private val mentionPopupController = MentionPopupController(project, inputArea)

    private val sendButton = JButton(AllIcons.Actions.Upload).apply {
        toolTipText = "Send (Enter)"
        preferredSize = Dimension(JBUI.scale(32), JBUI.scale(32))
    }

    val modeSelector = ModeSelector()
    val modelSelector = ModelSelector()

    /** Reserved for diff review bar etc. */
    val accessoryPanel = JPanel(BorderLayout()).apply {
        isVisible = false
        isOpaque = false
    }

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(8, 12, 12, 12),
        )
        isOpaque = false

        val inputWrapper = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                LineBorderRounded(JBColor.border()),
                JBUI.Borders.empty(),
            )
            background = JBColor.namedColor("TextField.background", JBColor.background())
            isOpaque = true
            add(inputArea, BorderLayout.CENTER)
        }

        mentionPopupController.install()

        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = submit()
        }.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)), inputArea)

        sendButton.addActionListener { submit() }

        val controls = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)

            val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(modeSelector)
                add(modelSelector)
                add(createOverflowButton())
            }

            add(leftControls, BorderLayout.WEST)
            add(sendButton, BorderLayout.EAST)
        }

        add(accessoryPanel, BorderLayout.NORTH)
        add(inputWrapper, BorderLayout.CENTER)
        add(controls, BorderLayout.SOUTH)
    }

    fun setInputEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        inputArea.isEnabled = enabled
        modeSelector.isEnabled = enabled
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
        return JButton("⋯").apply {
            toolTipText = "More options"
            margin = JBUI.insets(2, 6, 2, 6)
            addActionListener {
                JPopupMenu().apply {
                    add(
                        JCheckBoxMenuItem("Force (auto-approve)", AgentSettingsState.getInstance().forceEnabled).apply {
                            addActionListener {
                                AgentSettingsState.getInstance().forceEnabled = isSelected
                            }
                        },
                    )
                }.show(this, 0, height)
            }
        }
    }

    /** Simple line border wrapper; avoids custom LAF. */
    private class LineBorderRounded(color: java.awt.Color) : javax.swing.border.LineBorder(color, 1, true)
}
