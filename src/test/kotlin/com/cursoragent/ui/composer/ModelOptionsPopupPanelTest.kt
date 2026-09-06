package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class ModelOptionsPopupPanelTest {
    private val options = listOf(
        ModelOption("auto", "Auto (current, default)"),
        ModelOption("opus-high", "Opus 1M High"),
        ModelOption("opus-thinking-high", "Opus 1M High Thinking"),
        ModelOption("opus-thinking-high-fast", "Opus 1M High Thinking Fast"),
        ModelOption("opus-thinking-max", "Opus 1M Max Thinking"),
        ModelOption("composer", "Composer"),
        ModelOption("composer-fast", "Composer Fast"),
    )

    @Test
    fun `restore saved alias and show only options compatible with the current combination`() = SwingUtilities.invokeAndWait {
        val selected = mutableListOf<String>()
        val panel = ModelOptionsPopupPanel(options, "opus-high", null, { selected.add(it.id) }, {}, {})
        assertEquals(setOf(ModelAxis.THINKING), panel.optionControls.keys)
        assertTrue(selected.isEmpty())
        panel.selectOption(ModelAxis.FAST, "true")
        assertTrue(selected.isEmpty())
        (panel.optionControls.getValue(ModelAxis.THINKING) as AutoToggle).doClick()
        assertEquals(listOf("opus-thinking-high"), selected)
        assertEquals(setOf(ModelAxis.THINKING, ModelAxis.FAST, ModelAxis.EFFORT), panel.optionControls.keys)
        panel.selectOption(ModelAxis.EFFORT, "max")
        assertEquals("opus-thinking-max", panel.currentId)
        assertEquals(setOf(ModelAxis.EFFORT), panel.optionControls.keys)
    }

    @Test
    fun `family picker deduplicates aliases and searches hidden variant metadata`() = SwingUtilities.invokeAndWait {
        val selected = mutableListOf<String>()
        val panel = ModelOptionsPopupPanel(options, "opus-thinking-high", null, { selected.add(it.id) }, {}, {})
        panel.showModels()
        val picker = panel.picker!!
        assertEquals(2, picker.modelList.model.size)
        picker.searchField.text = "max"
        assertEquals(1, picker.modelList.model.size)
        picker.chooseHighlighted()
        assertEquals("opus-thinking-high", panel.currentId)
        assertNull(panel.picker)
        panel.showModels()
        panel.picker!!.searchField.text = "composer"
        panel.picker!!.chooseHighlighted()
        assertEquals("composer", panel.currentId)
        assertEquals(setOf(ModelAxis.FAST), panel.optionControls.keys)
    }

    @Test
    fun `Auto retains the exact manual variant and reopening options changes nothing`() = SwingUtilities.invokeAndWait {
        val selected = mutableListOf<String>()
        val panel = ModelOptionsPopupPanel(options, "opus-thinking-max", null, { selected.add(it.id) }, {}, {})
        panel.showModels()
        val picker = panel.picker!!
        picker.autoToggle.doClick()
        assertEquals("auto", panel.currentId)
        picker.autoToggle.doClick()
        assertEquals("opus-thinking-max", panel.currentId)
        panel.showOptions()
        assertEquals(listOf("auto", "opus-thinking-max"), selected)
    }

    @Test
    fun `Escape from effort page returns without choosing the highlighted alternative`() = SwingUtilities.invokeAndWait {
        var selections = 0
        val panel = ModelOptionsPopupPanel(options, "opus-thinking-high", null, { selections++ }, {}, {})
        (panel.optionControls.getValue(ModelAxis.EFFORT) as javax.swing.JButton).doClick()
        panel.choiceList!!.selectedIndex = 1
        panel.choiceList!!.actionMap.get("back").actionPerformed(null)
        assertNull(panel.choiceList)
        assertEquals("opus-thinking-high", panel.currentId)
        assertEquals(0, selections)
    }
    @Test
    fun `context selection uses an explicitly advertised alias and updates the model trigger`() = SwingUtilities.invokeAndWait {
        val contextOptions = listOf(
            ModelOption("model-high-300k", "Model 300K High"),
            ModelOption("model-high-1m", "Model 1M High"),
        )
        val settings = com.cursoragent.settings.AgentSettingsState().apply { selectedModel = "model-high-300k" }
        val panel = ModelOptionsPopupPanel(contextOptions, settings.selectedModel, null, { settings.selectedModel = it.id }, {}, {})
        assertEquals(setOf(ModelAxis.CONTEXT), panel.optionControls.keys)
        panel.selectOption(ModelAxis.CONTEXT, "1M")
        assertEquals("model-high-1m", settings.selectedModel)
        val trigger = ModelSelector(settings).apply { setModels(contextOptions) }
        assertEquals("Model High 1M", trigger.text)
        assertTrue(trigger.toolTipText.contains("model-high-1m"))
    }

}
