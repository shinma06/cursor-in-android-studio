package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.parser.StreamJsonParser
import com.cursoragent.parser.StreamEvent
import com.cursoragent.service.*
import com.cursoragent.session.SessionTabs
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.cursoragent.ui.timeline.RunPhase
import com.cursoragent.ui.timeline.RunStatusPanel
import com.intellij.openapi.project.Project
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RunProgressDispatchTest {
    @Test fun `elapsed includes preparation freezes once and releases timer on disposal`() = SwingUtilities.invokeAndWait {
        var now = 0L
        val panel = RunStatusPanel { now }
        panel.begin()
        assertTrue(panel.isTicking)
        now = 65_000_000_000
        panel.update(RunPhase.THINKING)
        assertEquals("考え中 · 経過 1:05", panel.statusText)
        panel.update(RunPhase.STOPPING)
        panel.update(RunPhase.RUNNING)
        assertEquals(RunPhase.STOPPING, panel.phase)
        now += 2_000_000_000
        panel.update(RunPhase.STOPPED)
        val stopped = panel.statusText
        now += 90_000_000_000
        panel.update(RunPhase.FAILED)
        assertEquals(stopped, panel.statusText)
        assertFalse(panel.isTicking)
        panel.begin()
        assertEquals("送信を準備中 · 経過 0:00", panel.statusText)
        panel.dispose()
        panel.begin()
        assertFalse(panel.isTicking)
    }

    @Test fun `real listeners reject queued stale work and keep parallel terminal notices and request ownership`() {
        val sessions = SessionTabs()
        val firstTab = sessions.snapshot().selected
        val otherTab = sessions.open()
        sessions.updateComposer(firstTab.id, com.cursoragent.settings.AgentMode.AGENT, "", "first", 0)
        sessions.updateComposer(otherTab.id, com.cursoragent.settings.AgentMode.AGENT, "", "second", 0)
        val first = sessions.beginTurn(firstTab.id)!!.token
        val other = sessions.beginTurn(otherTab.id)!!.token
        val notices = mutableListOf<String>()
        val finished = mutableListOf<Boolean>()
        var disposed = false
        val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            if (method.name == "isDisposed") disposed else error("Unexpected Project access: ${method.name}")
        } as Project
        lateinit var firstTimeline: ChatTimelinePanel
        lateinit var otherTimeline: ChatTimelinePanel
        lateinit var runA: AgentRun
        lateinit var runB: AgentRun
        lateinit var listenerA: AgentProcessListener
        lateinit var listenerB: AgentProcessListener
        SwingUtilities.invokeAndWait {
            firstTimeline = ChatTimelinePanel().apply { isActiveTab = false; runStatus.begin() }
            otherTimeline = ChatTimelinePanel().apply { isActiveTab = true; runStatus.begin() }
            fun factory(timeline: ChatTimelinePanel, id: String) = AgentTurnListenerFactory(
                project, timeline, { _, _ -> }, {}, ConversationRecorder(Conversation()) {},
                { success -> finished.add(success); sessions.finishTurn(if (id == first.turnId) first else other) },
                ConversationChanges(id), {},
                onUsageFinish = { _, _ -> }, onToolNotice = { notices.add("start:$it") },
                onTerminalNotice = { turn, phase -> notices.add("$phase:$turn") },
            )
            listenerA = factory(firstTimeline, first.turnId).create(1, first.turnId,
                { sessions.accepts(first) }, { runA.wasStopped }, { true }, { RestoreTarget.UNKNOWN })
            listenerB = factory(otherTimeline, other.turnId).create(2, other.turnId,
                { sessions.accepts(other) }, { runB.wasStopped }, { true }, { RestoreTarget.UNKNOWN },
                onPrintRequestId = { assertTrue(sessions.accepts(other)); notices.add("request:${it.value}") })
            runA = AgentRun(listenerA)
            runB = AgentRun(listenerB)
            runA.emit { it.onStarted(); it.onThinking("observed"); it.onToolCallStarted(ParsedToolCall("a", "started", "read", "first")); it.onToolCallStarted(ParsedToolCall("b", "started", "read", "second")) }
            runB.emit { it.onStarted(); it.onToolCallStarted(ParsedToolCall("c", "started", "read", "foreground")) }
            assertEquals(listOf("start:${first.turnId}"), notices)
            assertEquals(RunPhase.TOOL, firstTimeline.runStatus.phase)
            // The event is queued before Stop, but executes after it on the EDT.
            val sender = Thread { runA.emit { it.onThinking("late") } }
            sender.start(); sender.join()
            runA.stop()
            runA.complete(7, "shutdown stderr")
            assertEquals(RunPhase.STOPPED, firstTimeline.runStatus.phase)
            assertFalse(firstTimeline.runStatus.isTicking)
            assertTrue(otherTimeline.runStatus.isTicking)
            assertTrue(sessions.accepts(other))
            runB.complete(0, printRequestId = PrintRequestId("synthetic", null))
            listenerB.onCompleted(1)
            listenerB.onToolCall("after completion")
            assertEquals(RunPhase.COMPLETED, otherTimeline.runStatus.phase)
            assertFalse(otherTimeline.runStatus.isTicking)
        }
        SwingUtilities.invokeAndWait {
            assertEquals(RunPhase.STOPPED, firstTimeline.runStatus.phase)
            assertEquals(listOf(false, true), finished)
            assertEquals(listOf("start:${first.turnId}", "STOPPED:${first.turnId}", "COMPLETED:${other.turnId}", "request:synthetic"), notices)
            disposed = true
            firstTimeline.runStatus.dispose()
            otherTimeline.runStatus.dispose()
        }
    }

    @Test fun `error and uncertain completion stay distinct from stop and never duplicate terminal notice`() = SwingUtilities.invokeAndWait {
        for (uncertain in listOf(false, true)) {
            val timeline = ChatTimelinePanel().apply { isActiveTab = false; runStatus.begin() }
            var current = true
            val notices = mutableListOf<RunPhase>()
            val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
                if (method.name == "isDisposed") false else error("Unexpected Project access")
            } as Project
            val listener = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, ConversationRecorder(Conversation()) {},
                { current = false }, ConversationChanges("one"), {}, onUsageFinish = { _, _ -> }, onToolNotice = {},
                onTerminalNotice = { _, phase -> notices.add(phase) },
            ).create(1, "one", { current }, { false }, { true }, { RestoreTarget.UNKNOWN })
            if (uncertain) listener.onUncertain("終了未確認") else listener.onError("synthetic failure")
            listener.onStopped()
            listener.onCompleted(0)
            assertEquals(listOf(RunPhase.FAILED), notices)
            assertEquals(RunPhase.FAILED, timeline.runStatus.phase)
            assertFalse(timeline.runStatus.isTicking)
            timeline.runStatus.dispose()
        }
    }

    @Test fun `legacy and unknown tool states retain literal names without inventing started notifications`() = SwingUtilities.invokeAndWait {
        val timeline = ChatTimelinePanel().apply { isActiveTab = false; runStatus.begin() }
        val notices = mutableListOf<String>()
        val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            if (method.name == "isDisposed") false else error("Unexpected Project access")
        } as Project
        val listener = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, ConversationRecorder(Conversation()) {},
            {}, ConversationChanges("one"), {}, onUsageFinish = { _, _ -> }, onToolNotice = notices::add, onTerminalNotice = { _, _ -> },
        ).create(1, "one", { true }, { false }, { true }, { RestoreTarget.UNKNOWN })
        val parser = StreamJsonParser { event ->
            if (event is StreamEvent.ToolCall) listener.onToolCall(event.toolName)
        }
        parser.parseLine("""{"type":"tool_call","name":"syntheticLegacyRead"}""")
        parser.parseLine("""{"type":"tool_call","subtype":"unknown","name":"<html>literal"}""")
        fun descendants(value: java.awt.Component): List<java.awt.Component> = listOf(value) +
            if (value is java.awt.Container) value.components.flatMap(::descendants) else emptyList()
        val labels = descendants(timeline).filterIsInstance<javax.swing.JLabel>()
        assertTrue(labels.any { it.text == "ツール情報（状態未取得）: syntheticLegacyRead" })
        val literal = labels.single { it.text == "ツール情報（状態未取得）: <html>literal" }
        assertEquals(true, literal.getClientProperty("html.disable"))
        assertEquals(RunPhase.RUNNING, timeline.runStatus.phase)
        assertTrue(notices.isEmpty())
        timeline.runStatus.dispose()
    }

}
