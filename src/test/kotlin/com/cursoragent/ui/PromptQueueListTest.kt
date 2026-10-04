package com.cursoragent.ui

import com.cursoragent.actions.AgentQueueAction
import com.cursoragent.actions.AgentQueueActions
import com.cursoragent.actions.AgentQueueCommand
import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory

class PromptQueueListTest {
    @Test
    fun `entry from an empty prompt selects last for up and second or only item for down`() = SwingUtilities.invokeAndWait {
        val queue = PromptQueue("owner")
        var current = true
        var changes = 0
        val list = PromptQueueList(queue, { current }, { true }, {}, { changes++ }, {})
        assertFalse(list.selectFromPrompt(true))
        queue.add("one", AgentMode.AGENT, "model")
        assertTrue(list.selectFromPrompt(false))
        assertEquals(0, list.selectedIndex)
        repeat(2) { queue.add("next-$it", AgentMode.AGENT, "model") }
        val ticket = queue.ticket(1)
        assertTrue(list.selectFromPrompt(true))
        assertEquals(queue.snapshot().last().id, list.selectedValue.id)
        assertTrue(list.selectFromPrompt(false))
        assertEquals(queue.snapshot()[1].id, list.selectedValue.id)
        assertEquals(0, changes, "refreshing an inline view must not recursively notify queue mutations")
        assertEquals(ticket, queue.ticket(1), "focus navigation alone does not pause or resume automatic queue dispatch")
        current = false
        assertFalse(list.selectFromPrompt(true))
        assertEquals(1, list.selectedIndex)
    }

    @Test
    fun `queue actions retain identity across navigation edits and deletion and never resume sends`() = SwingUtilities.invokeAndWait {
        val queue = PromptQueue("owner")
        val context = PromptContextSnapshot(emptyList(), emptyList(), false)
        repeat(3) { queue.add("entry-$it", AgentMode.ASK, "exact-model", context, "command-$it") }
        val original = queue.snapshot()
        val oldTicket = queue.ticket(1)!!
        queue.pause()
        var returned = 0
        val list = PromptQueueList(queue, { true }, { true }, { queue.edit(it.id, "edited") }, {}, { returned++ })
        list.refresh()
        invoke(AgentQueueAction.Previous(), list.actions)
        assertEquals(original[0].id, list.selectedValue.id)
        invoke(AgentQueueAction.Next(), list.actions)
        invoke(AgentQueueAction.Edit(), list.actions)
        assertEquals(original[1].copy(text = "edited"), queue.snapshot()[1])
        assertEquals(original[1].id, list.selectedValue.id)
        invoke(AgentQueueAction.Remove(), list.actions)
        assertEquals(listOf(original[0], original[2]), queue.snapshot())
        assertEquals(original[2].id, list.selectedValue.id, "removing a row keeps the nearby remaining selection")
        invoke(AgentQueueAction.Next(), list.actions)
        invoke(AgentQueueAction.ReturnToInput(), list.actions)
        assertEquals(2, returned)
        assertTrue(queue.paused)
        assertNull(queue.next())
        assertFalse(queue.dispatch(oldTicket, 1, true) { error("queue navigation must not resume an old send") })
    }

    @Test
    fun `stale data contexts popup focus and lost ownership cannot edit or delete another queue`() = SwingUtilities.invokeAndWait {
        val queue = PromptQueue("owner")
        queue.add("old", AgentMode.AGENT, "model")
        queue.pause()
        var current = true
        var listFocus = true
        val edited = mutableListOf<QueuedPrompt>()
        var returned = 0
        val list = PromptQueueList(queue, { current }, { listFocus }, edited::add, {}, { returned++ })
        list.refresh()
        val remove = AgentQueueAction.Remove()
        val event = event(remove, list.actions)
        remove.update(event)
        assertTrue(event.presentation.isEnabled)
        listFocus = false
        remove.actionPerformed(event)
        assertEquals(1, queue.size)
        listFocus = true
        current = false
        remove.actionPerformed(event)
        invoke(AgentQueueAction.ReturnToInput(), list.actions)
        assertEquals(1, queue.size)
        assertEquals(0, returned)
        current = true
        queue.edit(queue.snapshot().single().id, "latest")
        invoke(AgentQueueAction.Edit(), list.actions)
        assertEquals("latest", edited.single().text, "actions resolve the current entry, not the displayed old snapshot")
        queue.remove(queue.snapshot().single().id)
        invoke(AgentQueueAction.Edit(), list.actions)
        assertEquals(1, edited.size)
        list.refresh()
        remove.update(event)
        assertFalse(event.presentation.isEnabled)
        invoke(AgentQueueAction.ReturnToInput(), list.actions)
        assertEquals(1, returned, "an empty list still allows return to the input")
        invoke(AgentQueueAction.Edit(), null)
        assertEquals(1, edited.size)
    }

    @Test
    fun `queue keymap actions use native list defaults and OS specific removal without a submit alias`() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/resources/META-INF/plugin.xml"))
        val nodes = xml.getElementsByTagName("action")
        val declarations = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }.associateBy { it.getAttribute("id") }
        val commands = mapOf(
            AgentQueueCommand.PREVIOUS to (AgentQueueAction.Previous() to listOf("UP")),
            AgentQueueCommand.NEXT to (AgentQueueAction.Next() to listOf("DOWN")),
            AgentQueueCommand.EDIT to (AgentQueueAction.Edit() to listOf("RIGHT", "SPACE")),
            AgentQueueCommand.REMOVE to (AgentQueueAction.Remove() to listOf("control DELETE")),
            AgentQueueCommand.RETURN_TO_INPUT to (AgentQueueAction.ReturnToInput() to listOf("ESCAPE")),
        )
        assertEquals(AgentQueueCommand.entries.toSet(), commands.keys)
        commands.forEach { (command, expected) ->
            val declaration = declarations.getValue(command.actionId)
            assertEquals(expected.first.javaClass.name, declaration.getAttribute("class"))
            val nodes = declaration.getElementsByTagName("keyboard-shortcut")
            val keys = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }.groupBy { it.getAttribute("keymap") }
            assertEquals(expected.second, keys.getValue("\$default").map { it.getAttribute("first-keystroke") })
            if (command == AgentQueueCommand.REMOVE) {
                assertEquals(setOf("\$default", "Mac OS X", "Mac OS X 10.5+"), keys.keys)
                for (keymap in listOf("Mac OS X", "Mac OS X 10.5+")) {
                    assertEquals("meta BACK_SPACE", keys.getValue(keymap).single().getAttribute("first-keystroke"))
                    assertEquals("true", keys.getValue(keymap).single().getAttribute("replace-all"))
                }
            } else assertEquals(setOf("\$default"), keys.keys)
            keys.values.flatten().forEach { assertNotNull(KeyStroke.getKeyStroke(it.getAttribute("first-keystroke"))) }
        }
    }

    private fun event(action: AgentQueueAction, target: AgentQueueActions?) = AnActionEvent(
        DataContext { if (AgentQueueActions.KEY.`is`(it)) target else null },
        action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )

    private fun invoke(action: AgentQueueAction, target: AgentQueueActions?) = action.actionPerformed(event(action, target))
}
