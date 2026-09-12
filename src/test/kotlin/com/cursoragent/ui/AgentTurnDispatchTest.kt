package com.cursoragent.ui

import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class AgentTurnDispatchTest {
    @Test
    fun `queued output follows its owner across selection and rejects closed or replaced tokens`() {
        for (change in listOf("select", "close", "replace")) {
            val tabs = SessionTabs()
            val owner = tabs.snapshot().selectedId
            tabs.updateComposer(owner, AgentMode.AGENT, "", "prompt", 0)
            val token = tabs.beginTurn(owner)!!.token
            var deliveries = 0
            queued {
                updateCurrentTurnOnEdt({ false }, { tabs.accepts(token) }, { false }) { deliveries++ }
                when (change) {
                    "select" -> tabs.open()
                    "close" -> tabs.close(owner)
                    "replace" -> {
                        tabs.finishTurn(token)
                        tabs.updateComposer(owner, AgentMode.AGENT, "", "next", 0)
                        tabs.beginTurn(owner)
                    }
                }
            }
            assertEquals(if (change == "select") 1 else 0, deliveries, change)
        }
    }

    @Test
    fun `queued callbacks recheck disposal generation and stop while terminal callbacks may finish stop`() {
        for (change in listOf("dispose", "generation", "stop")) {
            var disposed = false
            var current = true
            var stopped = false
            val deliveries = mutableListOf<String>()
            queued {
                updateCurrentTurnOnEdt({ disposed }, { current }, { stopped }) { deliveries.add("text") }
                updateCurrentTurnOnEdt({ disposed }, { current }, { stopped }, allowStopped = true) {
                    deliveries.add("terminal")
                }
                when (change) {
                    "dispose" -> disposed = true
                    "generation" -> current = false
                    "stop" -> stopped = true
                }
            }
            assertEquals(if (change == "stop") listOf("terminal") else emptyList<String>(), deliveries, change)
        }
    }

    @Test
    fun `callback already on EDT runs immediately on EDT`() {
        SwingUtilities.invokeAndWait {
            var delivered = false
            updateCurrentTurnOnEdt({ false }, { true }, { false }) {
                assertTrue(SwingUtilities.isEventDispatchThread())
                delivered = true
            }
            assertTrue(delivered)
        }
    }

    private fun queued(enqueueAndInvalidate: () -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        SwingUtilities.invokeLater {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            enqueueAndInvalidate()
        } finally {
            release.countDown()
            SwingUtilities.invokeAndWait {}
        }
    }
}
