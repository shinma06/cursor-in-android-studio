package com.cursoragent.settings

import com.cursoragent.service.ModelCatalogState
import com.cursoragent.service.ModelOption
import com.cursoragent.service.TurnSettings
import com.cursoragent.ui.composer.ModelSelector
import com.intellij.util.xmlb.XmlSerializer
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities
import org.jdom.Element
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DefaultModelSettingsPanelTest {
    private class Fixture(val saved: AgentSettingsState = AgentSettingsState().apply { selectedModel = "saved-unknown-id" }) {
        val work = mutableListOf<() -> Unit>()
        val delivery = mutableListOf<() -> Unit>()
        val futures = mutableListOf<CompletableFuture<Unit>>()
        var result: ModelCatalogState = ModelCatalogState.Failed
        val panel = DefaultModelSettingsPanel(saved, { result }, { work.add(it); CompletableFuture<Unit>().also(futures::add) }, { delivery.add(it) })
        fun complete() { work.removeAt(0)(); delivery.removeAt(0)() }
    }

    @Test
    fun `loading failure empty and retry retain the saved unknown ID until Apply`() = SwingUtilities.invokeAndWait {
        val f = Fixture()
        assertFalse(f.panel.selector.isEnabled)
        assertFalse(f.panel.isModified(f.saved))
        f.complete()
        assertTrue(f.panel.selector.isEnabled)
        assertEquals("saved-unknown-id", f.panel.draft.selectedModel)
        f.result = ModelCatalogState.Loaded(emptyList())
        f.panel.selector.onRetry()
        f.complete()
        assertEquals("saved-unknown-id", f.saved.selectedModel)
        f.result = ModelCatalogState.Loaded(listOf(ModelOption("auto", "Auto"), ModelOption("exact-cli-id", "CLI model")))
        f.panel.selector.onRetry()
        f.complete()
        assertEquals("saved-unknown-id", f.panel.draft.selectedModel)
        assertTrue(f.panel.selector.toolTipText.contains("保存済み選択を保持"))
        f.panel.draft.selectedModel = "exact-cli-id"
        assertTrue(f.panel.isModified(f.saved))
        assertEquals("saved-unknown-id", f.saved.selectedModel)
        f.panel.applyTo(f.saved)
        assertEquals("exact-cli-id", f.saved.selectedModel)
        assertFalse(f.panel.isModified(f.saved))
        f.panel.dispose()
    }

    @Test
    fun `reset cancels draft edits and disposal rejects late UI and settings updates`() = SwingUtilities.invokeAndWait {
        val f = Fixture()
        f.panel.draft.selectedModel = "auto"
        f.panel.reset(f.saved)
        assertEquals("saved-unknown-id", f.panel.draft.selectedModel)
        f.work.removeAt(0)()
        val text = f.panel.selector.text
        f.panel.dispose()
        assertTrue(f.futures.single().isCancelled)
        f.delivery.removeAt(0)()
        assertEquals(text, f.panel.selector.text)
        assertFalse(f.panel.selector.isEnabled)
        f.panel.draft.selectedModel = "auto"
        f.panel.applyTo(f.saved)
        assertEquals("saved-unknown-id", f.saved.selectedModel)
        f.panel.selector.onRetry()
        assertTrue(f.work.isEmpty())
    }

    @Test
    fun `Apply affects only fresh print selection while existing turn and ACP stay detached`() = SwingUtilities.invokeAndWait {
        val f = Fixture(AgentSettingsState().apply { selectedModel = "old-exact-id" })
        val oldTab = f.saved.composerSelection(true)
        oldTab.selectedModel = "current-tab-id"
        val running = TurnSettings("", oldTab.selectedModel, oldTab.mode, oldTab.permissionMode, oldTab.sandboxMode)
        f.panel.draft.selectedModel = "auto"
        f.panel.applyTo(f.saved)
        assertEquals("current-tab-id", oldTab.selectedModel)
        assertEquals("current-tab-id", running.model)
        val newTab = f.saved.composerSelection(true)
        assertEquals("auto", newTab.selectedModel)
        assertEquals("", f.saved.composerSelection(false).selectedModel)
        val acp = ModelSelector(newTab)
        acp.waitForAcp()
        assertEquals("", newTab.selectedModel)
        acp.setAcpModels(listOf(ModelOption("server-id", "Server model")), "server-id")
        assertEquals("server-id", newTab.selectedModel)
        assertEquals("auto", f.saved.selectedModel)
        f.panel.dispose()
    }

    @Test
    fun `existing XML default and exact Auto or unknown IDs survive restart`() {
        val old = XmlSerializer.deserialize(Element("state"), AgentSettingsState::class.java)
        assertEquals("", old.selectedModel)
        for (id in listOf("", "auto", "unknown-cli-id")) {
            val saved = AgentSettingsState().apply { selectedModel = id }
            val restart = AgentSettingsState().apply {
                loadState(XmlSerializer.deserialize(XmlSerializer.serialize(saved), AgentSettingsState::class.java))
            }
            assertEquals(id, restart.selectedModel)
            assertEquals(id, restart.composerSelection(true).selectedModel)
            assertEquals("", restart.composerSelection(false).selectedModel)
        }
    }
}
