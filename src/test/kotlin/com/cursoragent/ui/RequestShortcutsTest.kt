package com.cursoragent.ui

import com.cursoragent.actions.acceptsPendingShortcut
import com.cursoragent.service.*
import com.cursoragent.settings.SendKeyMode
import com.cursoragent.ui.timeline.AgentRequestCard
import com.cursoragent.ui.timeline.ChatTimelinePanel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

class RequestShortcutsTest {
    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap(::descendants) else emptyList()

    @Test
    fun `acceptance defaults follow send mode on each OS while custom shortcuts stay usable`() {
        for (mac in listOf(true, false)) {
            val primary = if (mac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
            fun accepts(mode: SendKeyMode, modifiers: Int, code: Int = KeyEvent.VK_ENTER) =
                acceptsPendingShortcut(key(code, modifiers), mode, mac)
            assertTrue(accepts(SendKeyMode.ENTER, primary))
            assertFalse(accepts(SendKeyMode.MODIFIER_ENTER, primary))
            assertFalse(accepts(SendKeyMode.ENTER, primary or InputEvent.ALT_DOWN_MASK))
            assertTrue(accepts(SendKeyMode.MODIFIER_ENTER, primary or InputEvent.ALT_DOWN_MASK))
            for (mode in SendKeyMode.entries) {
                assertTrue(accepts(mode, primary or InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F9))
                assertTrue(accepts(mode, primary or InputEvent.SHIFT_DOWN_MASK))
            }
        }
    }

    @Test
    fun `permission shortcuts only choose unique once options and reuse the exactly once reply`() = SwingUtilities.invokeAndWait {
        val replies = mutableListOf<AgentAnswer>()
        fun permission(options: List<PermissionOption>, target: Boolean = true): AgentRequestCard = AgentRequestCard(
            AgentInputRequest(AgentInput.Permission(AgentTool("tool", command = if (target) "synthetic" else null), options)) {
                replies.add(it); it
            })
        val once = listOf(PermissionOption("opaque-yes", "Allow", "allow_once"), PermissionOption("opaque-no", "No", "reject_once"))
        val allow = permission(once)
        assertTrue(allow.isToolPermission)
        assertTrue(allow.canRespond(true))
        allow.respond(true)
        allow.respond(false)
        allow.components.filterIsInstance<JButton>().forEach { it.doClick(0) }
        assertEquals(listOf(AgentAnswer.Permission("opaque-yes")), replies)

        val missingTarget = permission(once, target = false)
        assertFalse(missingTarget.canRespond(true))
        missingTarget.respond(true)
        assertTrue(missingTarget.canRespond(false))
        missingTarget.respond(false)
        assertEquals(AgentAnswer.Permission("opaque-no"), replies.last())

        val permanent = permission(listOf(PermissionOption("always", "Allow", "allow_always"), PermissionOption("never", "No", "reject_always")))
        assertFalse(permanent.canRespond(true))
        assertFalse(permanent.canRespond(false))
        assertTrue(permanent.components.filterIsInstance<JButton>().any { it.text.startsWith("今後も許可") && it.isEnabled })
        val ambiguous = permission(once + PermissionOption("another", "Other allow", "allow_once"))
        assertFalse(ambiguous.canRespond(true))
        ambiguous.respond(true)
        assertEquals(2, replies.size)
        ambiguous.respond(false)
        assertEquals(3, replies.size)
    }

    @Test
    fun `questions require complete explicit choices and stale owners cannot answer even by mouse`() = SwingUtilities.invokeAndWait {
        val replies = mutableListOf<AgentAnswer>()
        val questions = AgentInputRequest(AgentInput.Questions(null, listOf(
            Question("one", "一つ", listOf(QuestionOption("a", "A"), QuestionOption("b", "B")), false),
            Question("many", "複数", listOf(QuestionOption("x", "X"), QuestionOption("y", "Y")), true),
        ))) { replies.add(it); it }
        val card = AgentRequestCard(questions)
        assertFalse(card.canRespond(true))
        card.respond(true)
        card.components.filterIsInstance<JRadioButton>().last().doClick(0)
        assertFalse(card.canRespond(true))
        card.components.filterIsInstance<JCheckBox>().forEach { it.doClick(0) }
        assertTrue(card.canRespond(true))
        card.respond(true)
        assertEquals(listOf(AgentAnswer.Questions(mapOf("one" to listOf("b"), "many" to listOf("x", "y")))), replies)

        var current = true
        val request = AgentInputRequest(AgentInput.Plan("Plan", null, "合成内容")) { replies.add(it); it }
        val plan = AgentRequestCard(request) { current }
        assertFalse(plan.isToolPermission)
        assertTrue(plan.canRespond(true))
        current = false
        plan.respond(true)
        plan.respond(false)
        plan.components.filterIsInstance<JButton>().forEach { it.doClick(0) }
        assertTrue(request.isPending)
        assertEquals(1, replies.size)
        request.answer(AgentAnswer.Cancel)
        assertEquals(AgentAnswer.Cancel, replies.last())
    }

    @Test
    fun `timeline targets focus or one live request but never guesses a group or an inactive owner`() = SwingUtilities.invokeAndWait {
        val replies = mutableListOf<AgentAnswer>()
        fun plan() = AgentInputRequest(AgentInput.Plan(null, null, "合成計画")) { replies.add(it); it }
        val timeline = ChatTimelinePanel()
        var current = true
        timeline.addInputRequest(plan()) { current }
        val first = descendants(timeline).filterIsInstance<AgentRequestCard>().single()
        assertSame(first, timeline.pendingInput(null))
        timeline.addInputRequest(plan()) { current }
        val second = descendants(timeline).filterIsInstance<AgentRequestCard>().last()
        assertTrue(timeline.hasPendingInput)
        assertNull(timeline.pendingInput(null))
        assertNull(timeline.pendingInput(JButton()))
        assertSame(first, timeline.pendingInput(first.components.first()))
        assertSame(second, timeline.pendingInput(second.components.last()))
        first.respond(false)
        assertSame(second, timeline.pendingInput(null))
        assertNull(timeline.pendingInput(first.components.first()), "a resolved card must not redirect its shortcut to a different request")
        timeline.isActiveTab = false
        assertNull(timeline.pendingInput(second))
        assertFalse(timeline.hasPendingInput)
        timeline.isActiveTab = true
        current = false
        assertNull(timeline.pendingInput(second))
        assertFalse(timeline.hasPendingInput)
        second.respond(true)
        assertEquals(listOf(AgentAnswer.Reject), replies)
    }

    @Test
    fun `held keys cannot reply again or fall through to Stop until their own release`() = SwingUtilities.invokeAndWait {
        val keys = RequestShortcutKeys()
        try {
            assertTrue(keys.accept(key(KeyEvent.VK_ENTER)))
            assertTrue(keys.dispatchKeyEvent(key(KeyEvent.VK_ENTER)), "a held approval must not reach native newline or another send action")
            assertFalse(keys.accept(key(KeyEvent.VK_ENTER)))
            assertTrue(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
            assertFalse(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
            keys.dispatchKeyEvent(key(KeyEvent.VK_CONTROL, release = true))
            assertFalse(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
            keys.dispatchKeyEvent(key(KeyEvent.VK_ENTER, release = true))
            assertFalse(keys.dispatchKeyEvent(key(KeyEvent.VK_ENTER)))
            assertTrue(keys.accept(key(KeyEvent.VK_ENTER)))
            assertFalse(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
            assertTrue(keys.accept(null), "a separate explicit menu invocation is not a keyboard repeat")
            assertTrue(keys.accept(key(KeyEvent.VK_ESCAPE)))
            assertTrue(keys.dispatchKeyEvent(key(KeyEvent.VK_ESCAPE)), "a held rejection must not fall through to focus return")
            keys.dispose()
            assertTrue(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
        } finally { keys.dispose() }
    }

    @Test
    fun `real turn listener invalidates existing request controls on close replacement Stop project disposal and terminal completion`() = SwingUtilities.invokeAndWait {
        for (change in listOf("close", "replace", "stop", "dispose", "terminal")) {
            val tabs = com.cursoragent.session.SessionTabs()
            val owner = tabs.snapshot().selectedId
            tabs.updateComposer(owner, com.cursoragent.settings.AgentMode.AGENT, "", "prompt", 0)
            val token = tabs.beginTurn(owner)!!.token
            var stopped = false
            var disposed = false
            val project = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(com.intellij.openapi.project.Project::class.java)) { _, method, _ ->
                if (method.name == "isDisposed") disposed else error("Unexpected Project access: ${method.name}")
            } as com.intellij.openapi.project.Project
            val timeline = ChatTimelinePanel()
            val replies = mutableListOf<AgentAnswer>()
            val recorder = com.cursoragent.history.ConversationRecorder(com.cursoragent.history.Conversation()) {}
            recorder.begin(token.turnId, "synthetic")
            val listener = AgentTurnListenerFactory(project, timeline, { _, _ -> }, {}, recorder, {}, ConversationChanges("synthetic"), {},
                onUsageFinish = { _, _ -> }, onToolNotice = {}, onTerminalNotice = { _, _ -> },
            ).create(1, token.turnId, { tabs.accepts(token) }, { stopped }, { true }, { RestoreTarget.UNKNOWN })
            try {
                val request = AgentInputRequest(AgentInput.Plan(null, null, "合成計画")) { replies.add(it); it }
                listener.onStructuredEvent(AgentEvent.Input(request))
                val card = descendants(timeline).filterIsInstance<AgentRequestCard>().single()
                assertTrue(card.canRespond(true), change)
                when (change) {
                    "close" -> tabs.close(owner)
                    "replace" -> { tabs.finishTurn(token); tabs.beginTurn(owner) }
                    "stop" -> stopped = true
                    "dispose" -> disposed = true
                    "terminal" -> listener.onCompleted(0)
                }
                assertFalse(card.canRespond(true), change)
                assertFalse(timeline.hasPendingInput, change)
                card.respond(false)
                card.components.filterIsInstance<JButton>().forEach { it.doClick(0) }
                assertTrue(replies.isEmpty(), change)
            } finally { timeline.runStatus.dispose() }
        }
    }

    private fun key(code: Int, modifiers: Int = 0, release: Boolean = false) = KeyEvent(JButton(),
        if (release) KeyEvent.KEY_RELEASED else KeyEvent.KEY_PRESSED, 1, modifiers, code, KeyEvent.CHAR_UNDEFINED)
}
