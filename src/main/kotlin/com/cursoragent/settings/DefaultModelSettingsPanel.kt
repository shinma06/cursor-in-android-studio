package com.cursoragent.settings

import com.cursoragent.service.ModelCatalogState
import com.cursoragent.service.modelCatalogResult
import com.cursoragent.service.runAgentMetadataCommand
import com.cursoragent.ui.ModelCatalogLoader
import com.cursoragent.ui.composer.ModelSelector
import com.intellij.openapi.application.ApplicationManager
import java.awt.BorderLayout
import java.util.concurrent.Future
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** A settings edit owns its draft and catalog request; only Apply writes application defaults. */
internal class DefaultModelSettingsPanel(
    saved: AgentSettingsState,
    fetch: () -> ModelCatalogState = {
        modelCatalogResult(runAgentMetadataCommand(AgentSettingsState.getInstance().agentExecutablePath,
            System.getProperty("user.home"), 15_000, "--list-models"))
    },
    execute: (() -> Unit) -> Future<*> = { ApplicationManager.getApplication().executeOnPooledThread(it) },
    dispatch: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
) : JPanel(BorderLayout()) {
    internal val draft = AgentSettingsState().apply { selectedModel = saved.selectedModel }
    internal val selector = ModelSelector(draft)
    private var catalog: ModelCatalogState = ModelCatalogState.Loading
    private var disposed = false
    private val loader = ModelCatalogLoader(fetch, execute, dispatch, { !disposed }) {
        catalog = it
        selector.showCatalog(it)
    }

    init {
        add(selector, BorderLayout.WEST)
        selector.onRetry = loader::load
        loader.load()
    }

    fun isModified(saved: AgentSettingsState): Boolean = !disposed && draft.selectedModel != saved.selectedModel

    fun applyTo(saved: AgentSettingsState) {
        if (!disposed) saved.selectedModel = draft.selectedModel
    }

    fun reset(saved: AgentSettingsState) {
        if (disposed) return
        draft.selectedModel = saved.selectedModel
        selector.showCatalog(catalog)
    }

    fun dispose() {
        disposed = true
        loader.cancel()
        selector.onRetry = {}
        selector.closePopup()
        selector.isEnabled = false
    }
}
