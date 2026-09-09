package com.cursoragent.ui.header

import com.cursoragent.service.AgentTransport
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.extensions.PluginId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.InputEvent
import javax.swing.SwingUtilities

class ToolWindowChatActionsTest {
    @Test
    fun `all old options survive in native menus and opening preserves saved settings`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState().apply {
            permissionMode = PermissionMode.AUTO_REVIEW
            sandboxMode = SandboxMode.ENABLED
            worktreeMode = WorktreeMode.ISOLATED
        }
        val actions = actions(settings)
        assertEquals(listOf("新規チャット", "履歴"), actions.titleActions.map { it.templatePresentation.text })
        assertEquals(listOf("操作の確認", "実行範囲", "作業場所", "接続方法", "このセッションの内容を要約", "MCPサーバー設定", "設定", "ファイル編集について"),
            actions.gearActions.childActionsOrStubs.filterNot { it is Separator }.map { it.templatePresentation.text })
        actions.titleActions.forEach {
            assertNotNull(it.templatePresentation.icon)
            assertFalse(it.templatePresentation.description.isNullOrBlank())
        }
        actions.gearActions.childActionsOrStubs.filterIsInstance<DefaultActionGroup>().forEach { group ->
            assertTrue(group.isDumbAware, "Settings must remain usable during indexing")
            assertTrue(group.childActionsOrStubs.all { it.isDumbAware })
        }
        val permission = actions.group("操作の確認")
        val event = event(permission)
        permission.update(event)
        assertEquals("操作の確認: AIによる確認", event.presentation.text)
        assertTrue((permission.childActionsOrStubs[1] as ToggleAction).isSelected(event))
        assertEquals(PermissionMode.AUTO_REVIEW, settings.permissionMode)
        assertEquals(SandboxMode.ENABLED, settings.sandboxMode)
        assertEquals(WorktreeMode.ISOLATED, settings.worktreeMode)
    }

    @Test
    fun `native choices preserve default flags and only mutate the chosen setting`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState()
        val actions = actions(settings)
        assertEquals(PermissionMode.ASK_EVERY_TIME, settings.permissionMode)
        assertNull(settings.permissionMode.cliArg)
        actions.choose("実行範囲", 1)
        assertEquals(SandboxMode.ENABLED, settings.sandboxMode)
        assertEquals(PermissionMode.ASK_EVERY_TIME, settings.permissionMode)
        assertEquals(WorktreeMode.DEFAULT, settings.worktreeMode)
        actions.choose("操作の確認", 1)
        assertEquals("--auto-review", settings.permissionMode.cliArg)
        assertEquals(SandboxMode.ENABLED, settings.sandboxMode)
        actions.choose("作業場所", 1)
        assertEquals(WorktreeMode.ISOLATED, settings.worktreeMode)
        val selected = actions.group("操作の確認").childActionsOrStubs[1] as ToggleAction
        selected.setSelected(event(selected), false)
        assertEquals(PermissionMode.AUTO_REVIEW, settings.permissionMode)
    }

    @Test
    fun `actions resolve the current tab and recheck running lock and disposal at invocation`() = SwingUtilities.invokeAndWait {
        var selected = 0
        val running = booleanArrayOf(true, false)
        val transports = arrayOf(AgentTransport.PRINT, AgentTransport.ACP)
        var locked = false
        var available = true
        val calls = mutableListOf<String>()
        val actions = ToolWindowChatActions(AgentSettingsState(), { available }, { running[selected] },
            { transports[selected] to locked }, { transports[selected] = it },
            { calls.add("summary:$selected") }, { calls.add("new") }, { calls.add("history") },
            { calls.add("mcp") }, { calls.add("settings") }, { calls.add("notice") })
        val summary = actions.gearActions.childActionsOrStubs.first { it.templatePresentation.text == "このセッションの内容を要約" }
        val event = event(summary)
        summary.update(event)
        assertFalse(event.presentation.isEnabled)
        summary.actionPerformed(event)
        assertTrue(calls.isEmpty())
        selected = 1
        summary.update(event)
        assertTrue(event.presentation.isEnabled)
        summary.actionPerformed(event)
        assertEquals(listOf("summary:1"), calls)
        running[1] = true // An enabled popup item can become stale before invocation.
        summary.actionPerformed(event)
        assertEquals(1, calls.size)
        running[1] = false
        val transport = actions.group("接続方法")
        val transportEvent = event(transport)
        transport.update(transportEvent)
        assertEquals("接続方法: ACP", transportEvent.presentation.text)
        actions.choose("接続方法", 0)
        assertEquals(AgentTransport.PRINT, transports[1])
        locked = true
        actions.choose("接続方法", 1)
        assertEquals(AgentTransport.PRINT, transports[1])
        transport.update(transportEvent)
        assertFalse(transportEvent.presentation.isEnabled)
        actions.titleActions.forEach { it.actionPerformed(event(it)) }
        for (label in listOf("MCPサーバー設定", "設定", "ファイル編集について")) {
            actions.gearActions.childActionsOrStubs.first { it.templatePresentation.text == label }.let { it.actionPerformed(event(it)) }
        }
        assertEquals(listOf("summary:1", "new", "history", "mcp", "settings", "notice"), calls)
        available = false
        val before = calls.toList()
        (actions.titleActions + actions.gearActions.childActionsOrStubs.filterNot { it is ActionGroup || it is Separator }).forEach {
            val disposedEvent = event(it)
            it.update(disposedEvent)
            assertFalse(disposedEvent.presentation.isEnabled)
            it.actionPerformed(disposedEvent)
        }
        locked = false
        actions.choose("接続方法", 1)
        assertEquals(AgentTransport.PRINT, transports[1])
        assertEquals(before, calls)
    }

    private fun actions(settings: AgentSettingsState): ToolWindowChatActions {
        val unexpected = { fail<Unit>("Opening or updating a menu must not invoke an action") }
        return ToolWindowChatActions(settings, { true }, { false }, { AgentTransport.PRINT to false },
            { unexpected() }, unexpected, unexpected, { unexpected() }, unexpected, unexpected, unexpected)
    }

    private fun ToolWindowChatActions.group(caption: String) =
        gearActions.childActionsOrStubs.filterIsInstance<DefaultActionGroup>().first { it.templatePresentation.text == caption }

    private fun ToolWindowChatActions.choose(caption: String, index: Int) {
        val action = group(caption).childActionsOrStubs[index] as ToggleAction
        action.setSelected(event(action), true)
    }

    private fun event(action: AnAction) = AnActionEvent(
        DataContext.EMPTY_CONTEXT, action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, unusedActionManager,
    )

    // No IDE application or GUI: these actions only read Presentation and their injected callbacks.
    private val unusedActionManager = object : ActionManager() {
        override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu = error("Unused")
        override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar = error("Unused")
        override fun getAction(id: String): AnAction? = error("Unused")
        override fun getId(action: AnAction): String? = error("Unused")
        override fun registerAction(id: String, action: AnAction) = error("Unused")
        override fun registerAction(id: String, action: AnAction, pluginId: PluginId?) = error("Unused")
        override fun unregisterAction(id: String) = error("Unused")
        override fun replaceAction(id: String, action: AnAction) = error("Unused")
        @Deprecated("Required by the IDE ActionManager test stub")
        override fun getActionIds(prefix: String): Array<String> = error("Unused")
        override fun getActionIdList(prefix: String): List<String> = error("Unused")
        override fun isGroup(id: String) = false
        override fun getActionOrStub(id: String): AnAction? = error("Unused")
        override fun addTimerListener(listener: TimerListener) = error("Unused")
        override fun removeTimerListener(listener: TimerListener) = error("Unused")
        override fun tryToExecute(action: AnAction, input: InputEvent?, component: Component?, place: String?, now: Boolean): com.intellij.openapi.util.ActionCallback = error("Unused")
        override fun getKeyboardShortcut(id: String): KeyboardShortcut? = error("Unused")
    }
}
