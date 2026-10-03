package com.cursoragent.ui

import com.cursoragent.parser.FileEditDetails
import com.cursoragent.service.RestoreTarget
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.WorktreeMode
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import java.awt.Toolkit
import java.nio.file.Path
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ChangesReviewControllerTest {
    @Test fun `production review entry and callbacks pause tickets through a modal event loop and cancellation`(@TempDir root: Path) {
        for (action in listOf("cancel", "conversation", "toggle", "revert", "dispose")) SwingUtilities.invokeAndWait {
            val queue = PromptQueue("owner")
            val other = PromptQueue("other")
            queue.add("keep text", AgentMode.ASK, "model", PromptContextSnapshot(emptyList(), emptyList(), false))
            other.add("independent", AgentMode.AGENT, "")
            val original = queue.snapshot()
            val old = queue.ticket(1)!!
            var dispatched = 0
            var otherDispatched = 0
            var cancellations = 0
            var returns = 0
            var restores = 0
            var pauses = 0
            var alive = true
            val changes = ConversationChanges("owner")
            val target = RestoreTarget.capture(root.toString(), WorktreeMode.DEFAULT)
            changes.print("turn", "call", FileEditDetails("a", 0, 0, null, "before", "after"), target)
            lateinit var review: ChangesReviewController
            var fresh: QueueDispatch? = null
            review = ChangesReviewController(changes,
                pauseQueue = { queue.pause(); pauses++ },
                isAlive = { alive },
                captureCurrent = { { alive } },
                createView = { snapshot, _, revert, conversation ->
                    assertTrue(queue.paused, "must pause before constructing the dialog")
                    object : ChangesReviewView {
                        override fun cancel() { cancellations++ }
                        override fun show() = modalLoop {
                            when (action) {
                                "cancel" -> cancel()
                                "conversation" -> { cancel(); conversation() }
                                "toggle" -> review.show()
                                "dispose" -> { alive = false; review.dispose() }
                                "revert" -> {
                                    // Even a newly scheduled ticket must be invalidated at the Revert entry itself.
                                    queue.resume()
                                    fresh = queue.ticket(1)!!
                                    revert(snapshot.files().single())
                                    cancel()
                                }
                            }
                        }
                    }
                },
                onDiff = { error("No diff requested") },
                onRevert = { _, isCurrent ->
                    restores++
                    assertTrue(isCurrent())
                    assertTrue(queue.paused)
                    assertFalse(queue.dispatch(fresh!!, 1, true) { dispatched++; true })
                },
                onConversation = { returns++ },
            )
            SwingUtilities.invokeLater {
                queue.dispatch(old, 1, true) { dispatched++; true }
                other.dispatch(other.ticket(1)!!, 1, true) { otherDispatched++; true }
            }
            review.show()
            assertEquals(0, dispatched, action)
            assertEquals(1, otherDispatched, action)
            assertEquals(1, cancellations, action)
            assertEquals(if (action == "conversation") 1 else 0, returns, action)
            assertEquals(if (action == "revert") 1 else 0, restores, action)
            assertEquals(if (action in listOf("toggle", "revert")) 2 else 1, pauses, action)
            assertEquals(original, queue.snapshot(), action)
            assertTrue(queue.paused, action)
            queue.resume()
            assertFalse(queue.dispatch(old, 1, true) { error("old ticket revived") })
            assertTrue(queue.dispatch(queue.ticket(1)!!, 1, true) { assertEquals(original.single(), it); true })
        }
    }

    @Test fun `same review snapshot and owner guard reach Revert unchanged and invalid groups cannot restore`(@TempDir root: Path) = SwingUtilities.invokeAndWait {
        for (mutation in listOf("generation", "snapshot", "invalid")) {
            val changes = ConversationChanges("owner")
            val target = RestoreTarget.capture(root.toString(), WorktreeMode.DEFAULT)
            changes.print("turn", "call", FileEditDetails("a", 0, 0, null, "before", "after"),
                if (mutation == "invalid") RestoreTarget.UNKNOWN else target)
            var generation = 1
            var calls = 0
            var pauses = 0
            val review = ChangesReviewController(changes, { pauses++ }, { true }, {
                val captured = generation
                { captured == generation }
            }, { snapshot, _, revert, _ ->
                object : ChangesReviewView {
                    override fun cancel() = Unit
                    override fun show() {
                        if (mutation == "generation") generation++
                        if (mutation == "snapshot") changes.beginTurn("new")
                        revert(snapshot.files().single())
                    }
                }
            }, {}, { _, current -> calls++; assertFalse(current()) }, {})
            review.show()
            assertEquals(2, pauses)
            assertEquals(if (mutation == "invalid") 0 else 1, calls)
        }
    }

    /** Pumps queued EDT work just like a modal dialog, without displaying an IDE/OS window. */
    private fun modalLoop(action: () -> Unit) {
        val loop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
        var failure: Throwable? = null
        SwingUtilities.invokeLater {
            try { action() } catch (error: Throwable) { failure = error } finally { loop.exit() }
        }
        assertTrue(loop.enter())
        failure?.let { throw it }
    }
}
