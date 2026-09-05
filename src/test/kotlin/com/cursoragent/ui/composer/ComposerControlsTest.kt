package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ComposerControlsTest {
    private val options = listOf(
        ModelOption("auto", "Auto (current, default)"),
        ModelOption("sol-medium", "GPT Sol Medium"),
        ModelOption("opus-high", "Claude Opus High"),
        ModelOption("sol-high-fast", "GPT Sol High Fast"),
    )

    @Test
    fun `search by label or id and keyboard selection only changes model on confirmation`() = SwingUtilities.invokeAndWait {
        val selected = mutableListOf<String>()
        var closed = false
        val panel = ModelPopupPanel(options, "sol-medium", null, { selected.add(it.id) }, { closed = true }, {})
        panel.searchField.text = "HIGH"
        assertEquals(2, panel.modelList.model.size)
        assertTrue(selected.isEmpty())
        panel.moveSelection(1)
        panel.chooseHighlighted()
        assertEquals(listOf("sol-high-fast"), selected)
        assertTrue(closed)
        panel.searchField.text = "sol-medium"
        assertEquals("sol-medium", panel.modelList.model.getElementAt(0).id)
    }

    @Test
    fun `empty search result and Escape never change persisted selection`() = SwingUtilities.invokeAndWait {
        var selections = 0
        var closed = false
        val panel = ModelPopupPanel(options, "sol-medium", null, { selections++ }, { closed = true }, {})
        panel.searchField.text = "missing"
        panel.chooseHighlighted()
        assertEquals(0, panel.modelList.model.size)
        panel.actionMap.get("cancel").actionPerformed(ActionEvent(panel, 0, "cancel"))
        assertTrue(closed)
        assertEquals(0, selections)
    }

    @Test
    fun `Auto toggle restores last manual model and blocks hidden list confirmation`() = SwingUtilities.invokeAndWait {
        val selected = mutableListOf<String>()
        val panel = ModelPopupPanel(options, "opus-high", null, { selected.add(it.id) }, {}, {})
        val expandedHeight = panel.preferredSize.height
        panel.autoToggle.doClick()
        assertEquals(listOf("auto"), selected)
        assertTrue(panel.preferredSize.height < expandedHeight)
        panel.chooseHighlighted()
        assertEquals(listOf("auto"), selected)
        panel.autoToggle.doClick()
        assertEquals(listOf("auto", "opus-high"), selected)
    }

    @Test
    fun `Auto without any manual models cannot toggle into an invalid selection`() = SwingUtilities.invokeAndWait {
        val panel = ModelPopupPanel(options.take(1), "auto", null, { fail("No selection expected") }, {}, {})
        assertTrue(panel.autoToggle.isSelected)
        assertFalse(panel.autoToggle.isEnabled)
    }

    @Test
    fun `selector leaves unused row space unclickable and clamps a long model without covering mode`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState().apply { selectedModel = "auto" }
        val mode = ModeSelector(settings)
        val model = ModelSelector(settings).apply { setModels(options) }
        val row = JPanel(SelectorRowLayout()).apply { add(mode); add(model) }
        row.setSize(600, 32)
        row.doLayout()
        assertEquals(model.preferredSize.width, model.width)
        assertTrue(model.x + model.width < row.width)
        assertNotSame(model, row.getComponentAt(row.width - 1, 10))
        model.setModels(listOf(ModelOption("long", "A very long model name with high thinking and fast mode")))
        for (width in listOf(170, 270, 470)) {
            row.setSize(width, 32)
            row.doLayout()
            assertTrue(mode.x + mode.width <= model.x)
            assertTrue(model.x + model.width <= row.width)
            assertTrue(model.width <= model.preferredSize.width)
        }
    }

    @Test
    fun `Plan and Ask update the saved mode and distinct pill colors`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState()
        val selector = ModeSelector(settings)
        selector.selectMode(AgentMode.PLAN)
        val planColor = selector.pillColor
        assertEquals(AgentMode.PLAN, settings.mode)
        assertEquals("Mode: Plan", selector.getAccessibleContext().accessibleName)
        assertNotNull(selector.icon)
        selector.selectMode(AgentMode.ASK)
        assertEquals(AgentMode.ASK, settings.mode)
        assertEquals("Mode: Ask", selector.getAccessibleContext().accessibleName)
        assertNotEquals(planColor, selector.pillColor)
    }

    @Test
    fun `prompt grows to twelve visual lines then scrolls and shrinks on deletion`() {
        assertEquals(PromptSizing(58, false), promptSizing(20, 20, 14, 58))
        assertEquals(PromptSizing(174, false), promptSizing(160, 20, 14, 58))
        assertEquals(PromptSizing(254, false), promptSizing(240, 20, 14, 58))
        assertEquals(PromptSizing(254, true), promptSizing(260, 20, 14, 58))
        assertEquals(PromptSizing(58, false), promptSizing(20, 20, 14, 58))
    }
}
