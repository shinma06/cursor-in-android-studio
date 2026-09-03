package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiColors
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu

class ComposerPanel : JPanel(BorderLayout()) {
    var onSend: (String) -> Unit = {}

    val inputArea = JBTextArea(4, 20).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8)
    }

    private val placeholderLabel = JLabel("Plan, @ for context").apply {
        foreground = AgentUiColors.mutedText
        border = JBUI.Borders.empty(8)
    }

    private val sendButton = JButton(AllIcons.Actions.Upload).apply {
        toolTipText = "Send (Enter)"
        preferredSize = Dimension(JBUI.scale(32), JBUI.scale(32))
    }

    val modeSelector = ModeSelector()
    val modelSelector = ModelSelector()

    /** Reserved for @mention chips, diff review bar, etc. */
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
            add(placeholderLabel, BorderLayout.NORTH)
            add(JBScrollPane(inputArea).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }

        updatePlaceholderVisibility()
        inputArea.addCaretListener { updatePlaceholderVisibility() }
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    submit()
                }
            }
        })

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
        updatePlaceholderVisibility()
    }

    fun inputText(): String = inputArea.text.trim()

    private fun submit() {
        val text = inputText()
        if (text.isNotEmpty()) {
            onSend(text)
        }
    }

    private fun updatePlaceholderVisibility() {
        placeholderLabel.isVisible = inputArea.text.isEmpty()
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
