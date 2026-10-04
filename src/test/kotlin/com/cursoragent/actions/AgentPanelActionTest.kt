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
            val expectedKeymaps = setOf("\$default", "Mac OS X", "Mac OS X 10.5+") +
                if (command == AgentPanelCommand.MODEL_MENU) setOf("Default for XWin") else emptySet()
            assertEquals(expectedKeymaps, byKeymap.keys)
            if (command == AgentPanelCommand.MODEL_MENU) {
                assertEquals(listOf("control SLASH"), byKeymap.getValue("\$default").map { it.getAttribute("first-keystroke") })
                assertEquals(listOf("control SLASH", "control alt SLASH"), byKeymap.getValue("Default for XWin").map { it.getAttribute("first-keystroke") })
                listOf("Mac OS X", "Mac OS X 10.5+").forEach { keymap ->
                    assertEquals(listOf("meta SLASH", "meta alt SLASH"), byKeymap.getValue(keymap).map { it.getAttribute("first-keystroke") })
                }
            }
            if (command == AgentPanelCommand.STOP) {
                assertEquals(listOf("control shift BACK_SPACE"), byKeymap.getValue("\$default").map { it.getAttribute("first-keystroke") })
                listOf("Mac OS X", "Mac OS X 10.5+").forEach { keymap ->
                    assertEquals(listOf("meta shift BACK_SPACE", "control C"), byKeymap.getValue(keymap).map { it.getAttribute("first-keystroke") })
                }
            }
            if (command == AgentPanelCommand.ACCEPT_PENDING) {
                byKeymap.forEach { (keymap, keys) ->
                    val modifier = if (keymap == "\$default") "control" else "meta"
                    assertEquals(listOf("$modifier ENTER", "$modifier alt ENTER"), keys.map { it.getAttribute("first-keystroke") })
                }
            }
            if (command == AgentPanelCommand.RESET_CHAT) {
                assertEquals(listOf("control R"), byKeymap.getValue("\$default").map { it.getAttribute("first-keystroke") })
                listOf("Mac OS X", "Mac OS X 10.5+").forEach { keymap ->
                    assertEquals(listOf("meta R"), byKeymap.getValue(keymap).map { it.getAttribute("first-keystroke") })
                }
            }
            if (command == AgentPanelCommand.SUBMIT_INITIAL) {
                byKeymap.forEach { (keymap, keys) ->
                    val modifier = if (keymap == "\$default") "control" else "meta"
                    assertEquals(listOf("$modifier shift ENTER"), keys.map { it.getAttribute("first-keystroke") })
                }
            }
            if (command == AgentPanelCommand.MODE_MENU) {
                byKeymap.forEach { (keymap, keys) ->
                    val modifier = if (keymap == "\$default") "control" else "meta"
                    assertEquals(listOf("$modifier PERIOD", "$modifier alt PERIOD", "shift TAB"),
                        keys.map { it.getAttribute("first-keystroke") })
                }
            }
            val recent = command in setOf(AgentPanelCommand.RECENT_CHAT, AgentPanelCommand.LEAST_RECENT_CHAT)
            if (recent) {
                val expected = if (command == AgentPanelCommand.RECENT_CHAT) "control TAB" else "control shift TAB"
                byKeymap.values.forEach { keys -> assertEquals(listOf(expected), keys.map { it.getAttribute("first-keystroke") }) }
            }
            if (command == AgentPanelCommand.UNFOCUS_INPUT) {
                byKeymap.values.forEach { keys -> assertEquals(listOf("ESCAPE"), keys.map { it.getAttribute("first-keystroke") }) }
            }
            if (command in setOf(AgentPanelCommand.PREVIOUS_AGENT, AgentPanelCommand.NEXT_AGENT)) {
                val key = if (command == AgentPanelCommand.PREVIOUS_AGENT) "LEFT" else "RIGHT"
                byKeymap.forEach { (keymap, keys) ->
                    val modifier = if (keymap == "\$default") "control" else "meta"
                    assertEquals(listOf("$modifier alt $key"), keys.map { it.getAttribute("first-keystroke") })
                }
            }
            byKeymap.forEach { (keymap, keys) ->
                if (keymap != "\$default") assertEquals("true", keys.first().getAttribute("replace-all"))
                keys.forEach { key ->
                    val stroke = KeyStroke.getKeyStroke(key.getAttribute("first-keystroke"))
                    assertNotNull(stroke, key.getAttribute("first-keystroke"))
                    val macControlStop = command == AgentPanelCommand.STOP && keymap != "\$default" &&
                        key.getAttribute("first-keystroke") == "control C"
                    val modifier = if (keymap == "\$default" || keymap == "Default for XWin" || macControlStop || recent) java.awt.event.InputEvent.CTRL_DOWN_MASK else java.awt.event.InputEvent.META_DOWN_MASK
                    if (command == AgentPanelCommand.UNFOCUS_INPUT) assertEquals(0, stroke.modifiers)
                    else if (command == AgentPanelCommand.MODE_MENU && stroke.keyCode == java.awt.event.KeyEvent.VK_TAB) {
                        assertEquals(KeyStroke.getKeyStroke("shift TAB"), stroke)
                    } else assertTrue(stroke.modifiers and modifier != 0)
                }
            }
        }
    }

    private fun actions() = listOf(
        AgentPanelCommand.NEW_CHAT to AgentPanelAction.NewChat(),
        AgentPanelCommand.RESET_CHAT to AgentPanelAction.ResetChat(),
        AgentPanelCommand.SUBMIT_INITIAL to AgentPanelAction.SubmitInitialChat(),
        AgentPanelCommand.UNFOCUS_INPUT to AgentPanelAction.UnfocusInput(),
        AgentPanelCommand.CLOSE_CHAT to AgentPanelAction.CloseChat(),
        AgentPanelCommand.PREVIOUS_CHAT to AgentPanelAction.PreviousChat(),
        AgentPanelCommand.NEXT_CHAT to AgentPanelAction.NextChat(),
        AgentPanelCommand.PREVIOUS_AGENT to AgentPanelAction.PreviousAgent(),
        AgentPanelCommand.NEXT_AGENT to AgentPanelAction.NextAgent(),
        AgentPanelCommand.RECENT_CHAT to AgentPanelAction.RecentChat(),
        AgentPanelCommand.LEAST_RECENT_CHAT to AgentPanelAction.LeastRecentChat(),
        AgentPanelCommand.STOP to AgentPanelAction.Stop(),
        AgentPanelCommand.ACCEPT_PENDING to AgentPanelAction.AcceptPending(),
        AgentPanelCommand.MODE_MENU to AgentPanelAction.ModeMenu(),
        AgentPanelCommand.MODEL_MENU to AgentPanelAction.ModelMenu(),
        AgentPanelCommand.ADD_CONTEXT to AgentPanelAction.AddContext(),
        AgentPanelCommand.CHANGES to AgentPanelAction.Changes(),
    )

    private fun event(action: AgentPanelAction, context: DataContext = DataContext.EMPTY_CONTEXT) = AnActionEvent(
        context, action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )
}
