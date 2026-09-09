package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory

class ModelSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    private val popupController = SelectorPopupController(this, ownsChildPopups = true)
    private var models: List<ModelOption> = emptyList()
    private var acp = false
    private var families: List<ModelFamily> = emptyList()
    private var lastManualModelId: String? = settings.selectedModel.takeUnless { it == "auto" || it.isEmpty() }

    init {
        showsChevron = true
        isEnabled = false
        text = "Loading models…"
        toolTipText = text
        addActionListener {
            if (acp) {
                popupController.toggle {
                    JBPopupFactory.getInstance().createPopupChooserBuilder(models)
                        .setRenderer { _, option, _, selected, _ ->
                            javax.swing.JLabel(option.label).apply {
                                putClientProperty("html.disable", true)
                                border = com.intellij.util.ui.JBUI.Borders.empty(6, 8)
                                isOpaque = true
                                background = if (selected) com.cursoragent.ui.AgentUiColors.userBubbleBackground else com.cursoragent.ui.AgentUiColors.panelBackground
                                toolTipText = option.id
                            }
                        }
                        .setItemChosenCallback { option ->
                            settings.selectedModel = option.id
                            showSelection(option)
                            toolTipText = "次の送信で適用: ${option.label} — ${option.id}"
                        }.createPopup()
                }
                return@addActionListener
            }
            popupController.toggle {
                var popup: JBPopup? = null
                val content = ModelOptionsPopupPanel(models, settings.selectedModel, lastManualModelId, onSelect = { option ->
                    settings.selectedModel = option.id
                    if (option.id != "auto") lastManualModelId = option.id
                    showSelection(option)
                }, onClose = { popup?.cancel() }, onResize = {
                    popupController.repackAbove()
                }, onShowModels = { picker, requestFocus ->
                    popupController.showChild(picker, picker.searchField, requestFocus) { picker.actionMap.get("cancel").actionPerformed(null) }
                }, onHideModels = { popupController.closeChild() }, onModelsResize = { popupController.repackChild() })
                popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content.focusTarget)
                    .setFocusable(true)
                    .setRequestFocus(true)
                    .setCancelOnClickOutside(false)
                    .setCancelOnWindowDeactivation(true)
                    .setCancelOnOtherWindowOpen(false)
                    .setCancelKeyEnabled(false)
                    .createPopup()
                popup.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
                    override fun beforeShown(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                        content.picker?.modelList?.let { it.ensureIndexIsVisible(it.selectedIndex) }
                    }
                })
                popup
            }
        }
    }

    fun waitForAcp() {
        acp = true
        models = emptyList()
        families = emptyList()
        text = "ACPの既定モデル"
        toolTipText = "初回送信時に接続先の設定を確認します。現在のCLIモデル選択は引き継ぎません。"
        accessibleContext.accessibleName = toolTipText
        isEnabled = false
    }

    fun setAcpModels(models: List<ModelOption>, selected: String) {
        acp = true
        this.models = models
        families = emptyList()
        settings.selectedModel = selected
        showSelection(models.firstOrNull { it.id == selected } ?: ModelOption(selected, selected))
        isEnabled = models.isNotEmpty()
    }

    fun setModels(models: List<ModelOption>) {
        acp = false
        this.models = models
        families = modelFamilies(models)
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
        val family = families.find { it.variants.any { variant -> variant.option.id == option.id } }
        val variant = family?.variants?.find { it.option.id == option.id }
        text = if (family != null && variant != null) family.selectionLabel(variant) else option.displayName()
        toolTipText = "${option.label} — ${option.id}"
        getAccessibleContext().accessibleName = "Model: ${option.label}"
        revalidate()
        repaint()
    }
}
