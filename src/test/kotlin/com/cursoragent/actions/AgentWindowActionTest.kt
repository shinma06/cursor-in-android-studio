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
            assertEquals(command != AgentWindowCommand.HISTORY, event.presentation.isEnabled)
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
                assertEquals(command != AgentWindowCommand.HISTORY, event.presentation.isEnabled)
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
            Triple(AgentWindowCommand.HISTORY, AgentWindowAction.History(), "alt QUOTE"),
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

    @Test
    fun `settings opens for the event project without creating or showing Agent content`() = SwingUtilities.invokeAndWait {
        val first = WindowFixture()
        val second = WindowFixture()
        val opened = mutableListOf<Project>()
        val action = object : AgentWindowAction(AgentWindowCommand.SETTINGS,
            { if (it === first.project) first.window else second.window }, opened::add) {}
        for (fixture in listOf(first, second, first)) {
            val event = event(action, fixture.project)
            action.update(event)
            assertTrue(event.presentation.isEnabled)
            action.actionPerformed(event)
            assertSame(fixture.project, opened.last())
            assertFalse(fixture.visible)
            assertTrue(fixture.calls.isEmpty())
        }
        assertEquals(3, opened.size)

        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File("src/main/resources/META-INF/plugin.xml"))
        val nodes = xml.getElementsByTagName("action")
        val declaration = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("id") == "CursorAgent.Settings" }
        assertEquals(AgentWindowAction.Settings().javaClass.name, declaration.getAttribute("class"))
        assertFalse(AgentPanelCommand.entries.any { it.actionId == AgentWindowCommand.SETTINGS.actionId })
        val shortcuts = declaration.getElementsByTagName("keyboard-shortcut")
        val byKeymap = (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }.groupBy { it.getAttribute("keymap") }
        assertEquals(setOf("\$default", "Mac OS X", "Mac OS X 10.5+"), byKeymap.keys)
        byKeymap.forEach { (keymap, keys) ->
            val modifier = if (keymap == "\$default") "control" else "meta"
            assertEquals(listOf("$modifier shift J", "$modifier COMMA"), keys.map { it.getAttribute("first-keystroke") })
            if (keymap != "\$default") assertEquals("true", keys.first().getAttribute("replace-all"))
        }
    }

    @Test
    fun `history uses an existing selected chat without creating or activating hidden content`() {
        val ide = com.intellij.testFramework.fixtures.IdeaTestFixtureFactory.getFixtureFactory()
            .createLightFixtureBuilder("global history").fixture
        ide.setUp()
        val settings = com.cursoragent.settings.AgentSettingsState.getInstance()
        val executable = settings.agentExecutablePath
        settings.agentExecutablePath = java.nio.file.Path.of(ide.project.basePath!!, "missing-history-fixture-agent-${java.util.UUID.randomUUID()}").toString()
        var root: com.cursoragent.ui.AgentToolWindowRootPanel? = null
        try {
            var metadata: java.util.concurrent.Future<*>? = null
            com.intellij.testFramework.runInEdtAndWait {
                val created = com.cursoragent.ui.AgentToolWindowRootPanel(ide.project) {}
                root = created
                val views = created.javaClass.getDeclaredField("views").apply { isAccessible = true }.get(created) as Map<*, *>
                val view = requireNotNull(views.values.single())
                val controller = view.javaClass.getDeclaredField("controller").apply { isAccessible = true }.get(view)
                val loader = controller.javaClass.getDeclaredField("modelLoader").apply { isAccessible = true }.get(controller)
                metadata = loader.javaClass.getDeclaredField("pending").apply { isAccessible = true }.get(loader) as java.util.concurrent.Future<*>?
            }
            metadata?.get(10, java.util.concurrent.TimeUnit.SECONDS)
            com.intellij.testFramework.runInEdtAndWait {
                val root = requireNotNull(root)
                val fixture = WindowFixture(ide.project)
                val content = com.intellij.ui.content.ContentFactory.getInstance().createContent(root, "test", false)
                fixture.contentManager = Proxy.newProxyInstance(com.intellij.ui.content.ContentManager::class.java.classLoader,
                    arrayOf(com.intellij.ui.content.ContentManager::class.java)) { _, method, _ ->
                    when (method.name) {
                        "getContents" -> arrayOf(content)
                        else -> error("Unexpected ContentManager call: ${method.name}")
                    }
                } as com.intellij.ui.content.ContentManager
                try {
                    val action = object : AgentWindowAction(AgentWindowCommand.HISTORY, { fixture.window }) {}
                    val event = event(action, ide.project)
                    action.update(event)
                    assertTrue(event.presentation.isEnabled)
                    action.actionPerformed(event)
                    action.actionPerformed(event)
                    assertFalse(root.isShowing)
                    assertFalse(fixture.visible)
                    assertTrue(fixture.calls.isEmpty(), "history does not create, show, or activate the panel")
                    val history = root.javaClass.getDeclaredField("history").apply { isAccessible = true }.get(root)
                    val requests = history.javaClass.getDeclaredField("requested").apply { isAccessible = true }.get(history) as Set<*>
                    val sessions = root.javaClass.getDeclaredField("sessions").apply { isAccessible = true }.get(root) as com.cursoragent.session.SessionTabs
                    assertEquals(setOf(sessions.snapshot().selectedId), requests)
                    val local = com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(root)
                    assertTrue(local.contains(com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("CursorAgent.History")))
                    fixture.contentManager = null
                    action.update(event)
                    assertFalse(event.presentation.isEnabled)
                    action.actionPerformed(event)
                    assertEquals(1, requests.size)
                    root.dispose()
                    assertTrue(requests.isEmpty())
                    assertFalse(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(root).contains(
                        com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("CursorAgent.History")))
                } finally {
                    root.dispose()
                    com.intellij.openapi.util.Disposer.dispose(content)
                }
            }
        } finally {
            com.intellij.testFramework.runInEdtAndWait { root?.dispose() }
            settings.agentExecutablePath = executable
            com.intellij.testFramework.runInEdtAndWait { ide.tearDown() }
        }
    }

    private fun event(action: AgentWindowAction, project: Project?) = AnActionEvent(
        DataContext { if (CommonDataKeys.PROJECT.`is`(it)) project else null },
        action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )

    private class WindowFixture(providedProject: Project? = null) {
        var contentManager: com.intellij.ui.content.ContentManager? = null
        var projectDisposed = false
        var disposed = false
        var available = true
        var visible = false
        var anchor = ToolWindowAnchor.RIGHT
        val calls = mutableListOf<String>()
        val project = providedProject ?: Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
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
                "getContentManagerIfCreated" -> contentManager
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
