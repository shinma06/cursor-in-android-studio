package com.cursoragent.actions

import com.cursoragent.ui.unusedActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory

class AgentPanelActionTest {
    @Test
    fun `actions require panel context and recheck current tab availability before invocation`() = SwingUtilities.invokeAndWait {
        val calls = mutableListOf<Pair<String, AgentPanelCommand>>()
        var selected = "first"
        var allowed = true
        val target = AgentPanelActions({ allowed }) { command, _ -> calls.add(selected to command) }
        val context = DataContext { if (AgentPanelActions.KEY.`is`(it)) target else null }
        actions().forEach { (command, action) ->
            assertTrue(action.isDumbAware)
            assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
            val outside = event(action)
            action.update(outside)
            assertFalse(outside.presentation.isEnabled)
            val before = calls.size
            action.actionPerformed(outside)
            assertEquals(before, calls.size)

            allowed = true
            val event = event(action, context)
            action.update(event)
            assertTrue(event.presentation.isEnabled)
            selected = "second"
            action.actionPerformed(event)
            assertEquals("second" to command, calls.last())
            // A popup, IME composition, ACP lock or disposal can invalidate a prior update.
            allowed = false
            action.actionPerformed(event)
            assertEquals(before + 1, calls.size)
            action.update(event)
            assertFalse(event.presentation.isEnabled)
            selected = "first"
        }
    }

    @Test
    fun `actions never use a target from a previous project or event`() = SwingUtilities.invokeAndWait {
        val calls = mutableListOf<String>()
        val first = AgentPanelActions({ true }) { _, _ -> calls.add("first") }
        val second = AgentPanelActions({ true }) { _, _ -> calls.add("second") }
        val action = AgentPanelAction.Stop()
        for (target in listOf(first, second, null)) {
            val context = DataContext { if (AgentPanelActions.KEY.`is`(it)) target else null }
            action.actionPerformed(event(action, context))
        }
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun `all command IDs have loadable actions and explicit Windows Linux and Mac defaults`() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            java.io.File("src/main/resources/META-INF/plugin.xml"),
        )
        val nodes = xml.getElementsByTagName("action")
        val declarations = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .associateBy { it.getAttribute("id") }
        actions().forEach { (command, action) ->
            val declaration = declarations.getValue(command.actionId)
            assertEquals(action.javaClass.name, declaration.getAttribute("class"))
            assertTrue(declaration.getAttribute("text").isNotBlank())
            val shortcuts = declaration.getElementsByTagName("keyboard-shortcut")
            val byKeymap = (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }
                .groupBy { it.getAttribute("keymap") }
            assertEquals(setOf("\$default", "Mac OS X", "Mac OS X 10.5+"), byKeymap.keys)
            byKeymap.forEach { (keymap, keys) ->
                if (keymap != "\$default") assertEquals("true", keys.first().getAttribute("replace-all"))
                keys.forEach { key ->
                    val stroke = KeyStroke.getKeyStroke(key.getAttribute("first-keystroke"))
                    assertNotNull(stroke, key.getAttribute("first-keystroke"))
                    val modifier = if (keymap == "\$default") java.awt.event.InputEvent.CTRL_DOWN_MASK else java.awt.event.InputEvent.META_DOWN_MASK
                    assertTrue(stroke.modifiers and modifier != 0)
                }
            }
        }
    }

    private fun actions() = listOf(
        AgentPanelCommand.NEW_CHAT to AgentPanelAction.NewChat(),
        AgentPanelCommand.CLOSE_CHAT to AgentPanelAction.CloseChat(),
        AgentPanelCommand.PREVIOUS_CHAT to AgentPanelAction.PreviousChat(),
        AgentPanelCommand.NEXT_CHAT to AgentPanelAction.NextChat(),
        AgentPanelCommand.STOP to AgentPanelAction.Stop(),
        AgentPanelCommand.MODE_MENU to AgentPanelAction.ModeMenu(),
        AgentPanelCommand.MODEL_MENU to AgentPanelAction.ModelMenu(),
        AgentPanelCommand.ADD_CONTEXT to AgentPanelAction.AddContext(),
        AgentPanelCommand.HISTORY to AgentPanelAction.History(),
        AgentPanelCommand.CHANGES to AgentPanelAction.Changes(),
        AgentPanelCommand.SETTINGS to AgentPanelAction.Settings(),
    )

    private fun event(action: AgentPanelAction, context: DataContext = DataContext.EMPTY_CONTEXT) = AnActionEvent(
        context, action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )
}
