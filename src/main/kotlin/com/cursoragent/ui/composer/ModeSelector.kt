package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class ModeSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    private val popupController = SelectorPopupController(this)

    init {
        showsChevron = true
        refreshLabel()
        addActionListener {
            popupController.toggle {
                val renderer = DefaultListCellRenderer()
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(listOf(AgentMode.AGENT, AgentMode.PLAN, AgentMode.ASK))
                    .setSelectedValue(settings.mode, true)
                    .setRequestFocus(true)
                    .setCancelOnClickOutside(true)
                    .setCancelOnWindowDeactivation(true)
                    .setCancelOnOtherWindowOpen(true)
                    .setCancelKeyEnabled(true)
                    .setRenderer { list, value, index, selected, focus ->
                        val label = renderer.getListCellRendererComponent(list, label(value), index, selected, focus) as JLabel
                        label.font = AgentUiMetrics.textFont()
                        label.background = if (selected) AgentUiColors.userBubbleBackground else AgentUiColors.panelBackground
                        label.foreground = UIManager.getColor("Label.foreground")
                        label.icon = ModeIcon(value)
                        label.iconTextGap = JBUI.scale(9)
                        JPanel(BorderLayout()).apply {
                            getAccessibleContext().accessibleName = label(value)
                            background = label.background
                            foreground = label.foreground
                            border = JBUI.Borders.empty(5, 8)
                            preferredSize = JBUI.size(190, 28)
                            add(label, BorderLayout.CENTER)
                            add(JLabel(if (value == settings.mode) "✓" else "").apply {
                                foreground = label.foreground
                            }, BorderLayout.EAST)
                        }
                    }
                    .setItemChosenCallback(::selectMode)
                    .createPopup()
            }
        }
    }

    internal fun selectMode(mode: AgentMode) {
        settings.mode = mode
        refreshLabel()
    }

    private fun refreshLabel() {
        val name = label(settings.mode)
        text = name
        icon = ModeIcon(settings.mode)
        foreground = when (settings.mode) {
            AgentMode.PLAN -> JBColor(Color(0x865000), Color(0xF2B45F))
            AgentMode.ASK -> JBColor(Color(0x187343), Color(0x43AD72))
            AgentMode.AGENT -> UIManager.getColor("Label.foreground")
        }
        pillColor = when (settings.mode) {
            AgentMode.PLAN -> JBColor(Color(0xF8E8CF), Color(0x514330))
            AgentMode.ASK -> JBColor(Color(0xDCF0E3), Color(0x293F32))
            AgentMode.AGENT -> JBColor(Color(0xE3E3E3), Color(0x383838))
        }
        toolTipText = "Mode: $name"
        getAccessibleContext().accessibleName = toolTipText
        revalidate()
        repaint()
    }

    private fun label(mode: AgentMode): String = when (mode) {
        AgentMode.ASK -> "Ask"
        AgentMode.AGENT -> "Agent"
        AgentMode.PLAN -> "Plan"
    }
}
