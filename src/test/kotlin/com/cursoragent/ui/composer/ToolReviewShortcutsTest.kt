package com.cursoragent.ui.composer

import com.cursoragent.actions.AgentPanelActions
import com.cursoragent.actions.AgentPanelCommand
import com.cursoragent.service.*
import com.cursoragent.ui.timeline.AgentRequestCard
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.cursoragent.ui.toolReviewTarget
import com.cursoragent.ui.composer.image.ImageAttachmentStore
import com.cursoragent.ui.composer.image.ImageDraft
import com.cursoragent.ui.composer.image.ImageInput
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Component
import java.awt.Container
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.text.AttributedString
import java.util.concurrent.Executor
import javax.swing.JButton
import javax.swing.KeyStroke

class ToolReviewShortcutsTest {
    private fun children(value: Component): List<Component> = listOf(value) +
        if (value is Container) value.components.flatMap(::children) else emptyList()

    private fun permission(reply: (AgentAnswer) -> AgentAnswer?) = AgentInputRequest(
        AgentInput.Permission(AgentTool("synthetic-tool", command = "synthetic-command"), listOf(
            PermissionOption("once", "Run", "allow_once"), PermissionOption("skip", "Skip", "reject_once"),
        )), reply,
    )

    @Test
    fun `accepted key holds intercept IDE dispatch and typed events until release`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("tool review key hold").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val queue = com.intellij.ide.IdeEventQueue.getInstance()
                val keys = com.cursoragent.ui.RequestShortcutKeys()
                val lifetime = Disposer.newDisposable()
                val forwarded = mutableListOf<java.awt.AWTEvent>()
                val probe = object : com.intellij.ide.IdeEventQueue.NonLockedEventDispatcher {
                    override fun dispatch(e: java.awt.AWTEvent): Boolean { forwarded.add(e); return true }
                }
                val source = JButton()
                fun key(code: Int, id: Int = KeyEvent.KEY_PRESSED) = KeyEvent(source, id, 1, 0, code, KeyEvent.CHAR_UNDEFINED)
                fun typed(char: Char) = KeyEvent(source, KeyEvent.KEY_TYPED, 1, 0, KeyEvent.VK_UNDEFINED, char)
                fun dispatch(event: KeyEvent, expected: Boolean) {
                    forwarded.clear()
                    queue.dispatchEvent(event)
                    assertEquals(expected, event in forwarded, "event ${event.id}/${event.keyCode}/${event.keyChar.code}")
                }
                try {
                    assertTrue(keys.accept(key(KeyEvent.VK_ENTER)))
                    queue.addDispatcher(probe, lifetime)
                    dispatch(typed('\n'), false)
                    dispatch(key(KeyEvent.VK_ENTER), false)
                    assertFalse(keys.accept(key(KeyEvent.VK_ENTER)))
                    dispatch(typed('\n'), false)
                    dispatch(key(KeyEvent.VK_A), true)
                    dispatch(typed('a'), true)
                    assertTrue(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
                    dispatch(key(KeyEvent.VK_CONTROL, KeyEvent.KEY_RELEASED), true)
                    assertFalse(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
                    dispatch(key(KeyEvent.VK_BACK_SPACE), false)
                    dispatch(typed('\b'), false)
                    dispatch(key(KeyEvent.VK_ENTER, KeyEvent.KEY_RELEASED), true)
                    dispatch(key(KeyEvent.VK_ENTER), true)
                    dispatch(typed('\n'), true)
                    assertFalse(keys.accept(key(KeyEvent.VK_BACK_SPACE)))
                    assertTrue(keys.accept(null), "explicit menu invocation is not a keyboard repeat")
                    assertTrue(keys.accept(key(KeyEvent.VK_ESCAPE)))
                    dispatch(key(KeyEvent.VK_ESCAPE), false)
                    keys.dispose()
                    dispatch(key(KeyEvent.VK_ESCAPE), true)
                    assertFalse(queue.containsDispatcher(keys))
                    assertTrue(keys.accept(key(KeyEvent.VK_F9)))
                    assertTrue(queue.containsDispatcher(keys))
                    // After re-registration the probe precedes the guard, so inspect release directly.
                    assertFalse(keys.dispatch(key(KeyEvent.VK_F9, KeyEvent.KEY_RELEASED)))
                    assertFalse(queue.containsDispatcher(keys))
                } finally {
                    keys.dispose()
                    Disposer.dispose(lifetime)
                }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

    @Test
    fun `real composer and timeline only allow an empty unambiguous current tool review`(@TempDir parent: Path) {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("tool review context").fixture
        fixture.setUp()
        try {
            ImageAttachmentStore(parent).use { store ->
                val image = store.save(ImageInput.clipboard(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)))
                runInEdtAndWait {
                    val lifetime = Disposer.newDisposable()
                    val composer = ComposerPanel(fixture.project)
                    composer.inputArea.setDisposedWith(lifetime)
                    val timeline = ChatTimelinePanel()
                    val work = ArrayDeque<Runnable>()
                    val images = ImageDraft(Executor(work::add), { it() }, { store }, {})
                    composer.installImages(images)
                    val replies = mutableListOf<AgentAnswer>()
                    var current = true
                    var editor = requireNotNull(composer.inputArea.getEditor(true))
                    var focus: Component = editor.contentComponent
                    fun target(accept: Boolean = true) = toolReviewTarget(composer, timeline, focus, accept)
                    try {
                        timeline.addInputRequest(permission { replies.add(it); it }) { current }
                        val first = children(timeline).filterIsInstance<AgentRequestCard>().single()
                        assertSame(first, target())
                        composer.inputArea.text = "next request"
                        assertNull(target())
                        composer.inputArea.text = " \n\t "
                        assertSame(first, target())
                        composer.commands.restoreSelection("synthetic-command")
                        assertNull(target())
                        composer.commands.clearSelection()
                        images.restore(image)
                        assertNull(target())
                        images.clear()
                        images.import { error("cancelled import must not be executed") }
                        assertNull(target())
                        images.clear()
                        composer.showQueueEdit(true)
                        assertNull(target())
                        composer.showQueueEdit(false)
                        composer.setInputEnabled(false)
                        assertNull(target())
                        composer.setInputEnabled(true)
                        editor = requireNotNull(composer.inputArea.getEditor(true))
                        focus = editor.contentComponent
                        val ime = editor.contentComponent.inputMethodListeners.filterIsInstance<PromptImeGuard>().single()
                        ime.inputMethodTextChanged(InputMethodEvent(editor.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            AttributedString("変換中").iterator, 0, null, null))
                        assertNull(target())
                        ime.reset()
                        assertSame(first, target())
                        focus = JButton()
                        assertNull(target())
                        focus = first.components.filterIsInstance<JButton>().first()
                        assertNull(target(), "Enter must not replace a focused button's native operation")
                        assertSame(first, target(false))
                        focus = editor.contentComponent
                        timeline.isActiveTab = false
                        assertNull(target())
                        timeline.isActiveTab = true
                        current = false
                        assertNull(target())
                        current = true
                        timeline.addInputRequest(permission { replies.add(it); it }) { current }
                        assertNull(target(), "an empty input does not identify a decision group")
                        focus = first.components.first()
                        assertSame(first, target())
                        target()!!.respond(true)
                        assertNull(target(false), "a resolved card must not redirect to the remaining request")
                        focus = editor.contentComponent
                        assertNotNull(target(false))
                        target(false)!!.respond(false)
                        assertNull(target())
                        timeline.addInputRequest(AgentInputRequest(AgentInput.Plan(null, null, "synthetic")) { replies.add(it); it })
                        assertNull(target(), "plain Enter is not Plan acceptance")
                        assertNull(target(false))
                        assertEquals(listOf(AgentAnswer.Permission("once"), AgentAnswer.Permission("skip")), replies)
                    } finally {
                        images.close()
                        timeline.runStatus.dispose()
                        Disposer.dispose(lifetime)
                    }
                }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

    @Test
    fun `native input actions honor current review Keymap and restore send and Escape after removal`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("tool review keys").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val lifetime = Disposer.newDisposable()
                val composer = ComposerPanel(fixture.project)
                composer.inputArea.setDisposedWith(lifetime)
                composer.installInputShortcuts(lifetime)
                val timeline = ChatTimelinePanel()
                val manager = ActionManager.getInstance()
                val approve = manager.getAction(AgentPanelCommand.APPROVE_TOOL.actionId)
                val skip = manager.getAction(AgentPanelCommand.SKIP_TOOL.actionId)
                val unfocus = manager.getAction(AgentPanelCommand.UNFOCUS_INPUT.actionId)
                val oldApprove = approve.shortcutSet
                val oldSkip = skip.shortcutSet
                val send = ComposerPanel::class.java.getDeclaredField("sendShortcut").apply { isAccessible = true }.get(composer) as AnAction
                val editor = requireNotNull(composer.inputArea.getEditor(true))
                val replies = mutableListOf<AgentAnswer>()
                var returnedFocus = 0
                val actions = AgentPanelActions({ command ->
                    when (command) {
                        AgentPanelCommand.APPROVE_TOOL, AgentPanelCommand.SKIP_TOOL ->
                            toolReviewTarget(composer, timeline, editor.contentComponent, command == AgentPanelCommand.APPROVE_TOOL) != null
                        AgentPanelCommand.UNFOCUS_INPUT -> composer.canUnfocusFrom(editor.contentComponent)
                        else -> false
                    }
                }) { command, _ ->
                    if (command == AgentPanelCommand.UNFOCUS_INPUT) returnedFocus++
                    else toolReviewTarget(composer, timeline, editor.contentComponent, command == AgentPanelCommand.APPROVE_TOOL)
                        ?.respond(command == AgentPanelCommand.APPROVE_TOOL)
                }
                val context = DataContext { if (AgentPanelActions.KEY.`is`(it)) actions else null }
                fun event(action: AnAction, code: Int) = AnActionEvent(context, action.templatePresentation.clone(), "test", ActionUiKind.NONE,
                    KeyEvent(editor.contentComponent, KeyEvent.KEY_PRESSED, 1, 0, code, KeyEvent.CHAR_UNDEFINED), 0, manager)
                fun enabled(action: AnAction, code: Int): Boolean = event(action, code).let { action.update(it); it.presentation.isEnabled }
                try {
                    assertTrue(ActionUtil.getActions(composer.inputArea).containsAll(listOf(approve, skip, unfocus, send)))
                    approve.shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke("ENTER"))
                    skip.shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke("ESCAPE"))
                    timeline.addInputRequest(permission { replies.add(it); it })
                    assertTrue(enabled(approve, KeyEvent.VK_ENTER))
                    assertFalse(enabled(send, KeyEvent.VK_ENTER))
                    assertTrue(enabled(skip, KeyEvent.VK_ESCAPE))
                    assertFalse(enabled(unfocus, KeyEvent.VK_ESCAPE))
                    unfocus.actionPerformed(event(unfocus, KeyEvent.VK_ESCAPE))
                    assertEquals(0, returnedFocus)
                    approve.shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke("F9"))
                    skip.shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke("F10"))
                    assertTrue(enabled(send, KeyEvent.VK_ENTER))
                    assertTrue(enabled(unfocus, KeyEvent.VK_ESCAPE))
                    unfocus.actionPerformed(event(unfocus, KeyEvent.VK_ESCAPE))
                    assertEquals(1, returnedFocus)
                    assertTrue(replies.isEmpty())
                    approve.shortcutSet = CustomShortcutSet(KeyboardShortcut(KeyStroke.getKeyStroke("ENTER"), KeyStroke.getKeyStroke("F9")))
                    assertFalse(enabled(send, KeyEvent.VK_ENTER), "a remapped two-stroke prefix belongs to review")
                    approve.shortcutSet = CustomShortcutSet.EMPTY
                    skip.shortcutSet = CustomShortcutSet.EMPTY
                    assertTrue(enabled(send, KeyEvent.VK_ENTER))
                    assertTrue(enabled(unfocus, KeyEvent.VK_ESCAPE))
                    approve.shortcutSet = oldApprove
                    skip.shortcutSet = oldSkip
                    // Action invocation rechecks the live target after an enabled update.
                    val queued = mutableListOf<String>()
                    composer.onEnqueue = queued::add
                    composer.setRunning(true)
                    composer.inputArea.text = "new draft"
                    assertFalse(enabled(approve, KeyEvent.VK_ENTER))
                    approve.actionPerformed(event(approve, KeyEvent.VK_ENTER))
                    send.actionPerformed(event(send, KeyEvent.VK_ENTER))
                    assertEquals(listOf("new draft"), queued)
                    assertTrue(replies.isEmpty())
                    composer.clearInput()
                    skip.actionPerformed(event(skip, KeyEvent.VK_ESCAPE))
                    assertEquals(listOf(AgentAnswer.Permission("skip")), replies)
                    assertTrue(enabled(unfocus, KeyEvent.VK_ESCAPE))
                } finally {
                    approve.shortcutSet = oldApprove
                    skip.shortcutSet = oldSkip
                    timeline.runStatus.dispose()
                    Disposer.dispose(lifetime)
                    assertFalse(ActionUtil.getActions(composer.inputArea).any { it === approve || it === skip })
                }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
}
