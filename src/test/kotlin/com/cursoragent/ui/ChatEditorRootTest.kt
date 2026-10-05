package com.cursoragent.ui

import com.cursoragent.actions.AgentPanelActions
import com.cursoragent.actions.AgentPanelCommand
import com.cursoragent.history.ConversationStore
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.composer.context.TerminalContext
import com.cursoragent.ui.editor.ChatEditorPresentation
import com.cursoragent.ui.editor.ChatFileEditorProvider
import com.cursoragent.ui.header.ToolWindowChatActions
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.UUID
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities

class ChatEditorRootTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun get(owner: Any, name: String): Any? = field(owner, name).get(owner)
    private fun show(root: AgentToolWindowRootPanel) = root.javaClass.getDeclaredMethod("showSelected",
        com.cursoragent.history.Conversation::class.java, Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        .invoke(root, null, false, false, false)

    @Test
    fun `native chat actions and history keep their owner while hidden views retain their run and drafts`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("chat editor root").fixture
        fixture.setUp()
        val settings = AgentSettingsState.getInstance()
        val executable = settings.agentExecutablePath
        settings.agentExecutablePath = Path.of(fixture.project.basePath!!, "missing-editor-root-" + UUID.randomUUID()).toString()
        var root: AgentToolWindowRootPanel? = null
        var host: JPanel? = null
        val loads = mutableListOf<(Result<ConversationStore.Loaded>) -> Unit>()
        val shown = mutableListOf<String>()
        var currentMenu: (() -> Boolean)? = null
        lateinit var sessions: SessionTabs
        lateinit var firstId: String
        lateinit var secondId: String
        lateinit var first: ChatEditorPresentation
        lateinit var second: ChatEditorPresentation
        lateinit var firstFile: VirtualFile
        lateinit var secondFile: VirtualFile
        lateinit var firstComposer: ComposerPanel
        lateinit var secondComposer: ComposerPanel
        lateinit var firstHeader: ToolWindowChatActions
        lateinit var firstRun: AgentRun
        var stops = 0
        val terminal = TerminalContext("saved-output", "Build", 2, 3, "copied\\noutput")
        val manager = FileEditorManager.getInstance(fixture.project)
        try {
            runInEdtAndWait {
                val panel = AgentToolWindowRootPanel(fixture.project) {}
                root = panel
                sessions = get(panel, "sessions") as SessionTabs
                val views = get(panel, "views") as Map<*, *>
                firstId = sessions.snapshot().selectedId
                secondId = sessions.open().id
                show(panel)
                val firstView = requireNotNull(views[firstId])
                val secondView = requireNotNull(views[secondId])
                first = get(firstView, "presentation") as ChatEditorPresentation
                second = get(secondView, "presentation") as ChatEditorPresentation
                firstComposer = get(firstView, "composer") as ComposerPanel
                secondComposer = get(secondView, "composer") as ComposerPanel
                firstHeader = get(firstView, "editorActions") as ToolWindowChatActions
                firstComposer.inputArea.text = "first draft"
                firstComposer.promptContext.addTerminal(terminal)
                firstComposer.modeSelector.selectMode(AgentMode.AGENT)
                secondComposer.modeSelector.selectMode(AgentMode.ASK)
                val controller = get(firstView, "controller") as AgentUiController
                sessions.updateComposer(firstId, AgentMode.AGENT, "model", "running prompt", 0)
                val token = requireNotNull(sessions.beginTurn(firstId)?.token)
                firstRun = AgentRun(object : AgentProcessListener {}).apply { attachCancellation { stops++ } }
                field(controller, "activeRun").set(controller, firstRun)
                // TestEditorManagerImpl uses this fixture-only key instead of consulting provider EPs.
                firstFile = get(first, "file") as VirtualFile
                secondFile = get(second, "file") as VirtualFile
                listOf(firstFile, secondFile).forEach { it.putUserData(FileEditorProvider.KEY, ChatFileEditorProvider()) }
                first.focusInEditor()
                second.focusInEditor()
                val firstEditor = manager.getAllEditors(firstFile).single()
                val secondEditor = manager.getAllEditors(secondFile).single()
                host = JPanel().apply {
                    add(JRootPane().apply {
                        glassPane = com.intellij.openapi.wm.impl.IdeGlassPaneImpl(this, false)
                        contentPane.add(JPanel().apply { add(panel); add(firstEditor.component); add(secondEditor.component) })
                    })
                    addNotify()
                }
                panel.isVisible = false
                firstEditor.selectNotify()
                assertTrue(firstComposer.isShowing)
                assertFalse(panel.isShowing)
                assertEquals(firstId, sessions.snapshot().selectedId)
                assertTrue(panel.historyShortcutAvailable)
                val mode = ActionManager.getInstance().getAction(AgentPanelCommand.MODE_MENU.actionId)
                fun event(component: javax.swing.JComponent): AnActionEvent {
                    val data = Utils.createAsyncDataContext(component)
                    return AnActionEvent.createEvent(DataContext { key ->
                        if (CommonDataKeys.PROJECT.`is`(key)) fixture.project else data.getData(key)
                    }, null, "test", ActionUiKind.NONE, null)
                }
                val firstEvent = event(firstComposer.inputArea)
                assertNotNull(firstEvent.getData(AgentPanelActions.KEY))
                assertTrue(ActionUtil.getActions(firstEditor.component).contains(mode))
                mode.update(firstEvent)
                assertTrue(firstEvent.presentation.isEnabled)
                mode.actionPerformed(firstEvent)
                assertEquals(AgentMode.PLAN, firstComposer.selection.mode)
                assertEquals(AgentMode.ASK, secondComposer.selection.mode)
                secondEditor.selectNotify()
                mode.update(firstEvent)
                assertFalse(firstEvent.presentation.isEnabled)
                mode.actionPerformed(firstEvent)
                assertEquals(AgentMode.ASK, secondComposer.selection.mode, "a retained first chat event cannot affect the second")
                mode.actionPerformed(event(secondEditor.component))
                assertEquals(AgentMode.AGENT, secondComposer.selection.mode)
                assertTrue(sessions.accepts(token))
                assertEquals(0, stops)

                val history = get(panel, "history") as PastChatsCoordinator
                field(history, "load").set(history, { callback: (Result<ConversationStore.Loaded>) -> Unit -> loads.add(callback); Unit })
                field(history, "showMenu").set(history,
                    { _: ConversationStore.Loaded, current: () -> Boolean, closed: () -> Unit ->
                        shown.add(sessions.snapshot().selectedId)
                        currentMenu = current
                        Disposer.newDisposable().also { Disposer.register(it, com.intellij.openapi.Disposable(closed)) }
                    })
                firstHeader.historyAction.actionPerformed(event(firstEditor.component))
                assertEquals(firstId, sessions.snapshot().selectedId, "the editor header must select its source even after another chat was active")
                assertEquals(1, loads.size)
                secondEditor.selectNotify()
                loads[0](Result.success(ConversationStore.Loaded(emptyList(), 0)))
            }
            SwingUtilities.invokeAndWait {}
            runInEdtAndWait {
                assertTrue(shown.isEmpty(), "a late first-owner history load cannot open in the second chat")
                root!!.requestHistory()
                assertEquals(2, loads.size)
                loads[1](Result.success(ConversationStore.Loaded(emptyList(), 0)))
            }
            SwingUtilities.invokeAndWait {}
            runInEdtAndWait {
                assertEquals(listOf(secondId), shown)
                assertTrue(currentMenu!!.invoke())
                assertTrue(sessions.hide(firstId))
                root!!.javaClass.getDeclaredMethod("refreshStrip").apply { isAccessible = true }.invoke(root)
                assertFalse(first.inEditor)
                assertFalse(manager.isFileOpen(firstFile))
                assertEquals(secondId, sessions.snapshot().selectedId, "closing a hidden native handle must not change the selected owner")
                assertEquals("first draft", firstComposer.inputArea.text)
                assertEquals(listOf(terminal), firstComposer.promptContext.draft.snapshot().terminals)
                assertTrue(firstRun.isActive)
                assertEquals(0, stops)
                manager.closeFile(secondFile)
                assertFalse(second.inEditor)
                assertFalse(currentMenu!!.invoke(), "a menu cannot remain actionable after its editor closes")
                assertTrue(first.alive)
                assertTrue(second.alive)
                root!!.dispose()
                assertFalse(first.alive)
                assertFalse(second.alive)
                assertFalse(firstFile.isValid)
                assertFalse(secondFile.isValid)
            }
        } finally {
            runInEdtAndWait { root?.dispose(); host?.removeNotify() }
            settings.agentExecutablePath = executable
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
