package com.cursoragent.ui

import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.ComposerPanel
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SearchTextField
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.InputMethodEvent
import java.text.AttributedString
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JList
import javax.swing.Timer
import javax.swing.SwingUtilities

class ChatArchiveTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun get(owner: Any, name: String): Any? = field(owner, name).get(owner)
    private fun invoke(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    private fun entry(id: String = UUID.randomUUID().toString()) = RecentChatEntry(RecentChatId.Body(id), "Android Build", 10, AgentTransport.PRINT)

    @Test
    fun `sidebar pin menu shares buttons and rejects stale actions without restoring archived chats`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("sidebar pin menu").fixture
        fixture.setUp()
        val properties = PropertiesComponent.getInstance(fixture.project)
        val previousPins = properties.getList("CursorAgent.pinnedChats")
        val target = entry()
        val other = entry().copy(updatedMs = 1)
        val archive = ChatArchiveState(properties)
        val archiveKey = "CursorAgent.chatArchive." + pinnedChatKey(target.id)
        val views = mutableListOf<AllChatsView>()
        var valid = true
        fun pins() = properties.getList("CursorAgent.pinnedChats").orEmpty().mapNotNull(::pinnedChatId).toSet()
        fun action(view: AllChatsView) = invoke(view, "pinAction") as? com.intellij.openapi.actionSystem.AnAction
        fun perform(action: com.intellij.openapi.actionSystem.AnAction) {
            action.actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                action, null, "test", com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT))
        }
        fun searchNow(view: AllChatsView) {
            lateinit var worker: Future<*>
            runInEdtAndWait {
                (get(view, "debounce") as Timer).stop()
                invoke(view, "runSearch")
                worker = get(view, "worker") as Future<*>
            }
            worker.get(10, TimeUnit.SECONDS)
            SwingUtilities.invokeAndWait {}
        }
        try {
            runInEdtAndWait {
                properties.setList("CursorAgent.pinnedChats", emptyList())
                for (mode in ChatListMode.entries) views += AllChatsView(fixture.project, mode,
                    { listOf(target, other) }, { target.id }, { valid }, { fail("pin must not open a chat") },
                    { fail("pin must not archive or restore") }).apply {
                    useLoadedHistory(com.cursoragent.history.ConversationStore.Loaded(emptyList(), 0))
                }
            }
            views.forEach(::searchNow)
            val sidebar = views.first()
            runInEdtAndWait {
                views.drop(1).forEach { assertNull(action(it)) }
                assertNotNull((get(sidebar, "moreButton") as JButton).parent, "menu also works without bulk archive callback")
                val pin = requireNotNull(action(sidebar))
                assertEquals("一覧に固定", pin.templatePresentation.text)
                perform(pin)
                perform(pin)
                assertEquals(setOf(target.id), pins())
                assertEquals("固定を解除", requireNotNull(action(sidebar)).templatePresentation.text)
                (get(sidebar, "pinButton") as JButton).doClick()
                assertTrue(pins().isEmpty())
                val beforePins = requireNotNull(action(sidebar))
                properties.setList("CursorAgent.pinnedChats", listOf(pinnedChatKey(other.id)))
                perform(beforePins)
                assertEquals(setOf(other.id), pins())
                val beforeSelection = requireNotNull(action(sidebar))
                val list = get(sidebar, "list") as JList<*>
                val oldIndex = list.selectedIndex
                list.clearSelection()
                perform(beforeSelection)
                list.selectedIndex = oldIndex
                assertEquals(setOf(other.id), pins())
                val beforeIme = requireNotNull(action(sidebar))
                field(sidebar, "isComposing").set(sidebar, true)
                perform(beforeIme)
                field(sidebar, "isComposing").set(sidebar, false)
                valid = false
                perform(beforeIme)
                valid = true
                val beforeQuery = requireNotNull(action(sidebar))
                (get(sidebar, "search") as SearchTextField).text = "different"
                perform(beforeQuery)
                assertEquals(setOf(other.id), pins())
                (get(sidebar, "search") as SearchTextField).text = ""
                assertTrue(archive.set(target, true, 30))
                @Suppress("UNCHECKED_CAST")
                (get(sidebar, "collapsed") as MutableSet<ChatSection>).remove(ChatSection.ARCHIVED)
                sidebar.refreshOpenEntries()
            }
            searchNow(sidebar)
            runInEdtAndWait {
                val list = get(sidebar, "list") as JList<*>
                list.selectedIndex = (0 until list.model.size).first {
                    (list.model.getElementAt(it) as? AllChatRow.Chat)?.hit?.entry?.id == target.id
                }
                perform(requireNotNull(action(sidebar)))
                assertEquals(setOf(other.id, target.id), pins())
                assertTrue(archive.apply(target).archived)
                assertEquals(ChatSection.ARCHIVED, (get(sidebar, "rows") as List<*>).filterIsInstance<AllChatRow.Section>().last().section)
                val stale = requireNotNull(action(sidebar))
                sidebar.suspendUpdates()
                perform(stale)
                sidebar.dispose()
                perform(stale)
                assertEquals(setOf(other.id, target.id), pins())
            }
        } finally {
            runInEdtAndWait {
                views.forEach(AllChatsView::dispose)
                properties.unsetValue(archiveKey)
                if (previousPins == null) properties.unsetValue("CursorAgent.pinnedChats") else properties.setList("CursorAgent.pinnedChats", previousPins)
                fixture.tearDown()
            }
        }
    }

    @Test
    fun `prior archive menu captures all search hits beyond collapsed pages and refuses stale replay`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("prior archive menu").fixture
        fixture.setUp()
        val properties = PropertiesComponent.getInstance(fixture.project)
        val previousPins = properties.getList("CursorAgent.pinnedChats")
        val anchor = entry().copy(updatedMs = System.currentTimeMillis())
        val old = (1..14).map { entry().copy(updatedMs = it.toLong()) }
        val unrelated = entry().copy(title = "Kotlin unrelated")
        val requests = mutableListOf<ChatArchivePriorRequest>()
        val choices = mutableListOf<RecentChatId>()
        val views = mutableListOf<AllChatsView>()
        var valid = true
        fun searchNow(view: AllChatsView) {
            lateinit var worker: Future<*>
            runInEdtAndWait {
                (get(view, "debounce") as Timer).stop()
                invoke(view, "runSearch")
                worker = get(view, "worker") as Future<*>
            }
            worker.get(10, TimeUnit.SECONDS)
            SwingUtilities.invokeAndWait {}
        }
        fun action(view: AllChatsView) = invoke(view, "archivePriorAction") as? com.intellij.openapi.actionSystem.AnAction
        fun perform(action: com.intellij.openapi.actionSystem.AnAction) = action.actionPerformed(
            com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(action, null, "test", com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT))
        try {
            runInEdtAndWait {
                properties.setList("CursorAgent.pinnedChats", listOf(pinnedChatKey(old.first().id)))
                for (mode in ChatListMode.entries) {
                    views += AllChatsView(fixture.project, mode, { old + anchor + unrelated }, { anchor.id }, { valid }, choices::add, {},
                        onArchivePrior = requests::add).apply {
                        useLoadedHistory(com.cursoragent.history.ConversationStore.Loaded(emptyList(), 0))
                        (get(this, "search") as SearchTextField).text = "Android"
                    }
                }
            }
            views.forEach(::searchNow)
            val sidebar = views.first()
            runInEdtAndWait {
                views.drop(1).forEach { assertNull(action(it)); assertNull((get(it, "moreButton") as JButton).parent) }
                val list = get(sidebar, "list") as JList<*>
                assertTrue(list.mouseListeners.any { it is com.intellij.ui.PopupHandler }, "native ShowPopupMenu delivers its mouse trigger here")
                @Suppress("UNCHECKED_CAST")
                (get(sidebar, "collapsed") as MutableSet<ChatSection>).add(ChatSection.OLDER)
                sidebar.javaClass.getDeclaredMethod("rebuildRows", SidebarChatTarget::class.java).apply { isAccessible = true }
                    .invoke(sidebar, SidebarChatTarget.Chat(anchor.id))
                assertEquals(2, (0 until list.model.size).count { list.model.getElementAt(it) is AllChatRow.Chat })
                list.setSize(400, 400)
                val bounds = requireNotNull(list.getCellBounds(list.selectedIndex, list.selectedIndex))
                fun click(popup: Boolean = false, consumed: Boolean = false, modifiers: Int = 0) {
                    val event = java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, modifiers,
                        bounds.x + 1, bounds.y + 1, 1, popup, java.awt.event.MouseEvent.BUTTON1)
                    if (consumed) event.consume()
                    // Exercise row activation without showing an OS popup in the SDK fixture.
                    list.mouseListeners.filterNot { it is com.intellij.ui.PopupHandler }.forEach { it.mouseClicked(event) }
                }
                click(popup = true)
                click(consumed = true)
                if (com.intellij.openapi.util.SystemInfo.isMac) click(modifiers = java.awt.event.InputEvent.CTRL_DOWN_MASK)
                assertTrue(choices.isEmpty(), "context-menu clicks must not open the chat first")
                click()
                assertEquals(listOf(anchor.id), choices)
                // Retained hits are authoritative even when only the anchor and pinned section remain visible.
                val menu = requireNotNull(action(sidebar))
                assertEquals(com.intellij.openapi.actionSystem.ActionUpdateThread.EDT, menu.actionUpdateThread)
                perform(menu)
                perform(menu)
                assertEquals(1, requests.size)
                assertEquals(15, requests.single().candidates.size, "all 14 older matches plus the anchor, not six rendered rows")
                assertEquals(old.drop(1).map { it.id }.toSet(), priorArchiveCandidates(requests.single().candidates,
                    anchor.updatedMs, requests.single().pinned).map { it.id }.toSet())
                val stale = requireNotNull(action(sidebar))
                properties.setList("CursorAgent.pinnedChats", emptyList())
                assertFalse(requests.single().isCurrent())
                perform(stale)
                assertEquals(1, requests.size)
                properties.setList("CursorAgent.pinnedChats", listOf(pinnedChatKey(old.first().id)))
                val beforeQuery = requireNotNull(action(sidebar))
                (get(sidebar, "search") as SearchTextField).text = "Kotlin"
                perform(beforeQuery)
                assertNull(action(sidebar))
                assertEquals(1, requests.size)
                (get(sidebar, "search") as SearchTextField).text = "Android"
            }
            searchNow(sidebar)
            runInEdtAndWait {
                val beforeIme = requireNotNull(action(sidebar))
                field(sidebar, "isComposing").set(sidebar, true)
                perform(beforeIme)
                assertNull(action(sidebar))
                field(sidebar, "isComposing").set(sidebar, false)
                valid = false
                perform(beforeIme)
                valid = true
                sidebar.suspendUpdates()
                perform(beforeIme)
                sidebar.dispose()
                perform(beforeIme)
                assertEquals(1, requests.size)
            }
        } finally {
            runInEdtAndWait {
                views.forEach(AllChatsView::dispose)
                if (previousPins == null) properties.unsetValue("CursorAgent.pinnedChats") else properties.setList("CursorAgent.pinnedChats", previousPins)
                fixture.tearDown()
            }
        }
    }

    @Test
    fun `prior archive rereads history and preserves active owner draft queue and failed targets`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("prior archive owner").fixture
        fixture.setUp()
        val settings = AgentSettingsState.getInstance()
        val executable = settings.agentExecutablePath
        settings.agentExecutablePath = java.nio.file.Path.of(fixture.project.basePath!!, "missing-prior-agent-${UUID.randomUUID()}").toString()
        val properties = PropertiesComponent.getInstance(fixture.project)
        val previousPins = properties.getList("CursorAgent.pinnedChats")
        val store = fixture.project.getService(com.cursoragent.history.ConversationHistory::class.java)
        val saved = com.cursoragent.history.Conversation(updatedMs = 5)
        val keys = mutableListOf<String>()
        val metadata = mutableListOf<Future<*>>()
        var root: AgentToolWindowRootPanel? = null
        lateinit var request: ChatArchivePriorRequest
        lateinit var state: ChatArchiveState
        lateinit var sessions: SessionTabs
        lateinit var composer: ComposerPanel
        lateinit var queue: PromptQueue
        lateinit var run: AgentRun
        lateinit var target: com.cursoragent.session.SessionTab
        lateinit var entries: List<RecentChatEntry>
        var stops = 0
        var failedStops = 0
        var valid = true
        var previousDialog: TestDialog? = null
        fun drain() {
            val completed = java.util.concurrent.CompletableFuture<Unit>()
            store.load { completed.complete(Unit) }
            completed.get(10, TimeUnit.SECONDS)
            SwingUtilities.invokeAndWait {}
        }
        fun archive() = root!!.javaClass.getDeclaredMethod("archivePriorChats", ChatArchivePriorRequest::class.java)
            .apply { isAccessible = true }.invoke(root, request)
        try {
            runInEdtAndWait {
                val panel = AgentToolWindowRootPanel(fixture.project) {}
                root = panel
                sessions = get(panel, "sessions") as SessionTabs
                val views = get(panel, "views") as Map<*, *>
                fun show() = panel.javaClass.getDeclaredMethod("showSelected", com.cursoragent.history.Conversation::class.java,
                    Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(panel, null, false)
                fun controller() = get(views[sessions.snapshot().selectedId]!!, "controller") as AgentUiController
                fun time(value: Long) {
                    val recorder = get(controller(), "recorder") as com.cursoragent.history.ConversationRecorder
                    recorder.conversation = recorder.conversation.copy(updatedMs = value)
                }
                target = sessions.snapshot().selected
                time(10)
                val firstView = views[target.id]!!
                val first = controller()
                composer = get(firstView, "composer") as ComposerPanel
                composer.inputArea.text = "keep this draft"
                queue = get(first, "queue") as PromptQueue
                queue.add("queued", AgentMode.PLAN, "exact-model")
                sessions.updateComposer(target.id, AgentMode.AGENT, "model", "run", 0)
                val token = sessions.beginTurn(target.id)!!.token
                run = AgentRun(object : AgentProcessListener {
                    override fun onStopped() { sessions.finishTurn(token) }
                }).apply { attachCancellation { stops++ } }
                field(first, "activeRun").set(first, run)
                sessions.open(); show(); time(20)
                val pinnedId = RecentChatId.Body(sessions.snapshot().selected.conversationId)
                sessions.open(); show(); time(30)
                field(controller(), "activeRun").set(controller(), AgentRun(object : AgentProcessListener {}).apply {
                    attachCancellation { failedStops++; throw java.io.IOException("synthetic stop failure") }
                })
                sessions.open(); show(); time(100)
                val anchorId = RecentChatId.Body(sessions.snapshot().selected.conversationId)
                sessions.open(); show(); time(100)
                sessions.select(target.id); show()
                state = ChatArchiveState(properties)
                entries = (invoke(panel, "openRecentEntries") as List<*>).filterIsInstance<RecentChatEntry>()
                properties.setList("CursorAgent.pinnedChats", listOf(pinnedChatKey(pinnedId)))
                request = ChatArchivePriorRequest(entries.first { it.id == anchorId }, entries + RecentChatEntry(RecentChatId.Body(saved.id), "saved", 5, AgentTransport.PRINT),
                    "", setOf(pinnedId)) { valid }
                keys += request.candidates.map { "CursorAgent.chatArchive." + pinnedChatKey(it.id) }
                // The disk writer must flush a newer closed body before the bulk action chooses its targets.
                store.save(saved.copy(updatedMs = 101)) {}
                archive()
                valid = false
            }
            drain()
            runInEdtAndWait {
                assertEquals(0, stops, "late load does not stop anything after the originating menu is invalid")
                valid = true
                archive()
                properties.setList("CursorAgent.pinnedChats", emptyList())
            }
            drain()
            runInEdtAndWait {
                assertEquals(0, stops, "pin changes while loading invalidate the whole request")
                properties.setList("CursorAgent.pinnedChats", request.pinned.map(::pinnedChatKey))
                previousDialog = TestDialogManager.setTestDialog { fail("bulk archive does not use the per-chat confirmation") }
                archive()
            }
            drain()
            runInEdtAndWait {
                assertEquals(1, stops)
                assertEquals(1, failedStops)
                assertEquals(listOf(10L), entries.filter { state.apply(it).archived }.map { it.updatedMs })
                assertFalse(state.apply(request.candidates.last()).archived, "the newer disk body is not archived from its old row timestamp")
                assertEquals(target.id, sessions.snapshot().selectedId, "bulk archive keeps the current view")
                assertEquals("keep this draft", composer.inputArea.text)
                assertEquals(listOf("queued"), queue.snapshot().map { it.text })
                assertTrue(queue.paused)
                assertNotNull(sessions.snapshot().selected.run, "requesting Stop does not release run ownership")
                run.complete(0)
                assertNull(sessions.snapshot().selected.run)
                assertEquals(target.id, sessions.snapshot().selectedId)
                assertEquals(request.pinned.map(::pinnedChatKey), properties.getList("CursorAgent.pinnedChats"))
                val views = get(root!!, "views") as Map<*, *>
                views.values.filterNotNull().forEach {
                    (get(get(get(it, "controller")!!, "modelLoader")!!, "pending") as Future<*>?)?.let(metadata::add)
                }
            }
        } finally {
            metadata.forEach { it.get(10, TimeUnit.SECONDS) }
            store.delete(saved.id) {}
            drain()
            runInEdtAndWait {
                previousDialog?.let(TestDialogManager::setTestDialog)
                root?.dispose()
                keys.forEach(properties::unsetValue)
                if (previousPins == null) properties.unsetValue("CursorAgent.pinnedChats") else properties.setList("CursorAgent.pinnedChats", previousPins)
            }
            settings.agentExecutablePath = executable
            runInEdtAndWait { fixture.tearDown() }
        }
    }

    @Test
    fun `archive metadata roundtrips independently of bodies pins and legacy namespaces`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("archive metadata").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val properties = PropertiesComponent.getInstance(fixture.project)
                val state = ChatArchiveState(properties)
                val body = entry()
                val legacy = body.copy(id = RecentChatId.LegacyPrint((body.id as RecentChatId.Body).id))
                val key = "CursorAgent.chatArchive." + pinnedChatKey(body.id)
                try {
                    assertEquals(body, state.apply(body))
                    assertTrue(state.set(body, true, 20))
                    val archived = ChatArchiveState(properties).apply(body)
                    assertTrue(archived.archived)
                    assertEquals(20, archived.updatedMs)
                    assertEquals(20, archived.archiveChangedMs)
                    assertEquals(legacy, state.apply(legacy))
                    assertEquals(30, state.apply(body.copy(updatedMs = 30)).updatedMs)
                    assertTrue(state.apply(body.copy(title = "late save")).archived)
                    assertFalse(state.set(body, true, 21), "stale view cannot write")
                    assertFalse(state.set(archived, true, 21), "repeat does not update recency")
                    assertTrue(state.set(archived, false, 20))
                    val restored = state.apply(body)
                    assertFalse(restored.archived)
                    assertEquals(21, restored.updatedMs, "same-clock changes still invalidate stale callbacks")
                    assertFalse(state.matches(archived))
                    assertTrue(state.set(restored, true, 19), "clock rollback cannot resurrect the original stamp")
                    assertEquals(22, state.apply(body).archiveChangedMs)
                    for (malformed in listOf("", "1", "1:0", "1:-1", "1:no", "2:20", "1:20:extra", "1:999999999999999999999")) {
                        properties.setValue(key, malformed)
                        assertEquals(body, state.apply(body))
                        assertEquals(malformed, properties.getValue(key), "read preserves unknown data")
                    }
                } finally { properties.unsetValue(key) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

    @Test
    fun `real list rejects stale search IME suspended and disposed archive actions`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("archive list").fixture
        fixture.setUp()
        val original = entry()
        lateinit var view: AllChatsView
        lateinit var archive: ChatArchiveState
        lateinit var search: SearchTextField
        lateinit var button: JButton
        lateinit var list: JList<*>
        var valid = true
        val calls = mutableListOf<RecentChatEntry>()
        val chosen = mutableListOf<RecentChatId>()
        fun searchNow() {
            var worker: Future<*>? = null
            runInEdtAndWait {
                (get(view, "debounce") as Timer).stop()
                invoke(view, "runSearch")
                worker = get(view, "worker") as Future<*>?
            }
            worker?.get(10, TimeUnit.SECONDS)
            // Match the producer's Swing queue; the IDE invocation queue can overtake its callback.
            SwingUtilities.invokeAndWait { }
        }
        try {
            runInEdtAndWait {
                archive = ChatArchiveState(PropertiesComponent.getInstance(fixture.project))
                view = AllChatsView(fixture.project, ChatListMode.HISTORY, { listOf(original) }, { original.id }, { valid }, chosen::add, onArchive = {
                    assertTrue(it.isCurrent())
                    calls.add(it.entry)
                    assertTrue(archive.set(it.entry, !it.entry.archived))
                })
                search = get(view, "search") as SearchTextField
                button = get(view, "archiveButton") as JButton
                list = get(view, "list") as JList<*>
                assertFalse(button.isEnabled)
                invoke(view, "toggleArchive")
                assertTrue(calls.isEmpty())
                view.refreshOpenEntries()
            }
            searchNow()
            runInEdtAndWait {
                assertTrue(button.isEnabled)
                // Another view archived the selected identity after the search finished.
                assertTrue(archive.set(original, true))
                invoke(view, "toggleArchive")
                assertTrue(calls.isEmpty())
            }
            searchNow()
            runInEdtAndWait {
                assertEquals(1, list.model.size)
                assertTrue(list.model.getElementAt(0) is AllChatRow.Section)
                list.selectedIndex = 0
                view.javaClass.getDeclaredMethod("choose", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(view, true)
                list.selectedIndex = 1
                assertEquals("復元", button.text)
                assertTrue(button.isEnabled)
                button.doClick()
                assertEquals(1, calls.size)
                assertFalse(archive.apply(original).archived)
                assertTrue(chosen.isEmpty(), "restore never opens or sends")
            }
            searchNow()
            runInEdtAndWait {
                search.text = "missing"
                invoke(view, "toggleArchive")
                assertEquals(1, calls.size)
                search.text = ""
            }
            searchNow()
            runInEdtAndWait {
                val ime = search.textEditor.inputMethodListeners.last()
                ime.inputMethodTextChanged(InputMethodEvent(search.textEditor, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    AttributedString("変換").iterator, 0, null, null))
                invoke(view, "toggleArchive")
                assertEquals(1, calls.size)
                ime.inputMethodTextChanged(InputMethodEvent(search.textEditor, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, null, 0, null, null))
            }
            searchNow()
            runInEdtAndWait {
                valid = false
                invoke(view, "toggleArchive")
                valid = true
                view.suspendUpdates()
                invoke(view, "toggleArchive")
                view.dispose()
                invoke(view, "toggleArchive")
                assertEquals(1, calls.size)
            }
        } finally {
            runInEdtAndWait {
                view.dispose()
                PropertiesComponent.getInstance(fixture.project).unsetValue("CursorAgent.chatArchive." + pinnedChatKey(original.id))
                fixture.tearDown()
            }
        }
    }

    @Test
    fun `quick access excludes archived candidates while history has twenty row pages reset by query`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("history routing").fixture
        fixture.setUp()
        val normal = (1..241).map { entry().copy(title = "Android Build $it", updatedMs = it.toLong()) }
        val archived = entry().copy(title = "Archived Android Build", updatedMs = 999)
        val views = mutableListOf<AllChatsView>()
        val properties = PropertiesComponent.getInstance(fixture.project)
        fun searchNow(view: AllChatsView) {
            var worker: Future<*>? = null
            runInEdtAndWait {
                (get(view, "debounce") as Timer).stop()
                invoke(view, "runSearch")
                worker = get(view, "worker") as Future<*>?
            }
            worker?.get(10, TimeUnit.SECONDS)
            SwingUtilities.invokeAndWait {}
        }
        fun rows(view: AllChatsView) = (get(view, "list") as JList<*>).let { list ->
            (0 until list.model.size).map { list.model.getElementAt(it) as AllChatRow }
        }
        var archiveCalls = 0
        try {
            runInEdtAndWait {
                assertTrue(ChatArchiveState(properties).set(archived, true))
                for (mode in listOf(ChatListMode.QUICK_ACCESS, ChatListMode.HISTORY)) {
                    views.add(AllChatsView(fixture.project, mode, { normal + archived }, { normal.first().id }, { true }, {},
                        onArchive = { archiveCalls++ }, onManage = if (mode == ChatListMode.HISTORY) ({}) else null).apply {
                        useLoadedHistory(com.cursoragent.history.ConversationStore.Loaded(emptyList(), 0))
                    })
                }
            }
            views.forEach(::searchNow)
            val quick = views[0]
            val history = views[1]
            runInEdtAndWait {
                assertEquals(200, rows(quick).size)
                assertTrue(rows(quick).all { it is AllChatRow.Chat && !it.hit.entry.archived })
                assertNull((get(quick, "archiveButton") as JButton).parent)
                assertNull((get(quick, "pinButton") as JButton).parent)
                invoke(quick, "toggleArchive")
                invoke(quick, "togglePin")
                assertEquals(0, archiveCalls)
                assertEquals(20, rows(history).filterIsInstance<AllChatRow.Chat>().size)
                val list = get(history, "list") as JList<*>
                list.selectedIndex = rows(history).indexOfFirst { it is AllChatRow.More }
                history.javaClass.getDeclaredMethod("choose", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(history, true)
                assertEquals(40, rows(history).filterIsInstance<AllChatRow.Chat>().size)
                (get(history, "search") as SearchTextField).text = "Android"
                (get(quick, "search") as SearchTextField).text = "andb"
            }
            views.forEach(::searchNow)
            runInEdtAndWait {
                assertEquals(20, rows(history).filterIsInstance<AllChatRow.Chat>().size, "query changes reset both history page limits")
                assertEquals(200, rows(quick).size, "quick access remains fuzzy")
                (get(history, "search") as SearchTextField).text = "andb"
            }
            searchNow(history)
            runInEdtAndWait { assertTrue(rows(history).isEmpty(), "header history uses literal matching and hides empty archive during a query") }
        } finally {
            runInEdtAndWait {
                views.forEach(AllChatsView::dispose)
                properties.unsetValue("CursorAgent.chatArchive." + pinnedChatKey(archived.id))
                fixture.tearDown()
            }
        }
    }

    @Test
    fun `root stops only the archived run while retaining hidden draft queue and owner until completion`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("archive owner").fixture
        fixture.setUp()
        val settings = AgentSettingsState.getInstance()
        val executable = settings.agentExecutablePath
        settings.agentExecutablePath = java.nio.file.Path.of(fixture.project.basePath!!, "missing-archive-agent-${UUID.randomUUID()}").toString()
        var root: AgentToolWindowRootPanel? = null
        val metadata = mutableListOf<Future<*>>()
        val keys = mutableListOf<String>()
        try {
            runInEdtAndWait {
                val panel = AgentToolWindowRootPanel(fixture.project) {}
                root = panel
                var buttonShowing = true
                val action = panel.actions.historyAction
                val button = object : com.intellij.openapi.actionSystem.impl.ActionButton(
                    action, action.templatePresentation.clone(), "CursorAgent.SessionHeader", java.awt.Dimension(22, 34),
                ) { override fun isShowing() = buttonShowing }
                panel.installHeaderToolbar(javax.swing.JPanel().apply { add(button) })
                val popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(javax.swing.JPanel(), null).createPopup()
                try {
                    val anchor = panel.javaClass.getDeclaredMethod("historyPopupAnchor", com.intellij.openapi.ui.popup.JBPopup::class.java)
                        .apply { isAccessible = true }
                    assertSame(button, anchor.invoke(panel, popup))
                    assertSame(button, com.intellij.openapi.ui.popup.util.PopupUtil.getPopupToggleComponent(popup))
                    buttonShowing = false
                    assertSame(get(panel, "strip"), anchor.invoke(panel, popup))
                    assertNull(com.intellij.openapi.ui.popup.util.PopupUtil.getPopupToggleComponent(popup))
                } finally { com.intellij.openapi.util.Disposer.dispose(popup) }
                val sessions = get(panel, "sessions") as SessionTabs
                val views = get(panel, "views") as Map<*, *>
                val owner = sessions.snapshot().selected
                val firstView = views[owner.id]!!
                val first = get(firstView, "controller") as AgentUiController
                val composer = get(firstView, "composer") as ComposerPanel
                composer.inputArea.text = "unsubmitted draft"
                val queue = get(first, "queue") as PromptQueue
                queue.add("queued prompt", AgentMode.PLAN, "exact-model")
                val queued = queue.snapshot()
                sessions.updateComposer(owner.id, AgentMode.AGENT, "model", "run", 0)
                val token = sessions.beginTurn(owner.id)!!.token
                var stops = 0
                val run = AgentRun(object : AgentProcessListener {
                    override fun onStopped() { sessions.finishTurn(token) }
                }).apply { attachCancellation { stops++ } }
                field(first, "activeRun").set(first, run)
                val secondOwner = sessions.open()
                fun show() = panel.javaClass.getDeclaredMethod("showSelected", com.cursoragent.history.Conversation::class.java,
                    Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(panel, null, false)
                fun entries() = invoke(panel, "openRecentEntries") as List<*>
                fun archive(entry: RecentChatEntry, sidebar: Boolean) = panel.javaClass.getDeclaredMethod("archiveChat",
                    ChatArchiveRequest::class.java).apply { isAccessible = true }.invoke(panel,
                        ChatArchiveRequest(entry, sidebar, entries().filterIsInstance<RecentChatEntry>()) { true })
                show()
                val second = get(views[secondOwner.id]!!, "controller") as AgentUiController
                var otherStops = 0
                val other = AgentRun(object : AgentProcessListener {}).apply { attachCancellation { otherStops++ } }
                field(second, "activeRun").set(second, other)
                sessions.select(owner.id)
                show()
                val properties = PropertiesComponent.getInstance(fixture.project)
                val state = ChatArchiveState(properties)
                val original = (entries().filterIsInstance<RecentChatEntry>()).first { it.id == RecentChatId.Body(owner.conversationId) }
                keys.add("CursorAgent.chatArchive." + pinnedChatKey(original.id))
                keys.add("CursorAgent.pinnedChats")
                val previousPins = properties.getList("CursorAgent.pinnedChats")
                properties.setList("CursorAgent.pinnedChats", listOf(pinnedChatKey(original.id)))
                try {
                    val previousDialog = TestDialogManager.setTestDialog(TestDialog.NO)
                    try {
                        archive(original, true)
                        assertEquals(0, stops)
                        assertFalse(state.apply(original).archived)
                        assertEquals(owner.id, sessions.snapshot().selectedId)
                        TestDialogManager.setTestDialog {
                            // Re-entrant work while the confirmation is visible invalidates the row.
                            assertTrue(state.set(original, true))
                            com.intellij.openapi.ui.Messages.YES
                        }
                        archive(original, true)
                        assertEquals(0, stops, "confirmation cannot stop a row whose metadata changed")
                        assertTrue(state.set(state.apply(original), false))
                    } finally { TestDialogManager.setTestDialog(previousDialog) }
                    val currentOriginal = state.apply(original)
                    archive(currentOriginal, false)
                    assertEquals(1, stops)
                    assertEquals(0, otherStops)
                    assertTrue(sessions.accepts(token), "Stop does not imply actual completion")
                    assertEquals(owner.id, sessions.snapshot().selectedId, "header history archive keeps the view open")
                    assertEquals(queued, queue.snapshot())
                    assertTrue(queue.paused)
                    assertEquals("unsubmitted draft", composer.inputArea.text)
                    assertEquals(listOf(pinnedChatKey(original.id)), properties.getList("CursorAgent.pinnedChats"))
                    assertTrue(state.apply(original).archived)
                    archive(original, false)
                    assertEquals(1, stops, "stale repeated action is rejected")
                    archive(state.apply(original), false)
                    assertFalse(state.apply(original).archived)
                    assertTrue(queue.paused)
                    assertEquals(owner.id, sessions.snapshot().selectedId)
                    val acceptDialog = TestDialogManager.setTestDialog(TestDialog.YES)
                    try { archive(state.apply(original), true) } finally { TestDialogManager.setTestDialog(acceptDialog) }
                    assertEquals(secondOwner.id, sessions.snapshot().selectedId)
                    assertFalse(sessions.snapshot().tabs.first { it.id == owner.id }.visible)
                    assertSame(firstView, views[owner.id])
                    assertEquals(queued, queue.snapshot())
                    assertEquals("unsubmitted draft", composer.inputArea.text)
                    assertTrue(properties.getList("CursorAgent.pinnedChats").orEmpty().isEmpty())
                    assertTrue(sessions.accepts(token))
                    run.complete(0)
                    assertFalse(sessions.accepts(token))
                    assertEquals(secondOwner.id, sessions.snapshot().selectedId)
                    archive(state.apply(original), true)
                    assertEquals(secondOwner.id, sessions.snapshot().selectedId, "restore does not open a tab")
                    assertTrue(sessions.select(owner.id))
                    show()
                    assertSame(firstView, views[owner.id])
                    assertEquals("unsubmitted draft", composer.inputArea.text)
                    assertEquals(queued, queue.snapshot())
                    assertTrue(queue.paused)
                    assertEquals(0, otherStops)
                    views.values.filterNotNull().forEach {
                        val loader = get(get(it, "controller")!!, "modelLoader")!!
                        (get(loader, "pending") as Future<*>?)?.let(metadata::add)
                    }
                } finally {
                    if (previousPins == null) properties.unsetValue("CursorAgent.pinnedChats")
                    else properties.setList("CursorAgent.pinnedChats", previousPins)
                    keys.remove("CursorAgent.pinnedChats")
                }
            }
        } finally {
            metadata.forEach { it.get(10, TimeUnit.SECONDS) }
            runInEdtAndWait {
                root?.dispose()
                val properties = PropertiesComponent.getInstance(fixture.project)
                keys.forEach(properties::unsetValue)
            }
            settings.agentExecutablePath = executable
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
