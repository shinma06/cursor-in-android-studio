package com.cursoragent.ui

import com.cursoragent.history.ChatMessage
import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationHistory
import com.cursoragent.history.SavedTurn
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.ComposerPanel
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SearchTextField
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.JList
import javax.swing.SwingUtilities
import javax.swing.Timer

class ChatOpenTabTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun get(owner: Any, name: String): Any? = field(owner, name).get(owner)
    private fun invoke(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    private fun perform(action: AnAction) = action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "test", DataContext.EMPTY_CONTEXT))

    @Test
    fun `sidebar new tab opens latest body once keeps owners and rejects late menu loads`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("sidebar open tab").fixture
        fixture.setUp()
        val settings = AgentSettingsState.getInstance()
        val executable = settings.agentExecutablePath
        settings.agentExecutablePath = Path.of(fixture.project.basePath!!, "missing-open-tab-${UUID.randomUUID()}").toString()
        val properties = PropertiesComponent.getInstance(fixture.project)
        val collapsedKey = "CursorAgent.chatSection.archived.collapsed"
        val collapsed = properties.getValue(collapsedKey)
        val store = fixture.project.getService(ConversationHistory::class.java)
        val saved = Conversation(updatedMs = 10, turns = listOf(SavedTurn(messages = listOf(ChatMessage(role = "user", text = "saved body")))))
        val archive = ChatArchiveState(properties)
        val savedEntry = RecentChatEntry(RecentChatId.Body(saved.id), "saved body", 10, saved.transport)
        val metadata = mutableListOf<Future<*>>()
        var panel: AgentToolWindowRootPanel? = null
        var host: javax.swing.JPanel? = null
        lateinit var sidebar: AllChatsView
        lateinit var sessions: SessionTabs
        lateinit var original: com.cursoragent.session.SessionTab
        lateinit var originalView: Any
        lateinit var composer: ComposerPanel
        lateinit var queue: PromptQueue
        lateinit var run: AgentRun
        var stops = 0
        fun drain() {
            val done = CompletableFuture<Unit>()
            store.load { done.complete(Unit) }
            done.get(10, TimeUnit.SECONDS)
            SwingUtilities.invokeAndWait {}
        }
        fun searchNow() {
            // Opening a tab schedules another sidebar history load on the same writer.
            drain()
            lateinit var worker: Future<*>
            runInEdtAndWait {
                (get(sidebar, "debounce") as Timer).stop()
                invoke(sidebar, "runSearch")
                worker = get(sidebar, "worker") as Future<*>
            }
            worker.get(10, TimeUnit.SECONDS)
            SwingUtilities.invokeAndWait {}
        }
        fun select(id: RecentChatId) {
            val list = get(sidebar, "list") as JList<*>
            list.selectedIndex = (0 until list.model.size).first {
                (list.model.getElementAt(it) as? AllChatRow.Chat)?.hit?.entry?.id == id
            }
        }
        fun action() = requireNotNull(invoke(sidebar, "openInNewTabAction") as? AnAction)
        try {
            runInEdtAndWait {
                properties.setValue(collapsedKey, false, true)
                assertTrue(archive.set(savedEntry, true, 20))
                store.save(saved) {}
                val root = AgentToolWindowRootPanel(fixture.project) {}
                panel = root
                // A lightweight Swing peer exercises visibility guards without creating a native window.
                host = javax.swing.JPanel().apply {
                    add(javax.swing.JRootPane().apply {
                        glassPane = com.intellij.openapi.wm.impl.IdeGlassPaneImpl(this, false)
                        contentPane.add(root)
                    })
                    addNotify()
                }
                assertTrue(root.isShowing)
                sessions = get(root, "sessions") as SessionTabs
                original = sessions.snapshot().selected
                originalView = (get(root, "views") as Map<*, *>)[original.id]!!
                composer = get(originalView, "composer") as ComposerPanel
                composer.inputArea.text = "retain draft"
                val controller = get(originalView, "controller") as AgentUiController
                queue = get(controller, "queue") as PromptQueue
                queue.add("retain queued", AgentMode.PLAN, "model")
                sessions.updateComposer(original.id, AgentMode.AGENT, "model", "run", 0)
                val token = sessions.beginTurn(original.id)!!.token
                run = AgentRun(object : AgentProcessListener {}).apply { attachCancellation { stops++ } }
                field(controller, "activeRun").set(controller, run)
                assertTrue(sessions.accepts(token))
                root.javaClass.getDeclaredMethod("setAllChatsVisible", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(root, true)
                sidebar = get(root, "allChatsSidebar") as AllChatsView
                // The fixture has no screen coordinates; text accessibility must not query a native window.
                (get(sidebar, "search") as SearchTextField).isVisible = false
            }
            drain()
            searchNow()
            runInEdtAndWait {
                select(savedEntry.id)
                val menu = action()
                assertEquals("新しいタブで開く", menu.templatePresentation.text)
                perform(menu)
                (get(sidebar, "search") as SearchTextField).text = "changed while loading"
                perform(menu)
            }
            drain()
            runInEdtAndWait {
                assertEquals(1, sessions.snapshot().tabs.size, "a stale originating search must invalidate the queued load")
                (get(sidebar, "search") as SearchTextField).text = ""
            }
            searchNow()
            val latest = saved.copy(updatedMs = 40, turns = listOf(SavedTurn(messages = listOf(ChatMessage(role = "user", text = "newer saved body")))))
            runInEdtAndWait {
                select(savedEntry.id)
                store.save(latest) {}
                val menu = action()
                perform(menu)
                perform(menu)
            }
            drain()
            searchNow()
            runInEdtAndWait {
                assertEquals(2, sessions.snapshot().visibleTabs.size)
                val selected = sessions.snapshot().selected
                assertEquals(saved.id, selected.conversationId)
                val views = get(panel!!, "views") as Map<*, *>
                assertEquals(latest.copy(turns = latest.turns.map { it.copy(state = "interrupted") }),
                    (get(views[selected.id]!!, "controller") as AgentUiController).conversationSnapshot())
                assertSame(originalView, views[original.id])
                assertEquals("retain draft", composer.inputArea.text)
                assertEquals(listOf("retain queued"), queue.snapshot().map { it.text })
                assertNotNull(sessions.snapshot().tabs.first { it.id == original.id }.run)
                assertEquals(0, stops)
                assertTrue(archive.apply(savedEntry).archived, "opening does not restore")
                select(savedEntry.id)
                perform(action())
                assertEquals(2, sessions.snapshot().tabs.size, "an already open conversation has exactly one owner")
                assertEquals(selected.id, sessions.snapshot().selectedId)
                sessions.hide(original.id)
                sidebar.refreshOpenEntries()
            }
            searchNow()
            runInEdtAndWait {
                select(RecentChatId.Body(original.conversationId))
                perform(action())
                assertEquals(original.id, sessions.snapshot().selectedId)
                assertEquals(2, sessions.snapshot().visibleTabs.size)
                assertSame(originalView, (get(panel!!, "views") as Map<*, *>)[original.id])
                assertEquals("retain draft", composer.inputArea.text)
                assertEquals(0, stops)
                val controller = get(originalView, "controller") as AgentUiController
                field(controller, "activeRun").set(controller, null)
                run.complete(0)
                (get(panel!!, "views") as Map<*, *>).values.filterNotNull().forEach {
                    (get(get(get(it, "controller")!!, "modelLoader")!!, "pending") as Future<*>?)?.let(metadata::add)
                }
            }
        } finally {
            runInEdtAndWait {
                host?.removeNotify()
                panel?.dispose()
                properties.unsetValue("CursorAgent.chatArchive." + pinnedChatKey(savedEntry.id))
                if (collapsed == null) properties.unsetValue(collapsedKey) else properties.setValue(collapsedKey, collapsed)
            }
            metadata.forEach { runCatching { it.get(10, TimeUnit.SECONDS) } }
            store.delete(saved.id) {}
            drain()
            settings.agentExecutablePath = executable
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
