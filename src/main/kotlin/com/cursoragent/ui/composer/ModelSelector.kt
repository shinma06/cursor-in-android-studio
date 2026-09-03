package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentSettingsState
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JList

class ModelSelector : JComboBox<ModelOption>() {
    init {
        isEnabled = false
        toolTipText = "Loading models…"
        preferredSize = JBUI.size(160, 28)
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? ModelOption)?.label ?: "Default model"
                return component
            }
        }
        addActionListener {
            (selectedItem as? ModelOption)?.let { AgentSettingsState.getInstance().selectedModel = it.id }
        }
    }

    fun setModels(models: List<ModelOption>) {
        if (models.isEmpty()) {
            isEnabled = false
            toolTipText = "No models available (agent --list-models failed)"
            return
        }

        val settings = AgentSettingsState.getInstance()
        model = DefaultComboBoxModel(models.toTypedArray())
        selectedItem = models.find { it.id == settings.selectedModel } ?: models.first()
        isEnabled = true
        toolTipText = null
    }
}
