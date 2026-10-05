package com.cursoragent.ui.composer

import com.cursoragent.service.ModelCatalogState
import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentSettingsState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class ModelCatalogStatusTest {
    @Test
    fun `ACP queue configuration preserves draft and parameter cycle waits for confirmation`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState()
        val selector = ModelSelector(settings)
        selector.waitForAcp()
        val models = listOf(ModelOption("a", "A"), ModelOption("b", "B"))
        val values = listOf(ModelOption("low", "Low"), ModelOption("high", "High"))
        val initial = com.cursoragent.service.AgentEvent.Configuration("ask", "a", models, listOf(
            com.cursoragent.service.ModelParameter("fixed", "Fixed", "model_config", "low", values.take(1)),
            com.cursoragent.service.ModelParameter("thinking", "Thinking", "thought_level", "high", values),
        ))
        selector.setAcpConfiguration(initial)
        val queued = initial.copy(mode = "agent", model = "b", parameters = emptyList())
        selector.setAcpConfiguration(queued, preserveDraft = true)
        assertSame(queued, selector.providerConfiguration)
        assertSame(initial, selector.acpConfiguration)
        assertEquals("a", settings.selectedModel)
        val requested = mutableListOf<Pair<String, String>>()
        selector.onConfigure = { state, id, value -> assertSame(initial, state); requested += id to value }
        selector.cycleParameter()
        assertEquals(listOf("thinking" to "low"), requested)
        assertEquals("high", selector.parameterValues["thinking"])
        selector.isEnabled = false
        selector.cycleParameter()
        assertEquals(1, requested.size)
        selector.setAcpConfiguration(queued)
        assertEquals("b", settings.selectedModel)
        assertTrue(selector.parameterValues.isEmpty())
        selector.cycleParameter()
        assertEquals(1, requested.size)
        selector.setAcpConfiguration(null)
        assertFalse(selector.isEnabled)
    }

    @Test
    fun `failure empty loading and saved selection remain distinct and retry accepts keyboard`() = SwingUtilities.invokeAndWait {
        for (saved in listOf("", "auto", "gpt-5.3-codex-low")) {
            val settings = AgentSettingsState().apply { selectedModel = saved }
            val selector = ModelSelector(settings)
            var retries = 0
            selector.onRetry = { retries++; selector.showCatalog(ModelCatalogState.Loading) }
            assertFalse(selector.isEnabled)
            val loading = selector.text
            selector.showCatalog(ModelCatalogState.Failed)
            val failed = selector.text
            assertTrue(selector.isEnabled)
            assertTrue(selector.accessibleContext.accessibleName.contains("再試行"))
            for (key in listOf("pressed SPACE", "released SPACE")) {
                val binding = selector.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key))
                selector.actionMap.get(binding).actionPerformed(ActionEvent(selector, 0, key))
            }
            assertEquals(1, retries)
            assertEquals(loading, selector.text)
            selector.doClick(0)
            assertEquals(1, retries)
            selector.setModels(emptyList())
            assertTrue(selector.isEnabled)
            assertNotEquals(failed, selector.text)
            assertNotEquals(loading, selector.text)
            selector.doClick(0)
            assertEquals(2, retries)
            selector.setModels(listOf(ModelOption("other", "Other")))
            assertEquals(saved, settings.selectedModel)
            assertTrue(selector.isEnabled)
            assertTrue(selector.toolTipText.contains("今回の一覧にはありません"))
        }
    }

    @Test
    fun `ACP has separate IDs and returning to print restores the saved variant or Auto`() = SwingUtilities.invokeAndWait {
        for (saved in listOf("auto", "gpt-5.3-codex-low")) {
            val settings = AgentSettingsState().apply { selectedModel = saved }
            val selector = ModelSelector(settings)
            selector.setModels(listOf(ModelOption(saved, "Saved")))
            selector.waitForAcp()
            assertEquals("", settings.selectedModel)
            selector.setAcpModels(listOf(ModelOption("provider", "Provider")), "provider")
            assertEquals("provider", settings.selectedModel)
            assertTrue(selector.showsChevron)
            selector.showCatalog(ModelCatalogState.Loading)
            selector.showCatalog(ModelCatalogState.Failed)
            assertEquals(saved, settings.selectedModel)
            selector.setModels(listOf(ModelOption(saved, "Saved")))
            assertEquals("Saved — $saved", selector.toolTipText)
        }
    }

    @Test
    fun `one tabs failure or empty response never changes the other tabs selection`() = SwingUtilities.invokeAndWait {
        val a = AgentSettingsState().apply { selectedModel = "a" }
        val b = AgentSettingsState().apply { selectedModel = "b" }
        val first = ModelSelector(a)
        val second = ModelSelector(b)
        second.setModels(listOf(ModelOption("b", "B")))
        first.showCatalog(ModelCatalogState.Failed)
        first.setModels(emptyList())
        assertEquals("a", a.selectedModel)
        assertEquals("b", b.selectedModel)
        assertEquals("B — b", second.toolTipText)
    }
}
