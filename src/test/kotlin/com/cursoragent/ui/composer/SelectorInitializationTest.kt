package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class SelectorInitializationTest {
    @Test
    fun `late model loading in one tab cannot change another tabs mode or model`() = SwingUtilities.invokeAndWait {
        val a = AgentSettingsState().apply { mode = AgentMode.ASK; selectedModel = "a" }
        val b = AgentSettingsState().apply { mode = AgentMode.PLAN; selectedModel = "b" }
        val modelA = ModelSelector(a)
        val modelB = ModelSelector(b)
        ModeSelector(b).selectMode(AgentMode.AGENT)
        modelB.setModels(listOf(ModelOption("b", "B")))
        modelA.setModels(listOf(ModelOption("a", "A")))
        assertEquals("a", a.selectedModel)
        assertEquals(AgentMode.ASK, a.mode)
        assertEquals("b", b.selectedModel)
        assertEquals(AgentMode.AGENT, b.mode)
        assertEquals("B — b", modelB.toolTipText)
    }

    @Test
    fun `mode initializes its accessible name without crashing the tool window`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState().apply { mode = AgentMode.AGENT }
        val selector = ModeSelector(settings)
        assertEquals("Mode: Agent", selector.getAccessibleContext().accessibleName)
        assertEquals(AgentMode.AGENT, settings.mode)
    }

    @Test
    fun `loading model options preserves saved selection and exposes its full name`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState().apply { selectedModel = "long-model" }
        val selector = ModelSelector(settings)
        selector.setModels(listOf(ModelOption("auto", "Auto (current, default)"), ModelOption("long-model", "Long model name")))
        assertEquals("long-model", settings.selectedModel)
        assertEquals("Long model name — long-model", selector.toolTipText)
        assertEquals("Model: Long model name", selector.getAccessibleContext().accessibleName)
        assertTrue(selector.isEnabled)
    }
}
