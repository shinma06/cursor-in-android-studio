package com.cursoragent.actions

import com.cursoragent.ui.unusedActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory

class AgentWindowActionTest {
    @Test
    fun `global actions re-resolve the owning project and window state after update`() = SwingUtilities.invokeAndWait {
        val first = WindowFixture()
        val second = WindowFixture()
        var selected = first
        AgentWindowCommand.entries.forEach { command ->
            val action = object : AgentWindowAction(command, { selected.window }) {}
            assertTrue(action.isDumbAware)
            assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
            val event = event(action, first.project)
            action.update(event)
            assertTrue(event.presentation.isEnabled)
            // A resolver change must never route an old project's event into another project.
            selected = second
            action.actionPerformed(event)
            assertTrue(first.calls.isEmpty())
            assertTrue(second.calls.isEmpty())
            action.update(event)
            assertFalse(event.presentation.isEnabled)
            selected = first
        }
        val toggle = object : AgentWindowAction(AgentWindowCommand.TOGGLE, { first.window }) {}
        val event = event(toggle, first.project)
        toggle.update(event)
        first.visible = true
        toggle.actionPerformed(event)
        assertEquals(listOf("hide"), first.calls)
        toggle.actionPerformed(event)
        assertEquals(listOf("hide", "show"), first.calls)

        first.calls.clear()
        val swap = object : AgentWindowAction(AgentWindowCommand.SWAP_SIDE, { first.window }) {}
        val swapEvent = event(swap, first.project)
        swap.update(swapEvent)
        first.anchor = ToolWindowAnchor.LEFT
        swap.actionPerformed(swapEvent)
        assertEquals(ToolWindowAnchor.RIGHT, first.anchor)
        swap.actionPerformed(swapEvent)
        assertEquals(ToolWindowAnchor.LEFT, first.anchor)
        first.anchor = ToolWindowAnchor.BOTTOM
        swap.actionPerformed(swapEvent)
        assertEquals(ToolWindowAnchor.LEFT, first.anchor)
        assertEquals(listOf("anchor", "show", "anchor", "show", "anchor", "show"), first.calls)
    }

    @Test
    fun `missing unavailable and disposed windows cannot act even after a successful update`() = SwingUtilities.invokeAndWait {
        AgentWindowCommand.entries.forEach { command ->
            val fixture = WindowFixture()
            var window: ToolWindow? = fixture.window
            val action = object : AgentWindowAction(command, { window }) {}
            val outside = event(action, null)
            action.update(outside)
            action.actionPerformed(outside)
            assertFalse(outside.presentation.isEnabled)
            val event = event(action, fixture.project)
            for (invalidate in listOf<() -> Unit>(
                { window = null },
                { fixture.available = false },
                { fixture.disposed = true },
                { fixture.projectDisposed = true },
            )) {
                window = fixture.window
                fixture.available = true
                fixture.disposed = false
                fixture.projectDisposed = false
                action.update(event)
                assertTrue(event.presentation.isEnabled)
                invalidate()
                action.actionPerformed(event)
                action.update(event)
                assertFalse(event.presentation.isEnabled)
                assertTrue(fixture.calls.isEmpty())
            }
        }
    }

    @Test
    fun `global actions have distinct loadable IDs and platform shortcut defaults`() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/resources/META-INF/plugin.xml"))
        val nodes = xml.getElementsByTagName("action")
        val declarations = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .associateBy { it.getAttribute("id") }
        for ((command, action, key) in listOf(
            Triple(AgentWindowCommand.TOGGLE, AgentWindowAction.Toggle(), "alt J"),
            Triple(AgentWindowCommand.SWAP_SIDE, AgentWindowAction.SwapSide(), "E"),
        )) {
            assertFalse(AgentPanelCommand.entries.any { it.actionId == command.actionId })
            val declaration = declarations.getValue(command.actionId)
            assertEquals(action.javaClass.name, declaration.getAttribute("class"))
            val shortcuts = declaration.getElementsByTagName("keyboard-shortcut")
            assertEquals(3, shortcuts.length)
            val keys = (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }
                .associateBy { it.getAttribute("keymap") }
            assertEquals("control $key", keys.getValue("\$default").getAttribute("first-keystroke"))
            listOf("Mac OS X", "Mac OS X 10.5+").forEach { keymap ->
                assertEquals("meta $key", keys.getValue(keymap).getAttribute("first-keystroke"))
                assertEquals("true", keys.getValue(keymap).getAttribute("replace-all"))
            }
        }
    }

    @Test
    fun `chat entries expose both aliases and Windows follow up does not leak into Linux or Mac`() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/resources/META-INF/plugin.xml"))
        val nodes = xml.getElementsByTagName("action")
        val declarations = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .associateBy { it.getAttribute("id") }
        for ((command, action, keys) in listOf(
            Triple(AgentWindowCommand.OPEN_CHAT, AgentWindowAction.OpenChat(), listOf("L", "I")),
            Triple(AgentWindowCommand.NEW_AGENT, AgentWindowAction.NewAgent(), listOf("shift L", "shift I")),
            Triple(AgentWindowCommand.FOLLOW_UP, AgentWindowAction.FollowUp(), listOf("Y")),
        )) {
            val declaration = declarations.getValue(command.actionId)
            assertEquals(action.javaClass.name, declaration.getAttribute("class"))
            val shortcuts = declaration.getElementsByTagName("keyboard-shortcut")
            val byKeymap = (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }.groupBy { it.getAttribute("keymap") }
            val followUp = command == AgentWindowCommand.FOLLOW_UP
            assertEquals(if (followUp) 4 else 6, shortcuts.length)
            assertEquals(if (followUp) listOf("control shift Y") else keys.map { "control $it" },
                byKeymap.getValue("\$default").map { it.getAttribute("first-keystroke") })
            for (keymap in listOf("Mac OS X", "Mac OS X 10.5+") + if (followUp) listOf("Default for XWin") else emptyList()) {
                val entries = byKeymap.getValue(keymap)
                val modifier = if (keymap == "Default for XWin") "control" else "meta"
                assertEquals(keys.map { "$modifier $it" }, entries.map { it.getAttribute("first-keystroke") })
                assertEquals("true", entries.first().getAttribute("replace-all"))
            }
        }
    }

    @Test
    fun `All Agents uses Mac Control Shift S and Windows Linux Control Shift Slash`() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/resources/META-INF/plugin.xml"))
        val nodes = xml.getElementsByTagName("action")
        val declaration = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("id") == AgentWindowCommand.ALL_CHATS.actionId }
        assertEquals(AgentWindowAction.AllChats().javaClass.name, declaration.getAttribute("class"))
        val shortcuts = declaration.getElementsByTagName("keyboard-shortcut")
        assertEquals(3, shortcuts.length)
        val keys = (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }.associateBy { it.getAttribute("keymap") }
        assertEquals("control shift SLASH", keys.getValue("\$default").getAttribute("first-keystroke"))
        listOf("Mac OS X", "Mac OS X 10.5+").forEach { keymap ->
            assertEquals("control shift S", keys.getValue(keymap).getAttribute("first-keystroke"))
            assertEquals("true", keys.getValue(keymap).getAttribute("replace-all"))
        }
    }

    private fun event(action: AgentWindowAction, project: Project?) = AnActionEvent(
        DataContext { if (CommonDataKeys.PROJECT.`is`(it)) project else null },
        action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )

    private class WindowFixture {
        var projectDisposed = false
        var disposed = false
        var available = true
        var visible = false
        var anchor = ToolWindowAnchor.RIGHT
        val calls = mutableListOf<String>()
        val project = Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            when (method.name) {
                "isDisposed" -> projectDisposed
                "toString" -> "synthetic-project"
                else -> error("Unexpected Project call: ${method.name}")
            }
        } as Project
        val window = Proxy.newProxyInstance(ToolWindow::class.java.classLoader, arrayOf(ToolWindow::class.java)) { _, method, args ->
            when (method.name) {
                "getProject" -> project
                "isDisposed" -> disposed
                "isAvailable" -> available
                "isVisible" -> visible
                "getContentManagerIfCreated" -> null
                "getAnchor" -> anchor
                "setAnchor" -> { anchor = args!![0] as ToolWindowAnchor; calls.add("anchor"); null }
                "show" -> { visible = true; calls.add("show"); null }
                "hide" -> { visible = false; calls.add("hide"); null }
                "toString" -> "synthetic-window"
                else -> error("Unexpected ToolWindow call: ${method.name}")
            }
        } as ToolWindow
    }
}
