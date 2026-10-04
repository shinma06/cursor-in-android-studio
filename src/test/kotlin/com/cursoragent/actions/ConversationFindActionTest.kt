package com.cursoragent.actions

import com.cursoragent.ui.unusedActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConversationFindActionTest {
    private val actions = listOf(
        ConversationFindCommand.OPEN to ConversationFindAction.Open(),
        ConversationFindCommand.NEXT to ConversationFindAction.Next(),
        ConversationFindCommand.PREVIOUS to ConversationFindAction.Previous(),
        ConversationFindCommand.CLOSE to ConversationFindAction.Close(),
    )

    @Test
    fun `actions recheck current target and cannot act outside the owning search context`() = SwingUtilities.invokeAndWait {
        var allowed = true
        var owner = "tab A"
        val calls = mutableListOf<Pair<String, ConversationFindCommand>>()
        val target = ConversationFindTarget({ allowed }) { calls.add(owner to it) }
        actions.forEach { (command, action) ->
            val outside = event(action, DataContext.EMPTY_CONTEXT)
            action.update(outside)
            assertFalse(outside.presentation.isEnabled)
            val before = calls.size
            action.actionPerformed(outside)
            assertEquals(before, calls.size)
            val e = event(action, DataContext { if (ConversationFindTarget.KEY.`is`(it)) target else null })
            allowed = true
            action.update(e)
            assertTrue(e.presentation.isEnabled)
            owner = "tab B"
            action.actionPerformed(e)
            assertEquals(owner to command, calls.last())
            allowed = false // lost focus, IME, child popup or disposed after update
            action.actionPerformed(e)
            assertEquals(before + 1, calls.size)
            action.update(e)
            assertFalse(e.presentation.isEnabled)
            owner = "tab A"
        }
    }

    @Test
    fun `registered keymap IDs classes and native keys match the in-panel find contract`() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/resources/META-INF/plugin.xml"))
        val nodes = xml.getElementsByTagName("action")
        val declarations = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }.associateBy { it.getAttribute("id") }
        actions.forEach { (command, action) ->
            val declaration = declarations.getValue(command.actionId)
            assertEquals(action.javaClass.name, declaration.getAttribute("class"))
            val keys = declaration.getElementsByTagName("keyboard-shortcut")
            val defaults = (0 until keys.length).map { keys.item(it) as org.w3c.dom.Element }
                .groupBy({ it.getAttribute("keymap") }, { it.getAttribute("first-keystroke") })
            val expected = when (command) {
                ConversationFindCommand.OPEN -> mapOf("\$default" to listOf("control F"), "Mac OS X" to listOf("meta F"), "Mac OS X 10.5+" to listOf("meta F"))
                ConversationFindCommand.NEXT -> mapOf("\$default" to listOf("F3", "ENTER"))
                ConversationFindCommand.PREVIOUS -> mapOf("\$default" to listOf("shift F3", "shift ENTER"))
                ConversationFindCommand.CLOSE -> mapOf("\$default" to listOf("ESCAPE"))
            }
            assertEquals(expected, defaults)
            defaults.values.flatten().forEach { assertNotNull(KeyStroke.getKeyStroke(it)) }
        }
    }

    private fun event(action: ConversationFindAction, context: DataContext) = AnActionEvent(
        context, action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )
}
