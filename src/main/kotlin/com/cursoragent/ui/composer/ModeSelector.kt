package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import javax.swing.DefaultListCellRenderer

class ModeSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    init {
        preferredSize = JBUI.size(82, 26)
        refreshLabel()
        addActionListener {
            val renderer = DefaultListCellRenderer()
            JBPopupFactory.getInstance()
                .createPopupChooserBuilder(AgentMode.entries.toList())
                .setRenderer { list, value, index, selected, focus ->
                    renderer.getListCellRendererComponent(list, label(value), index, selected, focus)
                }
                .setItemChosenCallback { mode ->
                    settings.mode = mode
                    refreshLabel()
                }
                .createPopup()
                .showUnderneathOf(this)
        }
    }

    private fun refreshLabel() {
        val name = label(settings.mode)
        text = "$name  ⌄"
        toolTipText = "Mode: $name"
        getAccessibleContext().accessibleName = toolTipText
    }

    private fun label(mode: AgentMode): String = when (mode) {
        AgentMode.ASK -> "Ask"
        AgentMode.AGENT -> "Agent"
        AgentMode.PLAN -> "Plan"
    }
}
