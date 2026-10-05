package com.cursoragent.ui.composer

import com.cursoragent.ui.composer.image.ImageAttachmentStore
import com.cursoragent.ui.composer.image.ImageDraft
import com.cursoragent.ui.composer.image.ImageInput
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
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
import java.util.ArrayDeque
import java.util.concurrent.Executor
import javax.swing.JButton

class ComposerSubmissionTest {
    private fun components(value: Component): List<Component> = listOf(value) +
        if (value is Container) value.components.flatMap(::components) else emptyList()

    @Test
    fun `initial submit shares input guards and held Enter state with ordinary send`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("initial chat submit").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val lifetime = Disposer.newDisposable()
                val composer = ComposerPanel(fixture.project)
                composer.inputArea.setDisposedWith(lifetime)
                composer.installInputShortcuts(lifetime)
                val action = ActionManager.getInstance().getAction(com.cursoragent.actions.AgentPanelCommand.SUBMIT_INITIAL.actionId)
                assertNotNull(action)
                val sent = mutableListOf<String>()
                composer.onSend = sent::add
                composer.onEnqueue = { fail("Initial submit must not turn into a queued follow-up") }
                var emptyConversation = true
                val target = com.cursoragent.actions.AgentPanelActions(
                    { emptyConversation && composer.canSubmitInitial },
                    { _, event -> composer.submitInitial(event) },
                )
                val context = DataContext { if (com.cursoragent.actions.AgentPanelActions.KEY.`is`(it)) target else null }
                fun submit(key: KeyEvent? = null) = action.actionPerformed(AnActionEvent(context, action.templatePresentation.clone(),
                    "test", ActionUiKind.NONE, key, 0, ActionManager.getInstance()))
                try {
                    assertTrue(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(composer.inputArea).contains(action))
                    assertFalse(composer.canSubmitInitial)
                    composer.inputArea.text = " \n\t "
                    assertFalse(composer.canSubmitInitial)
                    submit()
                    assertTrue(sent.isEmpty())
                    composer.inputArea.text = " 最初の依頼 "
                    assertTrue(composer.canSubmitInitial)
                    var editor = requireNotNull(composer.inputArea.getEditor(true))
                    val held = KeyEvent(editor.contentComponent, KeyEvent.KEY_PRESSED, 1,
                        java.awt.event.InputEvent.CTRL_DOWN_MASK or java.awt.event.InputEvent.SHIFT_DOWN_MASK,
                        KeyEvent.VK_ENTER, '\n')
                    submit(held)
                    submit(held)
                    assertEquals(listOf("最初の依頼"), sent)
                    val regular = ComposerPanel::class.java.getDeclaredField("sendShortcut").apply { isAccessible = true }.get(composer) as AnAction
                    regular.actionPerformed(AnActionEvent(context, regular.templatePresentation.clone(), "test", ActionUiKind.NONE, held, 0, ActionManager.getInstance()))
                    assertEquals(1, sent.size, "Changing modifiers while holding Enter must not send twice")
                    editor.contentComponent.keyListeners.forEach { it.keyReleased(KeyEvent(editor.contentComponent, KeyEvent.KEY_RELEASED, 2, 0, KeyEvent.VK_ENTER, '\n')) }
                    emptyConversation = false
                    submit(held)
                    assertEquals(1, sent.size, "The live owner can lose initial-conversation availability after update")
                    emptyConversation = true
                    composer.setRunning(true)
                    assertFalse(composer.canSubmitInitial)
                    submit()
                    composer.setRunning(false)
                    composer.showQueueEdit(true)
                    assertFalse(composer.canSubmitInitial)
                    submit()
                    composer.showQueueEdit(false)
                    composer.setInputEnabled(false)
                    assertFalse(composer.canSubmitInitial)
                    submit()
                    composer.setInputEnabled(true)
                    editor = requireNotNull(composer.inputArea.getEditor(true))
                    val ime = editor.contentComponent.inputMethodListeners.filterIsInstance<PromptImeGuard>().single()
                    ime.inputMethodTextChanged(InputMethodEvent(editor.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        AttributedString("変換中").iterator, 0, null, null))
                    assertFalse(composer.canSubmitInitial)
                    submit()
                    ime.reset()
                    assertEquals(1, sent.size)
                    assertEquals(" 最初の依頼 ", composer.inputArea.text)
                    submit()
                    assertEquals(listOf("最初の依頼", "最初の依頼"), sent)
                    Disposer.dispose(lifetime)
                    assertFalse(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(composer.inputArea).contains(action))
                } finally { if (!Disposer.isDisposed(lifetime)) Disposer.dispose(lifetime) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

    @Test
    fun `mode cycling is registered on recreated prompt editors and cannot bypass ACP busy state`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("mode cycle").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val lifetime = Disposer.newDisposable()
                val composer = ComposerPanel(fixture.project)
                composer.inputArea.setDisposedWith(lifetime)
                val globalMode = com.cursoragent.settings.AgentSettingsState.getInstance().mode
                try {
                    composer.inputArea.text = "draft stays here"
                    val action = ActionManager.getInstance().getAction(com.cursoragent.actions.AgentPanelCommand.MODE_MENU.actionId)
                    assertNotNull(action)
                    var editor = requireNotNull(composer.inputArea.getEditor(true))
                    assertTrue(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(editor.contentComponent).contains(action))
                    composer.modeSelector.selectMode(com.cursoragent.settings.AgentMode.AGENT)
                    composer.cycleMode()
                    assertEquals(com.cursoragent.settings.AgentMode.PLAN, composer.selection.mode)
                    composer.setRunning(true)
                    assertTrue(composer.canCycleMode, "print next-turn selection remains editable during execution")
                    composer.cycleMode()
                    assertEquals(com.cursoragent.settings.AgentMode.ASK, composer.selection.mode)
                    composer.useAcp()
                    composer.setRunning(true)
                    assertFalse(composer.canCycleMode)
                    composer.modeSelector.isEnabled = true // Stale widget state must not bypass ACP's run guard.
                    composer.cycleMode()
                    assertEquals(com.cursoragent.settings.AgentMode.ASK, composer.selection.mode)
                    composer.setRunning(false)
                    composer.cycleMode()
                    assertEquals(com.cursoragent.settings.AgentMode.AGENT, composer.selection.mode)
                    composer.setInputEnabled(false)
                    assertFalse(composer.canCycleMode)
                    composer.cycleMode()
                    assertEquals(com.cursoragent.settings.AgentMode.AGENT, composer.selection.mode)
                    composer.setInputEnabled(true)
                    editor = requireNotNull(composer.inputArea.getEditor(true))
                    assertTrue(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(editor.contentComponent).contains(action))
                    assertEquals("draft stays here", composer.inputArea.text)
                    assertEquals(globalMode, com.cursoragent.settings.AgentSettingsState.getInstance().mode)
                } finally {
                    Disposer.dispose(lifetime)
                }
            }
        } finally {
            runInEdtAndWait { fixture.tearDown() }
        }
    }

    @Test
    fun `the configured send action queues while running and preserves native input guards and Stop`(@TempDir parent: Path) {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("composer submission").fixture
        fixture.setUp()
        try {
            ImageAttachmentStore(parent).use { store ->
                val image = store.save(ImageInput.clipboard(BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB)))
                val work = ArrayDeque<Runnable>()
                runInEdtAndWait {
                    val lifetime = Disposer.newDisposable()
                    val composer = ComposerPanel(fixture.project)
                    composer.inputArea.setDisposedWith(lifetime)
                    val inputActions = listOf(
                        ActionManager.getInstance().getAction(com.cursoragent.actions.AgentQueueCommand.RETURN_TO_INPUT.actionId),
                        ActionManager.getInstance().getAction(com.cursoragent.actions.AgentPanelCommand.UNFOCUS_INPUT.actionId),
                        ActionManager.getInstance().getAction(com.cursoragent.actions.AgentPanelCommand.ACCEPT_PENDING.actionId),
                    )
                    assertTrue(inputActions.all { it != null }, "registered plugin input actions must be available")
                    composer.installInputShortcuts(lifetime)
                    val localActions = com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(composer.inputArea)
                    assertTrue(localActions.containsAll(inputActions), "Escape and pending acceptance must be candidates at the same input component as send")
                    var editor = requireNotNull(composer.inputArea.getEditor(true))
                    val images = ImageDraft(Executor { work.add(it) }, { it() }, { store }, {})
                    composer.installImages(images)
                    val sent = mutableListOf<String>()
                    val queued = mutableListOf<String>()
                    var acceptQueue = true
                    var stopped = 0
                    composer.onSend = sent::add
                    composer.onEnqueue = { text ->
                        if (acceptQueue) { queued.add(text); composer.clearInput() }
                    }
                    composer.onStop = { stopped++ }
                    val send = ComposerPanel::class.java.getDeclaredField("sendShortcut").apply { isAccessible = true }.get(composer) as AnAction
                    fun submit(key: KeyEvent? = null) = send.actionPerformed(AnActionEvent(DataContext.EMPTY_CONTEXT, send.templatePresentation.clone(),
                        "test", ActionUiKind.NONE, key, 0, ActionManager.getInstance()))
                    fun release() = editor.contentComponent.keyListeners.forEach { listener ->
                        listener.keyReleased(KeyEvent(editor.contentComponent, KeyEvent.KEY_RELEASED, 2, 0, KeyEvent.VK_ENTER, '\n'))
                    }
                    try {
                        assertTrue(composer.canResetFrom(editor.contentComponent))
                        assertTrue(composer.canUnfocusFrom(editor.contentComponent))
                        assertFalse(composer.canUnfocusFrom(composer))
                        assertFalse(composer.canUnfocusFrom(JButton()))
                        assertFalse(composer.canUnfocusFrom(null))
                        composer.showQueueEdit(true)
                        assertFalse(composer.canUnfocusFrom(editor.contentComponent), "Escape must cancel queue editing before returning to the workspace")
                        composer.showQueueEdit(false)
                        assertFalse(composer.canResetFrom(composer), "reset belongs to the input, not the entire panel")
                        assertFalse(composer.canResetFrom(JButton()), "another component/project cannot reset this input")
                        assertFalse(composer.canResetFrom(null))
                        composer.inputArea.text = " idle draft "
                        submit()
                        assertEquals(listOf("idle draft"), sent)
                        composer.setRunning(true)
                        assertTrue(composer.canResetFrom(editor.contentComponent), "reset may replace a running view without stopping its owner")
                        assertTrue(composer.canUnfocusFrom(editor.contentComponent), "focus return does not cancel a running turn")
                        composer.inputArea.text = " first queue "
                        val heldEnter = KeyEvent(editor.contentComponent, KeyEvent.KEY_PRESSED, 1, 0, KeyEvent.VK_ENTER, '\n')
                        submit(heldEnter)
                        composer.inputArea.text = "restored draft"
                        submit(heldEnter)
                        assertEquals(listOf("first queue"), queued, "holding Enter must not enqueue a draft restored by the first action")
                        assertEquals("restored draft", composer.inputArea.text)
                        release()
                        composer.inputArea.text = "retained"
                        acceptQueue = false // The owner/run may have changed before the controller receives it.
                        submit()
                        assertEquals("retained", composer.inputArea.text)
                        assertEquals(1, queued.size)
                        acceptQueue = true
                        composer.setInputEnabled(false)
                        assertFalse(composer.canResetFrom(editor.contentComponent))
                        assertFalse(composer.canUnfocusFrom(editor.contentComponent))
                        submit()
                        composer.setInputEnabled(true)
                        // EditorTextField replaces its native editor when enabled state changes.
                        editor = requireNotNull(composer.inputArea.getEditor(true))
                        assertTrue(composer.canResetFrom(editor.contentComponent))
                        assertTrue(composer.canUnfocusFrom(editor.contentComponent))
                        val ime = editor.contentComponent.inputMethodListeners.filterIsInstance<PromptImeGuard>().single()
                        ime.inputMethodTextChanged(InputMethodEvent(editor.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            AttributedString("変換中").iterator, 0, null, null))
                        assertFalse(composer.canResetFrom(editor.contentComponent))
                        assertFalse(composer.canUnfocusFrom(editor.contentComponent))
                        submit()
                        assertEquals(1, queued.size)
                        assertEquals("retained", composer.inputArea.text)
                        ime.reset()
                        images.import { error("pending import must not be read by submit") }
                        assertFalse(composer.canResetFrom(editor.contentComponent))
                        assertTrue(composer.canUnfocusFrom(editor.contentComponent), "focus return can leave an import running in its original draft")
                        submit()
                        assertEquals(1, queued.size)
                        images.clear()
                        composer.clearInput()
                        composer.commands.restoreSelection("command")
                        composer.inputArea.text = "  raw arguments  "
                        submit()
                        assertEquals("  raw arguments  ", queued.last())
                        composer.commands.restoreSelection("command-only")
                        submit()
                        assertEquals("", queued.last(), "command-only requests still reach queue validation")
                        images.restore(image)
                        submit()
                        assertEquals(4, queued.size, "an image-only request also reaches queue validation")
                        composer.inputArea.text = "button"
                        components(composer).filterIsInstance<JButton>().single { it.text == "予約に追加" }.doClick(0)
                        assertEquals("button", queued.last())
                        components(composer).filterIsInstance<SelectorButton>().single { it.accessibleContext.accessibleName == "停止" }.doClick(0)
                        assertEquals(1, stopped)
                        assertEquals(5, queued.size)
                        assertEquals(listOf("idle draft"), sent, "running submissions must never call ordinary send")
                        composer.setRunning(false)
                        composer.inputArea.text = "after completion"
                        submit(heldEnter)
                        assertEquals(listOf("idle draft", "after completion"), sent)
                        release()
                    } finally {
                        images.close()
                        Disposer.dispose(lifetime)
                        assertTrue(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(composer.inputArea).none { it in inputActions })
                    }
                }
                while (work.isNotEmpty()) work.remove().run()
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
}
