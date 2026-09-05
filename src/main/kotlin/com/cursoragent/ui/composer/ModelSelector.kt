package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory

class ModelSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    private var models: List<ModelOption> = emptyList()
    private var lastManualModelId: String? = settings.selectedModel.takeUnless { it == "auto" || it.isEmpty() }

    init {
        showsChevron = true
        isEnabled = false
        text = "Loading models…"
        toolTipText = text
        addActionListener {
            var popup: JBPopup? = null
            val content = ModelPopupPanel(models, settings.selectedModel, lastManualModelId, onSelect = { option ->
                settings.selectedModel = option.id
                if (option.id != "auto") lastManualModelId = option.id
                showSelection(option)
            }, onClose = { popup?.cancel() }, onResize = {
                popup?.pack(true, true)
                popup?.moveToFitScreen()
            })
            popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content.searchField)
                .setFocusable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .setCancelKeyEnabled(true)
                .createPopup()
            popup.showUnderneathOf(this)
            content.modelList.ensureIndexIsVisible(content.modelList.selectedIndex)
        }
    }

    fun setModels(models: List<ModelOption>) {
        this.models = models
        if (models.isEmpty()) {
            isEnabled = false
            text = "Default model"
            toolTipText = "No models available (agent --list-models failed)"
            getAccessibleContext().accessibleName = toolTipText
            return
        }
        val selected = models.find { it.id == settings.selectedModel } ?: models.first()
        settings.selectedModel = selected.id
        if (selected.id != "auto") lastManualModelId = selected.id
        showSelection(selected)
        isEnabled = true
    }

    private fun showSelection(option: ModelOption) {
        text = option.displayName()
        toolTipText = "${option.label} — ${option.id}"
        getAccessibleContext().accessibleName = "Model: ${option.label}"
        revalidate()
        repaint()
    }
}
