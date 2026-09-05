package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory

class ModelSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    private val popupController = SelectorPopupController(this)
    private var models: List<ModelOption> = emptyList()
    private var lastManualModelId: String? = settings.selectedModel.takeUnless { it == "auto" || it.isEmpty() }

    init {
        showsChevron = true
        isEnabled = false
        text = "Loading models…"
        toolTipText = text
        addActionListener {
            popupController.toggle {
                var popup: JBPopup? = null
                val content = ModelPopupPanel(models, settings.selectedModel, lastManualModelId, onSelect = { option ->
                    settings.selectedModel = option.id
                    if (option.id != "auto") lastManualModelId = option.id
                    showSelection(option)
                }, onClose = { popup?.cancel() }, onResize = {
                    popupController.repackAbove()
                })
                popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content.searchField)
                    .setFocusable(true)
                    .setRequestFocus(true)
                    .setCancelOnClickOutside(true)
                    .setCancelOnWindowDeactivation(true)
                    .setCancelOnOtherWindowOpen(true)
                    .setCancelKeyEnabled(true)
                    .createPopup()
                popup.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
                    override fun beforeShown(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                        content.modelList.ensureIndexIsVisible(content.modelList.selectedIndex)
                    }
                })
                popup
            }
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
