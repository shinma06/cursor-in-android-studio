package com.cursoragent.ui

import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.parser.belongsToPrintSession
import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import com.cursoragent.service.AgentTask
import com.cursoragent.service.AgentTool
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.cursoragent.ui.timeline.TaskToolCard
import java.awt.Component
import java.awt.Container
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaskDispatchTest {
    private fun cards(component: Component): List<TaskToolCard> =
        (if (component is TaskToolCard) listOf(component) else emptyList()) +
            if (component is Container) component.components.flatMap(::cards) else emptyList()

    @Test fun `same task IDs in different runs route to their owning timeline and ignore terminal delivery`() {
        val tabs = SessionTabs()
        val a = tabs.snapshot().selected
        val b = tabs.open()
        lateinit var timelines: List<ChatTimelinePanel>
        SwingUtilities.invokeAndWait { timelines = listOf(ChatTimelinePanel(), ChatTimelinePanel()) }
        val runs = listOf(a, b).mapIndexed { index, tab ->
            tabs.updateComposer(tab.id, AgentMode.AGENT, "", "synthetic", 0)
            val token = tabs.beginTurn(tab.id)!!.token
            val parent = "parent-$index"
            lateinit var run: AgentRun
            run = AgentRun(object : AgentProcessListener {
                override fun onToolCallCompleted(payload: ParsedToolCall) {
                    if (!payload.belongsToPrintSession(parent)) return
                    updateCurrentTurnOnEdt({ false }, { tabs.accepts(token) }, { run.wasStopped }) {
                        timelines[index].upsertTask(payload.task!!, payload.parentSessionId)
                    }
                }
            })
            run
        }
        val child = AgentTool("same-call", status = "failed", task = AgentTask(errorText = "child failure"))
        runs.forEachIndexed { index, run ->
            run.emit { it.onToolCallCompleted(ParsedToolCall("same-call", "completed", "task", "Task", parentSessionId = "wrong", task = child)) }
            run.emit { it.onToolCallCompleted(ParsedToolCall("same-call", "completed", "task", "Task", parentSessionId = "parent-$index", task = child)) }
        }
        SwingUtilities.invokeAndWait {
            assertEquals(listOf(1, 1), timelines.map { cards(it).size })
            assertNotSame(cards(timelines[0]).single(), cards(timelines[1]).single())
        }
        runs[0].complete(0)
        runs[0].emit { it.onToolCallCompleted(ParsedToolCall("late", "completed", "task", "late", parentSessionId = "parent-0", task = child.copy(id = "late"))) }
        SwingUtilities.invokeAndWait { assertEquals(1, cards(timelines[0]).size) }
    }

    @Test fun `queued Task delivery rechecks Stop closed tab and disposed view on EDT`() {
        for (reason in listOf("stop", "close", "dispose")) {
            val tabs = SessionTabs()
            val tab = tabs.snapshot().selected
            tabs.updateComposer(tab.id, AgentMode.AGENT, "", "synthetic", 0)
            val token = tabs.beginTurn(tab.id)!!.token
            lateinit var timeline: ChatTimelinePanel
            SwingUtilities.invokeAndWait { timeline = ChatTimelinePanel() }
            var disposed = false
            lateinit var run: AgentRun
            run = AgentRun(object : AgentProcessListener {
                override fun onStructuredEvent(event: AgentEvent) {
                    updateCurrentTurnOnEdt({ disposed }, { tabs.accepts(token) }, { run.wasStopped }) {
                        timeline.upsertStructuredTool((event as AgentEvent.Tool).state) {}
                    }
                }
            })
            SwingUtilities.invokeAndWait {
                thread { run.emit { it.onStructuredEvent(AgentEvent.Tool(AgentTool("late", task = AgentTask()))) } }.join()
                when (reason) {
                    "stop" -> run.stop()
                    "close" -> { tabs.close(tab.id); run.detachListener() }
                    else -> disposed = true
                }
            }
            SwingUtilities.invokeAndWait { assertTrue(cards(timeline).isEmpty(), reason) }
        }
    }
}
