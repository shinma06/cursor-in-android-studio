package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.intellij.util.ui.JBUI
import javax.swing.JComboBox

class ModeSelector : JComboBox<AgentMode>(AgentMode.entries.toTypedArray()) {
    init {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = when (value as? AgentMode) {
                    AgentMode.ASK -> "Ask"
                    AgentMode.AGENT -> "Agent"
                    AgentMode.PLAN -> "Plan"
                    null -> ""
                }
                return component
            }
        }

        selectedItem = AgentSettingsState.getInstance().mode
        addActionListener {
            AgentSettingsState.getInstance().mode = selectedItem as AgentMode
        }
        preferredSize = JBUI.size(90, 28)
    }
}
