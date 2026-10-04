package com.cursoragent.ui

import com.cursoragent.history.ConversationStore
import com.cursoragent.settings.ChatHistoryState
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import javax.swing.SwingUtilities

class PastChatsCoordinatorTest {
    @Test
    fun `hidden requests follow the original chat and keyboard repeats do not toggle or duplicate loading`() {
        withCoordinator { f ->
            f.edt {
                f.state = f.state.copy(visible = false)
                f.coordinator.request()
                f.coordinator.request()
                assertTrue(f.loads.isEmpty())
                assertEquals(0, f.showing)
                f.state = f.state.copy(ownerId = "other", visible = true)
                f.coordinator.refresh()
                assertTrue(f.loads.isEmpty(), "another selected chat cannot consume a hidden request")
                f.state = f.state.copy(ownerId = "original")
                f.coordinator.refresh()
                f.coordinator.request()
                assertEquals(1, f.loads.size)
            }
            f.complete()
            f.edt {
                assertEquals(1, f.shown)
                assertEquals(1, f.showing)
                f.coordinator.refresh()
                assertTrue(f.loads.isEmpty(), "closing the history consumes its display request")
            }
        }
    }

    @Test
    fun `header toggle sidebar and owner close invalidate in flight history without reopening it`() {
        withCoordinator { f ->
            for (cancel in listOf<(Fixture) -> Unit>(
                { it.coordinator.request(toggle = true) },
                { it.state = it.state.copy(sidebar = true); it.coordinator.refresh() },
                { it.coordinator.forget("original") },
            )) {
                f.edt {
                    f.state = HistoryPopupContext("original", true, false, true)
                    f.coordinator.request()
                    assertEquals(1, f.loads.size)
                    cancel(f)
                }
                f.complete()
                f.edt {
                    f.state = f.state.copy(sidebar = false)
                    f.coordinator.refresh()
                    assertTrue(f.loads.isEmpty())
                    assertEquals(0, f.shown)
                    assertEquals(0, f.showing, "cancelling before display must not pause the queue")
                }
            }
        }
    }

    @Test
    fun `hidden and switched owners invalidate old callbacks and only the new generation may display`() {
        withCoordinator { f ->
            f.edt {
                f.coordinator.request()
                f.state = f.state.copy(visible = false)
                f.coordinator.refresh()
                f.state = f.state.copy(visible = true)
                f.coordinator.refresh()
                assertEquals(2, f.loads.size)
            }
            f.complete()
            f.edt { assertEquals(0, f.shown) }
            f.complete()
            f.edt { assertEquals(1, f.shown) }
            f.edt {
                f.coordinator.request()
                f.state = f.state.copy(ownerId = "other")
                f.coordinator.refresh()
                f.coordinator.request()
            }
            f.complete()
            f.edt { assertEquals(1, f.shown) }
            f.complete()
            f.edt { assertEquals(2, f.shown) }
        }
    }

    @Test
    fun `missing owners sidebar and focus guards reject requests and late guards discard delayed dialogs`() {
        withCoordinator { f ->
            f.edt {
                for (state in listOf(
                    HistoryPopupContext(null, true, false, true),
                    HistoryPopupContext("original", true, true, true),
                    HistoryPopupContext("original", true, false, false),
                )) {
                    f.state = state
                    f.coordinator.request()
                    assertTrue(f.loads.isEmpty())
                }
                f.state = HistoryPopupContext("original", true, false, true)
                f.coordinator.request()
                f.state = f.state.copy(allowed = false)
            }
            f.complete()
            f.edt {
                f.state = f.state.copy(allowed = true)
                f.coordinator.refresh()
                assertTrue(f.loads.isEmpty())
                assertEquals(0, f.shown)
                f.coordinator.request()
                f.coordinator.dispose()
            }
            f.complete()
            f.edt {
                f.coordinator.request()
                assertTrue(f.loads.isEmpty())
                assertEquals(0, f.shown)
            }
        }
    }

    @Test
    fun `an open dialog survives repeated show requests and cannot apply its selection to a later owner`() {
        withCoordinator { f ->
            val conversation = com.cursoragent.history.Conversation()
            f.edt {
                f.duringShow = { dialog ->
                    f.coordinator.request()
                    f.coordinator.refresh()
                    assertTrue(f.loads.isEmpty(), "show is idempotent while its dialog is open")
                    dialog.javaClass.getDeclaredMethod("doOKAction").apply { isAccessible = true }.invoke(dialog)
                }
                f.coordinator.request()
            }
            f.complete(listOf(conversation))
            f.edt {
                assertEquals(listOf(conversation.id), f.opened)
                f.duringShow = { dialog ->
                    f.state = f.state.copy(ownerId = "other")
                    f.coordinator.refresh()
                    // Simulate a delayed callback from the old dialog after ownership changed.
                    @Suppress("UNCHECKED_CAST")
                    val open = dialog.javaClass.getDeclaredField("onOpen").apply { isAccessible = true }.get(dialog) as (HistoryHit, String) -> Unit
                    open(HistoryHit(HistoryEntry(conversation, null, "", 0), null), "")
                }
                f.coordinator.request()
            }
            f.complete(listOf(conversation))
            f.edt { assertEquals(listOf(conversation.id), f.opened) }
        }
    }

    @Test
    fun `nonmodal history persists through repeated requests and closes before a later owner can act`() {
        val ide = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("history menu lifecycle").fixture
        ide.setUp()
        var state = HistoryPopupContext("original", true, false, true)
        val loads = ArrayDeque<(Result<ConversationStore.Loaded>) -> Unit>()
        val guards = mutableListOf<() -> Boolean>()
        val closes = mutableListOf<() -> Unit>()
        val handles = mutableListOf<com.intellij.openapi.Disposable>()
        var showing = 0
        var managerShows = 0
        var disposeCount = 0
        var closeImmediately = false
        lateinit var coordinator: PastChatsCoordinator
        fun complete() {
            runInEdtAndWait { loads.removeFirst()(Result.success(ConversationStore.Loaded(emptyList(), 0))) }
            SwingUtilities.invokeAndWait {}
        }
        try {
            runInEdtAndWait {
                coordinator = PastChatsCoordinator(ide.project, ChatHistoryState(),
                    onChatResumed = { _, _, _, _ -> fail("metadata menu must not use body-search opening") },
                    isOpen = { false }, context = { state }, onShowing = { showing++ }, load = { loads.add(it) },
                    showDialog = { managerShows++; it.close(DialogWrapper.CANCEL_EXIT_CODE) },
                    showMenu = { _, current, closed ->
                        guards.add(current)
                        closes.add(closed)
                        com.intellij.openapi.Disposable { disposeCount++; closed() }.also {
                            handles.add(it)
                            if (closeImmediately) closed()
                        }
                    },
                )
                coordinator.request()
            }
            complete()
            runInEdtAndWait {
                assertTrue(guards[0]())
                coordinator.request()
                coordinator.refresh()
                assertTrue(loads.isEmpty())
                assertEquals(1, showing)
                state = state.copy(ownerId = "other")
                coordinator.refresh()
                assertFalse(guards[0]())
                assertEquals(1, disposeCount)
                coordinator.request()
            }
            complete()
            runInEdtAndWait {
                closes[0]()
                assertTrue(guards[1](), "late close cannot consume a new owner's request")
                coordinator.request(toggle = true)
                assertFalse(guards[1]())
                assertEquals(2, disposeCount)
                coordinator.refresh()
                assertTrue(loads.isEmpty())
                coordinator.request(manage = true)
            }
            complete()
            runInEdtAndWait {
                assertEquals(1, managerShows, "saved body search remains explicitly reachable")
                assertEquals(2, handles.size)
                closeImmediately = true
                coordinator.request()
            }
            complete()
            runInEdtAndWait {
                assertFalse(guards.last()())
                assertEquals(3, disposeCount, "synchronous close must not leave a retained popup handle")
                coordinator.refresh()
                assertTrue(loads.isEmpty())
                closeImmediately = false
                coordinator.request()
            }
            complete()
            runInEdtAndWait {
                coordinator.dispose()
                assertFalse(guards.last()())
                assertEquals(4, disposeCount)
            }
        } finally {
            runInEdtAndWait { coordinator.dispose(); ide.tearDown() }
        }
    }

    private fun withCoordinator(check: (Fixture) -> Unit) {
        val ide = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("history requests").fixture
        ide.setUp()
        var f: Fixture? = null
        try {
            runInEdtAndWait { f = Fixture(ide.project) }
            check(requireNotNull(f))
        } finally {
            runInEdtAndWait { f?.coordinator?.dispose() }
            com.intellij.testFramework.runInEdtAndWait { ide.tearDown() }
        }
    }

    private class Fixture(project: com.intellij.openapi.project.Project) {
        var state = HistoryPopupContext("original", true, false, true)
        val loads = ArrayDeque<(Result<ConversationStore.Loaded>) -> Unit>()
        var shown = 0
        var showing = 0
        val opened = mutableListOf<String?>()
        var duringShow: (HistorySearchDialog) -> Unit = {}
        val coordinator = PastChatsCoordinator(project, ChatHistoryState(),
            onChatResumed = { conversation, legacy, _, _ -> opened.add(conversation?.id ?: legacy) },
            isOpen = { false }, context = { state }, onShowing = { showing++ },
            load = { loads.add(it) },
            showDialog = { dialog ->
                shown++
                duringShow(dialog)
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
            },
        )
        fun edt(block: () -> Unit) = runInEdtAndWait(block)
        fun complete(conversations: List<com.cursoragent.history.Conversation> = emptyList()) {
            edt { loads.removeFirst()(Result.success(ConversationStore.Loaded(conversations, 0))) }
            // The coordinator posts to Swing's queue. The IDE's separate invocation queue
            // may overtake that event, so an empty runInEdtAndWait is not a completion barrier.
            SwingUtilities.invokeAndWait {}
        }
    }
}
