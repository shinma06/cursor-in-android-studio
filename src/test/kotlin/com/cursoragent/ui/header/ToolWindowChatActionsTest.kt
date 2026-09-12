package com.cursoragent.ui.header

import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import com.cursoragent.ui.confirmCloseChats
import com.cursoragent.ui.openedChatEntries
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UpdateSession
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.InputEvent
import kotlin.math.roundToInt
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
        assertEquals(listOf("新規チャット", "履歴", "開いているチャット…", "すべてのチャットを閉じる…", "ブラウザーを開く…", "操作の確認", "実行範囲", "作業場所", "接続方法", "このセッションの内容を要約", "MCPサーバー設定", "設定", "ファイル編集について", "フィードバック…", "ファイルエディター", "上部アイコンの表示"),
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
            { calls.add("mcp") }, { calls.add("settings") }, { calls.add("notice") },
            { calls.add("opened") }, { calls.add("closeAll") }, { calls.add(it) },
            { false }, { calls.add("preview:$it") }, { calls.add("editorSettings") }, { calls.add("icons") }, { calls.add("browser") })
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
        for (label in listOf("MCPサーバー設定", "設定", "ファイル編集について", "ブラウザーを開く…")) {
            actions.gearActions.childActionsOrStubs.first { it.templatePresentation.text == label }.let { it.actionPerformed(event(it)) }
        }
        assertEquals(listOf("summary:1", "new", "history", "mcp", "settings", "notice", "browser"), calls)
        val browser = actions.gearActions.childActionsOrStubs.first { it.templatePresentation.text == "ブラウザーを開く…" }
        assertTrue(browser.isDumbAware)
        assertEquals(ActionUpdateThread.EDT, browser.actionUpdateThread)
        assertTrue(browser.templatePresentation.description!!.contains("手動"))
        assertTrue(browser.templatePresentation.description!!.contains("未接続"))
        running[1] = true
        val browserEvent = event(browser)
        browser.update(browserEvent)
        assertTrue(browserEvent.presentation.isEnabled, "Manual browsing does not require an idle Agent")
        available = false
        val before = calls.toList()
        fun leaves(group: DefaultActionGroup): List<AnAction> = group.childActionsOrStubs.flatMap {
            if (it is DefaultActionGroup) leaves(it) else listOf(it)
        }.filterNot { it is Separator }
        (actions.titleActions + leaves(actions.gearActions)).forEach {
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

    @Test
    fun `hidden icons remain executable in menu and reset changes only those two settings`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState().apply { permissionMode = PermissionMode.AUTO_REVIEW }
        var newChats = 0
        var histories = 0
        var notifications = 0
        val actions = ToolWindowChatActions(settings, { true }, { false }, { AgentTransport.PRINT to false },
            {}, {}, { newChats++ }, { histories++ }, {}, {}, {}, {}, {}, {}, { false }, {}, {}, { notifications++ }, {})
        val visibility = actions.group("上部アイコンの表示").childActionsOrStubs
        visibility.take(2).forEach { (it as ToggleAction).setSelected(event(it), false) }
        actions.titleActions.forEach { action ->
            val toolbar = event(action, ActionUiKind.TOOLBAR)
            val menu = event(action, ActionUiKind.POPUP)
            action.update(toolbar)
            action.update(menu)
            assertFalse(toolbar.presentation.isVisible)
            assertTrue(menu.presentation.isVisible)
            action.actionPerformed(menu)
        }
        assertEquals(1, newChats)
        assertEquals(1, histories)
        visibility.last().actionPerformed(event(visibility.last()))
        assertTrue(settings.showNewChatIcon)
        assertTrue(settings.showHistoryIcon)
        assertEquals(PermissionMode.AUTO_REVIEW, settings.permissionMode)
        assertEquals(3, notifications)
        actions.titleActions.forEach {
            val toolbar = event(it, ActionUiKind.TOOLBAR)
            it.update(toolbar)
            assertTrue(toolbar.presentation.isVisible)
        }
    }

    @Test
    fun `feedback sends only fixed destination urls and preview reads current IDE value`() = SwingUtilities.invokeAndWait {
        val urls = mutableListOf<String>()
        var preview = true
        var previewChanges = 0
        var editorSettings = 0
        val actions = ToolWindowChatActions(AgentSettingsState(), { true }, { false }, { AgentTransport.PRINT to false },
            {}, {}, {}, {}, {}, {}, {}, {}, {}, urls::add, { preview }, { preview = it; previewChanges++ }, { editorSettings++ }, {}, {})
        actions.group("フィードバック…").childActionsOrStubs.forEach { it.actionPerformed(event(it)) }
        assertEquals(listOf("https://github.com/shinma06/cursor-in-android-studio/issues/new",
            "https://prod.cursor.com/help/troubleshooting/reporting-bugs"), urls)
        val editor = actions.group("ファイルエディター").childActionsOrStubs
        val toggle = editor.first() as ToggleAction
        val event = event(toggle)
        assertTrue(toggle.isSelected(event))
        assertEquals(0, previewChanges)
        toggle.setSelected(event, false)
        assertFalse(preview)
        assertEquals(1, previewChanges)
        preview = true // An IDE settings change outside this plugin must be visible on reopen.
        assertTrue(toggle.isSelected(event))
        editor.last().actionPerformed(event(editor.last()))
        assertEquals(1, editorSettings)
    }

    @Test
    fun `chat picker uses stable ids and cancel leaves drafts selection and runs untouched`() {
        val tabs = SessionTabs()
        val first = tabs.snapshot().selected.id
        tabs.updateComposer(first, AgentMode.AGENT, "", "question", 0)
        val token = tabs.beginTurn(first)!!.token
        val second = tabs.open().id
        tabs.updateComposer(second, AgentMode.ASK, "model", "未送信", 2)
        val before = tabs.snapshot()
        val entries = openedChatEntries(before)
        assertEquals(listOf("1. New Agent（実行中）", "2. New Agent"), entries.map { it.second })
        confirmCloseChats(before, confirm = { count, running ->
            assertEquals(2, count)
            assertEquals(1, running)
            false
        }, close = { fail<Unit>("Cancel must not close or dispose any tab") })
        assertEquals(before, tabs.snapshot())
        assertTrue(tabs.accepts(token))
        tabs.move(second, 0)
        assertTrue(tabs.select(entries[1].first))
        assertEquals(second, tabs.snapshot().selectedId)
        assertEquals("未送信", tabs.snapshot().selected.draft)
        assertTrue(tabs.snapshot().tabs.all { it.title == "New Agent" })
    }

    @Test
    fun `confirmed bulk close rejects late callbacks and leaves one fresh tab without touching newer or other projects`() {
        val tabs = SessionTabs()
        val first = tabs.snapshot().selected.id
        tabs.updateComposer(first, AgentMode.AGENT, "", "question", 0)
        val token = tabs.beginTurn(first)!!.token
        tabs.open()
        val otherProject = SessionTabs()
        val otherBefore = otherProject.snapshot()
        val victims = tabs.snapshot()
        confirmCloseChats(victims, { _, _ -> true }) { ids ->
            assertEquals(victims.tabs.map { it.id }, tabs.closeAll(ids).map { it.id })
        }
        assertFalse(tabs.accepts(token))
        assertFalse(tabs.bindChat(token, "late-id"))
        val fresh = tabs.snapshot().selected
        assertEquals(1, tabs.snapshot().tabs.size)
        assertFalse(victims.tabs.any { it.id == fresh.id })
        assertNull(fresh.chatId)
        assertEquals("", fresh.draft)
        assertEquals(otherBefore, otherProject.snapshot())
        var addedWhileConfirming = ""
        confirmCloseChats(tabs.snapshot(), { _, _ ->
            addedWhileConfirming = tabs.open().id
            true
        }) { tabs.closeAll(it) }
        assertEquals(listOf(addedWhileConfirming), tabs.snapshot().tabs.map { it.id })
    }

    @Test
    fun `chat options retain horizontal icon during a real update session and preserve native children`() {
        val children = actions(AgentSettingsState()).titleActions
        val native = DefaultActionGroup(children).apply {
            templatePresentation.text = "Show Options Menu"
            templatePresentation.icon = AllIcons.Actions.More
        }
        val more = ChatOptionsActionGroup(native)
        val event = event(more, ActionUiKind.TOOLBAR).apply {
            updateSession = object : UpdateSession {
                override fun presentation(action: AnAction): Presentation = action.templatePresentation.clone()
            }
        }
        more.update(event)
        assertSame(AllIcons.Actions.MoreHorizontal, more.templatePresentation.icon)
        assertEquals(JBUI.scale(14), event.presentation.icon!!.iconWidth)
        assertEquals("その他の操作", event.presentation.text)
        assertEquals(true, event.presentation.getClientProperty(ActionUtil.HIDE_DROPDOWN_ICON))
        assertTrue(more.isPopup)
        assertEquals(children, more.getChildren(event).toList())
    }

    @Test
    fun `header icons shrink without changing button sizes or menu icons across updates and scales`() = SwingUtilities.invokeAndWait {
        val previousScale = JBUIScale.scale(1f)
        try {
            val actions = actions(AgentSettingsState()).titleActions + ChatOptionsActionGroup(DefaultActionGroup())
            val component = javax.swing.JPanel()
            val context = DataContext { if (PlatformDataKeys.CONTEXT_COMPONENT.`is`(it)) component else null }
            for (scale in listOf(1f, 1.25f, 2f)) {
                JBUIScale.setUserScaleFactorForTest(scale)
                var totalWidth = JBUI.scale(4)
                for (action in actions) {
                    val original = action.templatePresentation.icon!!
                    val toolbar = event(action, ActionUiKind.TOOLBAR, context)
                    val menu = event(action, ActionUiKind.POPUP)
                    repeat(3) {
                        action.update(toolbar)
                        action.update(menu)
                        assertEquals((original.iconWidth * 0.875).roundToInt(), toolbar.presentation.icon!!.iconWidth)
                        assertEquals((original.iconHeight * 0.875).roundToInt(), toolbar.presentation.icon!!.iconHeight)
                        assertSame(original, menu.presentation.icon)
                        assertSame(original, action.templatePresentation.icon)
                    }
                    fun buttonSize(presentation: Presentation) = ActionButton(
                        action, presentation, "CursorAgent.SessionHeader", JBUI.size(22, 34),
                    ).apply { border = JBUI.Borders.empty(1, 2) }.preferredSize
                    assertEquals(buttonSize(action.templatePresentation.clone()), buttonSize(toolbar.presentation))
                    totalWidth += buttonSize(toolbar.presentation).width
                }
                val hide = IconLoader.getIcon("/icons/hide-agent-panel.svg", javaClass)
                val compactHide = compactHeaderIcon(hide, component)
                // The SVG is 16px; an inactive IconLoader can report a 1px placeholder before scaling loads it.
                assertEquals(JBUI.scale(14), compactHide.iconWidth)
                assertEquals(JBUI.scale(14), compactHide.iconHeight)
                val hideAction = object : AnAction("Hide", null, hide) {
                    override fun actionPerformed(e: AnActionEvent) = Unit
                }
                fun hideButtonSize(icon: javax.swing.Icon) = ActionButton(
                    hideAction, hideAction.templatePresentation.clone().apply { this.icon = icon },
                    "CursorAgent.SessionHeader", JBUI.size(22, 34),
                ).apply { border = JBUI.Borders.empty(1, 2) }.preferredSize
                assertEquals(hideButtonSize(hide), hideButtonSize(compactHide))
                totalWidth += hideButtonSize(compactHide).width
                assertEquals(4 * (JBUI.scale(22) + 2 * JBUI.scale(2)) + JBUI.scale(4), totalWidth)
            }
        } finally {
            JBUIScale.setUserScaleFactorForTest(previousScale)
        }
    }

    @Test
    fun `ACP guidance reuses service validation for each candidate and preserves shared selection`() = SwingUtilities.invokeAndWait {
        val project = java.lang.reflect.Proxy.newProxyInstance(com.intellij.openapi.project.Project::class.java.classLoader,
            arrayOf(com.intellij.openapi.project.Project::class.java)) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> "/synthetic-project"
                "isDisposed" -> false
                else -> error("Unexpected project access: ${method.name}")
            }
        } as com.intellij.openapi.project.Project
        val service = com.cursoragent.service.AgentProcessService(project)
        val settings = AgentSettingsState()
        var transport = AgentTransport.ACP
        var running = true
        var available = true
        fun reason(permission: PermissionMode, sandbox: SandboxMode, worktree: WorktreeMode): String? {
            check(available) { "A disposed view must not acquire its service" }
            return service.settingsUnavailableReason(
                AgentTransport.ACP, com.cursoragent.service.TurnSettings("", "", AgentMode.AGENT, permission, sandbox), worktree,
            )
        }
        val actions = ToolWindowChatActions(settings, { available }, { running }, { transport to false },
            { transport = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, { false }, {}, {}, {}, {}, ::reason)
        try {
            for (permission in PermissionMode.entries) for (sandbox in SandboxMode.entries) for (worktree in WorktreeMode.entries) {
                settings.permissionMode = permission
                settings.sandboxMode = sandbox
                settings.worktreeMode = worktree
                val before = com.intellij.util.xmlb.XmlSerializer.serialize(settings)
                for ((caption, options) in listOf(
                    "操作の確認" to PermissionMode.entries,
                    "実行範囲" to SandboxMode.entries,
                    "作業場所" to WorktreeMode.entries,
                )) {
                    val group = actions.group(caption)
                    val groupEvent = event(group)
                    group.update(groupEvent)
                    assertTrue(groupEvent.presentation.description!!.contains("全プロジェクト"))
                    assertTrue(groupEvent.presentation.description!!.contains("次回の送信準備"))
                    assertTrue(groupEvent.presentation.description!!.contains("進行中"))
                    assertTrue(groupEvent.presentation.isEnabled, "Running turns do not block next-turn settings")
                    group.childActionsOrStubs.forEachIndexed { index, action ->
                        val candidate = options[index]
                        val expected = reason(candidate as? PermissionMode ?: permission,
                            candidate as? SandboxMode ?: sandbox, candidate as? WorktreeMode ?: worktree)
                        val e = event(action)
                        action.update(e)
                        assertEquals(expected != null, e.presentation.text!!.contains("ACP要設定変更"))
                        if (expected != null) assertTrue(e.presentation.description!!.contains(expected))
                        assertTrue(e.presentation.isEnabled, "An unsupported current ACP combination must not destroy other print tabs' shared settings")
                        (action as ToggleAction).setSelected(e, false)
                    }
                }
                assertEquals(com.intellij.openapi.util.JDOMUtil.writeElement(before),
                    com.intellij.openapi.util.JDOMUtil.writeElement(com.intellij.util.xmlb.XmlSerializer.serialize(settings)))
            }
            actions.choose("操作の確認", 1)
            assertEquals(PermissionMode.AUTO_REVIEW, settings.permissionMode)
            transport = AgentTransport.PRINT
            running = false
            actions.group("操作の確認").childActionsOrStubs.forEach {
                val e = event(it); it.update(e)
                assertFalse(e.presentation.text!!.contains("ACP要設定変更"))
            }
            val standard = actions.group("操作の確認").childActionsOrStubs[0]
            assertTrue(standard.templatePresentation.description!!.contains("保存済みCLI設定"))
            assertTrue(standard.templatePresentation.description!!.contains("毎回事前確認"))
            assertTrue(actions.group("操作の確認").childActionsOrStubs[2].templatePresentation.description!!.contains("拒否設定"))
            assertTrue(actions.group("実行範囲").childActionsOrStubs[0].templatePresentation.description!!.contains("無効を保証"))
            assertTrue(actions.group("実行範囲").childActionsOrStubs[1].templatePresentation.description!!.contains("実行経路"))
            available = false
            transport = AgentTransport.ACP
            for (caption in listOf("操作の確認", "実行範囲", "作業場所", "接続方法")) {
                val group = actions.group(caption)
                for (action in listOf(group) + group.childActionsOrStubs) {
                    val e = event(action); action.update(e)
                    assertFalse(e.presentation.isEnabled)
                }
            }
        } finally { service.dispose() }
    }

    private fun actions(settings: AgentSettingsState): ToolWindowChatActions {
        val unexpected = { fail<Unit>("Opening or updating a menu must not invoke an action") }
        return ToolWindowChatActions(settings, { true }, { false }, { AgentTransport.PRINT to false },
            { unexpected() }, unexpected, unexpected, { unexpected() }, unexpected, unexpected, unexpected,
            { unexpected() }, unexpected, { unexpected() }, { false }, { unexpected() }, unexpected, {}, unexpected)
    }

    private fun ToolWindowChatActions.group(caption: String) =
        gearActions.childActionsOrStubs.filterIsInstance<DefaultActionGroup>().first { it.templatePresentation.text == caption }

    private fun ToolWindowChatActions.choose(caption: String, index: Int) {
        val action = group(caption).childActionsOrStubs[index] as ToggleAction
        action.setSelected(event(action), true)
    }

    private fun event(action: AnAction, kind: ActionUiKind = ActionUiKind.NONE, context: DataContext = DataContext.EMPTY_CONTEXT) = AnActionEvent(
        context, action.templatePresentation.clone(), "test", kind, null, 0, unusedActionManager,
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
