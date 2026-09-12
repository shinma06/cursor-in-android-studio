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
    fun `unlisted saved ID is never presented as Auto or implicitly selected`() = SwingUtilities.invokeAndWait {
        var changes = 0
        val panel = ModelOptionsPopupPanel(options, "saved-unlisted", null, { changes++ }, {}, {})
        assertEquals("Model: saved-unlisted", panel.modelRow.accessibleContext.accessibleName)
        assertTrue(panel.modelRow.components.filterIsInstance<javax.swing.JLabel>().any { it.text.contains("saved-unlisted") })
        assertEquals("saved-unlisted", panel.currentId)
        assertTrue(panel.optionControls.isEmpty())
        panel.showModels()
        assertEquals(0, changes)
    }

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
        assertEquals(3, picker.modelList.model.size)
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
        picker.modelList.selectedIndex = 0
        picker.chooseHighlighted()
        assertEquals("auto", panel.currentId)
        assertNull(panel.picker)
        assertTrue(panel.optionControls.isEmpty())
        panel.showModels()
        panel.picker!!.searchField.text = "opus"
        panel.picker!!.chooseHighlighted()
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

    @Test
    fun `returning to initial family preserves the original options before any option change`() = SwingUtilities.invokeAndWait {
        val panel = ModelOptionsPopupPanel(options, "opus-thinking-max", null, {}, {}, {})
        panel.showModels()
        panel.picker!!.searchField.text = "composer"
        panel.picker!!.chooseHighlighted()
        panel.showModels()
        panel.picker!!.searchField.text = "opus"
        panel.picker!!.chooseHighlighted()
        assertEquals("opus-thinking-max", panel.currentId)
    }

    @Test
    fun `Auto startup keeps its manual family variant after visiting another model`() = SwingUtilities.invokeAndWait {
        val panel = ModelOptionsPopupPanel(options, "auto", "opus-thinking-max", {}, {}, {})
        assertNull(panel.picker)
        panel.showModels()
        panel.picker!!.searchField.text = "composer"
        panel.picker!!.chooseHighlighted()
        panel.showModels()
        panel.picker!!.searchField.text = "opus"
        panel.picker!!.chooseHighlighted()
        assertEquals("opus-thinking-max", panel.currentId)
    }

    @Test
    fun `Model hover opens one child and keeps parent options until selection`() = SwingUtilities.invokeAndWait {
        var opens = 0
        var requestedFocus = true
        var selections = 0
        val panel = ModelOptionsPopupPanel(options, "opus-thinking-high", null, { selections++ }, {}, {},
            onShowModels = { _, focus -> opens++; requestedFocus = focus })
        val controls = panel.optionControls.toMap()
        val event = java.awt.event.MouseEvent(panel.modelRow, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 2, 2, 0, false)
        panel.modelRow.dispatchEvent(event)
        panel.modelRow.dispatchEvent(event)
        assertEquals(1, opens)
        assertFalse(requestedFocus)
        assertEquals(controls, panel.optionControls)
        assertEquals(0, selections)
        panel.picker!!.actionMap.get("cancel").actionPerformed(null)
        assertNull(panel.picker)
        assertEquals("opus-thinking-high", panel.currentId)
    }

    @Test
    fun `Auto opens with description card and keyboard Model action exposes all candidates`() = SwingUtilities.invokeAndWait {
        var opens = 0
        val panel = ModelOptionsPopupPanel(options, "auto", null, {}, {}, {},
            onShowModels = { _, focus -> assertTrue(focus); opens++ })
        assertNull(panel.picker)
        assertTrue(panel.optionControls.isEmpty())
        assertEquals("Model: Auto", panel.modelRow.accessibleContext.accessibleName)
        panel.modelRow.actionMap.get("models").actionPerformed(null)
        assertEquals(1, opens)
        assertEquals(3, panel.picker!!.modelList.model.size)
        assertEquals("auto", panel.picker!!.modelList.selectedValue.id)
    }
}
