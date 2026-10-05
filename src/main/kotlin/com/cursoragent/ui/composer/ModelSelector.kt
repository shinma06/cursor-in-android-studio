package com.cursoragent.ui.composer

import com.cursoragent.service.ModelCatalogState
import com.cursoragent.service.ModelOption
import com.cursoragent.service.AgentEvent
import com.cursoragent.settings.AgentSettingsState
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory

class ModelSelector(
    private val settings: AgentSettingsState = AgentSettingsState.getInstance(),
) : SelectorButton() {
    private val popupController = SelectorPopupController(this, ownsChildPopups = true)
    private var acpPopup: JBPopup? = null
    private var models: List<ModelOption> = emptyList()
    private var acp = false
    internal var acpConfiguration: AgentEvent.Configuration? = null
        private set
    internal var providerConfiguration: AgentEvent.Configuration? = null
        private set
    internal var onConfigure: (AgentEvent.Configuration, String, String) -> Unit = { _, _, _ -> }
    internal var parameterValues: Map<String, String> = emptyMap()
        private set
    var onRetry: () -> Unit = {}
    private var catalog: ModelCatalogState = ModelCatalogState.Loading
    private var printModelId: String? = null
    private var families: List<ModelFamily> = emptyList()
    private var lastManualModelId: String? = settings.selectedModel.takeUnless { it == "auto" || it.isEmpty() }

    init {
        showsChevron = true
        addHierarchyListener { if (!isShowing) closePopup() }
        showCatalog(ModelCatalogState.Loading)
        addActionListener {
            if (!acp && (
                    catalog == ModelCatalogState.NotRequested || catalog == ModelCatalogState.Failed ||
                        (catalog as? ModelCatalogState.Loaded)?.models?.isEmpty() == true
                    )) {
                onRetry()
                return@addActionListener
            }
            if (acp) {
                val state = acpConfiguration ?: return@addActionListener
                acpPopup?.takeUnless { it.isDisposed }?.let {
                    closePopup()
                    return@addActionListener
                }
                val content = AcpModelOptionsPanel(state) { id, value ->
                    closePopup()
                    if (isEnabled && acpConfiguration === state) onConfigure(state, id, value)
                }
                // Let the native popup own ComboBox drop-downs; the print picker owns separate custom children.
                val next = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content.selectors.values.firstOrNull { it.isEnabled })
                    .setFocusable(true).setRequestFocus(true).setCancelOnOtherWindowOpen(false).createPopup()
                acpPopup = next
                next.show(com.intellij.openapi.ui.popup.PopupShowOptions.aboveComponent(this).withPopupComponentUnscaledGap(4))
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

    internal fun cycleParameter() {
        val state = acpConfiguration ?: return
        val parameter = state.parameters.firstOrNull { it.options.size > 1 } ?: return
        val index = parameter.options.indexOfFirst { it.id == parameter.currentValue }
        if (index >= 0 && isEnabled) onConfigure(state, parameter.id, parameter.options[(index + 1) % parameter.options.size].id)
    }

    fun closePopup() {
        popupController.close()
        acpPopup?.cancel()
        acpPopup = null
    }

    override fun createToolTip() = super.createToolTip().apply { putClientProperty("html.disable", true) }

    internal fun restoreSelection(id: String, configuration: AgentEvent.Configuration? = null,
        parameters: Map<String, String> = configuration?.parameterValues().orEmpty()) {
        // A saved draft is local selection, not confirmation that the resident provider still uses it.
        acpConfiguration = configuration?.takeIf { it.model == id && it.parameterValues() == parameters }
        parameterValues = parameters.toMap()
        settings.selectedModel = id
        showSelection((acpConfiguration?.models ?: models).firstOrNull { it.id == id } ?: ModelOption(id, id.ifEmpty { "既定モデル" }))
        if (acp) isEnabled = acpConfiguration != null && providerConfiguration != null
    }

    fun waitForAcp() {
        if (!acp) printModelId = settings.selectedModel
        settings.selectedModel = ""
        acp = true
        acpConfiguration = null
        parameterValues = emptyMap()
        providerConfiguration = null
        models = emptyList()
        families = emptyList()
        text = "ACPの既定モデル"
        toolTipText = "初回送信時に接続先の設定を確認します。現在のCLIモデル選択は引き継ぎません。"
        getAccessibleContext().accessibleName = toolTipText
        isEnabled = false
    }

    fun setAcpModels(models: List<ModelOption>, selected: String) {
        setAcpConfiguration(AgentEvent.Configuration("agent", selected, models))
    }

    internal fun setAcpConfiguration(state: AgentEvent.Configuration?, preserveDraft: Boolean = false) {
        closePopup()
        providerConfiguration = state
        if (preserveDraft) return
        acpConfiguration = state
        parameterValues = state?.parameterValues().orEmpty()
        if (state == null) {
            models = emptyList()
            isEnabled = false
            text = "ACPのモデル設定を確認中…"
            toolTipText = "接続先の設定を確認できるまでモデルを変更できません。"
            return
        }
        val models = state.models
        val selected = state.model
        if (!acp) printModelId = settings.selectedModel
        acp = true
        showsChevron = models.isNotEmpty()
        this.models = models
        families = emptyList()
        settings.selectedModel = selected
        showSelection(models.firstOrNull { it.id == selected } ?: ModelOption(selected, selected))
        isEnabled = models.isNotEmpty()
    }

    fun setModels(models: List<ModelOption>) = showCatalog(ModelCatalogState.Loaded(models))

    fun showCatalog(state: ModelCatalogState) {
        if (acp) {
            settings.selectedModel = printModelId.orEmpty()
            printModelId = null
        }
        acp = false
        acpConfiguration = null
        parameterValues = emptyMap()
        providerConfiguration = null
        catalog = state
        showsChevron = state is ModelCatalogState.Loaded && state.models.isNotEmpty()
        when (state) {
            ModelCatalogState.NotRequested -> showCatalogStatus("モデル一覧を取得", retry = true)
            ModelCatalogState.Loading -> showCatalogStatus("モデルを取得中…", retry = false)
            ModelCatalogState.Failed -> showCatalogStatus("モデル取得に失敗 · 再試行", retry = true)
            is ModelCatalogState.Loaded -> {
                models = state.models
                families = modelFamilies(models)
                if (models.isEmpty()) {
                    showCatalogStatus("モデルなし · 再試行", retry = true)
                } else {
                    val saved = settings.selectedModel
                    val selected = models.find { it.id == saved } ?: ModelOption(saved, saved.ifEmpty { "既定モデル" })
                    if (saved.isNotEmpty() && saved != "auto") lastManualModelId = saved
                    showSelection(selected)
                    if (models.none { it.id == saved }) toolTipText += " — 保存済み選択を保持（今回の一覧にはありません）"
                    isEnabled = true
                }
            }
        }
    }

    private fun showCatalogStatus(label: String, retry: Boolean) {
        text = label
        val saved = settings.selectedModel.ifEmpty { "既定モデル" }
        toolTipText = "$label。選択を保持: $saved" + if (retry) "。クリックまたはSpaceで取得します。" else ""
        getAccessibleContext().accessibleName = toolTipText
        isEnabled = retry
        revalidate()
        repaint()
    }

    private fun showSelection(option: ModelOption) {
        val family = families.find { it.variants.any { variant -> variant.option.id == option.id } }
        val variant = family?.variants?.find { it.option.id == option.id }
        text = if (family != null && variant != null) family.selectionLabel(variant) else option.displayName()
        val parameters = acpConfiguration?.parameters.orEmpty().joinToString(" / ") { parameter ->
            "${parameter.name}: ${parameter.options.firstOrNull { it.id == parameter.currentValue }?.label ?: parameter.currentValue}"
        }
        toolTipText = "${option.label} — ${option.id}" + if (parameters.isEmpty()) "" else " — $parameters"
        getAccessibleContext().accessibleName = "Model: ${option.label}" + if (parameters.isEmpty()) "" else ", $parameters"
        revalidate()
        repaint()
    }
}
