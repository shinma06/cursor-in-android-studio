package com.cursoragent.ui.composer.command

import com.cursoragent.service.AgentCommand
import com.cursoragent.service.CommandCatalog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import java.awt.event.InputMethodEvent
import java.text.AttributedString
import javax.swing.SwingUtilities

class CommandPickerPanelTest {
    @Test
    fun `search replacement empty failure and cancel do not invoke and selection keeps exact ID`() = SwingUtilities.invokeAndWait {
        val selected = mutableListOf<String>()
        var cancelled = false
        val panel = CommandPickerPanel({ selected.add(it.name) }, { cancelled = true }, {})
        panel.update(CommandCatalog.Loading, true)
        assertEquals(0, panel.model.size)
        panel.update(CommandCatalog.Ready(listOf(AgentCommand("Mixed-日本語", "説明"), AgentCommand("second", "other"))), true)
        panel.search.text = "説明"
        assertEquals(1, panel.model.size)
        assertTrue(selected.isEmpty())
        panel.search.actionMap.get("ENTER").actionPerformed(ActionEvent(panel, 0, ""))
        assertEquals(listOf("Mixed-日本語"), selected)
        panel.update(CommandCatalog.Ready(emptyList()), true)
        assertEquals(0, panel.model.size)
        panel.update(CommandCatalog.Invalid, true)
        assertEquals(0, panel.model.size)
        panel.search.actionMap.get("ESCAPE").actionPerformed(ActionEvent(panel, 0, ""))
        assertTrue(cancelled)
    }

    @Test
    fun `IME Enter Escape and arrows do not select cancel or navigate while composing`() = SwingUtilities.invokeAndWait {
        var choices = 0
        var cancelled = false
        val panel = CommandPickerPanel({ choices++ }, { cancelled = true }, {})
        panel.update(CommandCatalog.Ready(listOf(AgentCommand("a", "a"), AgentCommand("b", "b"))), true)
        val event = InputMethodEvent(panel.search, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, AttributedString("日本").iterator, 0, null, null)
        panel.search.inputMethodListeners.forEach { it.inputMethodTextChanged(event) }
        for (key in listOf("ENTER", "ESCAPE", "DOWN")) panel.search.actionMap.get(key).actionPerformed(ActionEvent(panel, 0, ""))
        assertEquals(0, choices)
        assertFalse(cancelled)
        assertEquals(0, panel.list.selectedIndex)
    }

    @Test
    fun `large catalog remains searchable and keyboard scroll follows selection`() = SwingUtilities.invokeAndWait {
        val panel = CommandPickerPanel({}, {}, {})
        panel.update(CommandCatalog.Ready((0 until 1200).map { AgentCommand("command-$it", "description") }), true)
        panel.search.text = "command-1100"
        assertEquals("command-1100", panel.list.selectedValue.name)
        panel.search.text = ""
        assertEquals(1100, panel.list.selectedIndex)
        panel.list.selectedIndex = 0
        repeat(90) { panel.search.actionMap.get("DOWN").actionPerformed(ActionEvent(panel, 0, "")) }
        assertEquals(90, panel.list.selectedIndex)
        repeat(90) { panel.search.actionMap.get("UP").actionPerformed(ActionEvent(panel, 0, "")) }
        assertEquals(0, panel.list.selectedIndex)
    }
}
