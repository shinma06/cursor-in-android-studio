package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.DefaultListCellRenderer

class ModelSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    private var models: List<ModelOption> = emptyList()

    init {
        isEnabled = false
        text = "Loading models…"
        toolTipText = text
        addActionListener {
            val renderer = DefaultListCellRenderer()
            JBPopupFactory.getInstance()
                .createPopupChooserBuilder(models)
                .setTitle("Model")
                .setNamerForFiltering { it.label }
                .setRenderer { list, value, index, selected, focus ->
                    renderer.getListCellRendererComponent(list, value.label, index, selected, focus)
                }
                .setItemChosenCallback { option ->
                    settings.selectedModel = option.id
                    showSelection(option)
                }
                .createPopup()
                .showUnderneathOf(this)
        }
    }

    fun setModels(models: List<ModelOption>) {
        this.models = models
        if (models.isEmpty()) {
            isEnabled = false
            text = "Default model"
            toolTipText = "No models available (agent --list-models failed)"
            return
        }
        val selected = models.find { it.id == settings.selectedModel } ?: models.first()
        settings.selectedModel = selected.id
        showSelection(selected)
        isEnabled = true
    }

    private fun showSelection(option: ModelOption) {
        val name = option.label.replace(" (current, default)", "")
            .replace(" (default)", "").replace(" (current)", "")
        text = "$name  ⌄"
        toolTipText = "${option.label} — ${option.id}"
        getAccessibleContext().accessibleName = "Model: ${option.label}"
    }
}
