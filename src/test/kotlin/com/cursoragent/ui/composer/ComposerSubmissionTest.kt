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
                    val editor = requireNotNull(composer.inputArea.getEditor(true))
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
                        composer.inputArea.text = " idle draft "
                        submit()
                        assertEquals(listOf("idle draft"), sent)
                        composer.setRunning(true)
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
                        submit()
                        composer.setInputEnabled(true)
                        val ime = editor.contentComponent.inputMethodListeners.filterIsInstance<PromptImeGuard>().single()
                        ime.inputMethodTextChanged(InputMethodEvent(editor.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            AttributedString("変換中").iterator, 0, null, null))
                        submit()
                        assertEquals(1, queued.size)
                        assertEquals("retained", composer.inputArea.text)
                        ime.reset()
                        images.import { error("pending import must not be read by submit") }
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
                    } finally { images.close(); Disposer.dispose(lifetime) }
                }
                while (work.isNotEmpty()) work.remove().run()
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
}
