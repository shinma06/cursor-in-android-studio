package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import javax.swing.DefaultListCellRenderer

class ModeSelector : SelectorButton() {
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
                    AgentSettingsState.getInstance().mode = mode
                    refreshLabel()
                }
                .createPopup()
                .showUnderneathOf(this)
        }
    }

    private fun refreshLabel() {
        val name = label(AgentSettingsState.getInstance().mode)
        text = "$name  ⌄"
        toolTipText = "Mode: $name"
        accessibleContext.accessibleName = toolTipText
    }

    private fun label(mode: AgentMode): String = when (mode) {
        AgentMode.ASK -> "Ask"
        AgentMode.AGENT -> "Agent"
        AgentMode.PLAN -> "Plan"
    }
}
