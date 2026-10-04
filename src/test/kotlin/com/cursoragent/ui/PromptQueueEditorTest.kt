package com.cursoragent.ui

import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.composer.PromptImeGuard
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import com.cursoragent.ui.composer.context.SelectionContext
import com.cursoragent.ui.composer.image.ImageAttachmentStore
import com.cursoragent.ui.composer.image.ImageDraft
import com.cursoragent.ui.composer.image.ImageInput
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.event.InputMethodEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.text.AttributedString
import java.util.ArrayDeque
import java.util.concurrent.Executor

class PromptQueueEditorTest {
    private class Tasks : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.remove().run() }
    }

    @Test
    fun `inline queue editing preserves complete drafts leases owner and native send guard`(@TempDir parent: Path) {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("inline queue edit").fixture
        fixture.setUp()
        try {
            ImageAttachmentStore(parent).use { store ->
                val originalImage = store.save(ImageInput.clipboard(BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB)))
                val queuedImage = store.save(ImageInput.clipboard(BufferedImage(40, 50, BufferedImage.TYPE_INT_ARGB)))
                val replacementImage = store.save(ImageInput.clipboard(BufferedImage(60, 70, BufferedImage.TYPE_INT_ARGB)))
                val worker = Tasks()
                val ui = Tasks()
                runInEdtAndWait {
                    val project = fixture.project
                    val lifetime = Disposer.newDisposable()
                    val composer = ComposerPanel(project)
                    composer.inputArea.setDisposedWith(lifetime)
                    val editor = requireNotNull(composer.inputArea.getEditor(true))
                    val imageDraft = ImageDraft(worker, { ui.execute(it) }, { store }, {})
                    composer.installImages(imageDraft)
                    val queue = PromptQueue("owner") { worker.execute { it.close() } }
                    val selection = SelectionContext("file:///synthetic.kt", "synthetic.kt", 0, 3, 1, 1, "old", 1)
                    val context = PromptContextSnapshot(listOf(selection), emptyList(), false)
                    composer.inputArea.text = "draft\ntext"
                    editor.caretModel.moveToOffset(3)
                    editor.selectionModel.setSelection(1, 3)
                    assertNotNull(editor.caretModel.addCaret(com.intellij.openapi.editor.VisualPosition(1, 2)))
                    val carets = editor.caretModel.caretsAndSelections
                    composer.modeSelector.selectMode(AgentMode.ASK)
                    composer.modelSelector.restoreSelection("draft-model")
                    composer.promptContext.restore(context)
                    composer.commands.restoreSelection("original-command")
                    imageDraft.restore(originalImage)
                    queue.add("queued", AgentMode.PLAN, "queued-model", context, "queued-command", queuedImage)
                    val first = queue.snapshot().single()
                    val ticket = queue.ticket(1)!!
                    var current = true
                    var sends = 0
                    val errors = mutableListOf<String>()
                    val idleSubmissions = mutableListOf<QueuedPrompt>()
                    var queueReady = 0
                    val editing = PromptQueueEditor(queue, composer, { current }, { worker.execute { it.close() } }, {}, errors::add,
                        { queueReady++ }, idleSubmissions::add)
                    composer.onSubmitQueueEdit = editing::submit
                    composer.onCancelQueueEdit = editing::cancel
                    composer.onSend = { sends++ }
                    val send = ComposerPanel::class.java.getDeclaredField("sendShortcut").apply { isAccessible = true }.get(composer) as AnAction
                    fun submit() = send.actionPerformed(AnActionEvent(DataContext.EMPTY_CONTEXT, send.templatePresentation.clone(),
                        "test", ActionUiKind.NONE, null, 0, unusedActionManager))
                    try {
                        composer.setRunning(true)
                        editing.begin(first.id)
                        assertTrue(editing.isEditing)
                        assertFalse(queue.paused)
                        assertNull(queue.next(), "the edited head is held without a manual pause")
                        assertFalse(queue.dispatch(ticket, 1, true) { error("pre-edit send must be invalidated") })
                        assertEquals("queued", composer.inputArea.text)
                        assertEquals("queued-command", composer.commands.selectedName)
                        assertEquals(AgentMode.PLAN, composer.selection.mode)
                        assertEquals("queued-model", composer.selection.selectedModel)
                        composer.showAcpConfiguration(com.cursoragent.service.AgentEvent.Configuration("agent", "provider-model",
                            listOf(com.cursoragent.service.ModelOption("provider-model", "Provider"))))
                        assertEquals(AgentMode.PLAN, composer.selection.mode, "a running turn's advertisement must not replace the edit")
                        assertEquals("queued-model", composer.selection.selectedModel)
                        assertEquals(40, imageDraft.attachment!!.width)
                        composer.inputArea.text = " 東京  alpha "
                        val ime = editor.contentComponent.inputMethodListeners.filterIsInstance<PromptImeGuard>().single()
                        ime.inputMethodTextChanged(InputMethodEvent(editor.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                            AttributedString("未確定").iterator, 0, null, null))
                        submit()
                        editing.cancel()
                        assertTrue(editing.isEditing)
                        assertEquals(first, queue.snapshot().single())
                        ime.reset()
                        current = false
                        submit(); editing.cancel()
                        assertTrue(editing.isEditing)
                        current = true
                        composer.promptContext.addSelection(selection.copy(fileUrl = "file:///missing.kt", path = "missing.kt"))
                        submit()
                        assertTrue(editing.isEditing, "new stale selections must fail validation")
                        assertEquals(first, queue.snapshot().single())
                        composer.promptContext.restore(context)
                        submit() // The actual send shortcut saves a queued item even while the run is active.
                        assertFalse(editing.isEditing)
                        val updated = queue.snapshot().single()
                        assertEquals(first.id, updated.id)
                        assertEquals(" 東京  alpha ", updated.text, "command arguments retain whitespace")
                        assertEquals(context, updated.context, "an unchanged queued snapshot remains valid after file edits")
                        assertEquals(0, sends)
                        assertTrue(idleSubmissions.isEmpty(), "a running edit is re-queued, not sent now")
                        assertFalse(queue.paused)
                        assertSame(updated, queue.next())
                        assertEquals("draft\ntext", composer.inputArea.text)
                        assertEquals(carets.map { it.caretPosition to (it.selectionStart to it.selectionEnd) },
                            editor.caretModel.caretsAndSelections.map { it.caretPosition to (it.selectionStart to it.selectionEnd) })
                        assertEquals(AgentMode.ASK, composer.selection.mode)
                        assertEquals("draft-model", composer.selection.selectedModel)
                        assertEquals("original-command", composer.commands.selectedName)
                        assertEquals(context, composer.promptContext.draft.snapshot())
                        assertEquals(20, imageDraft.attachment!!.width)

                        editing.begin(updated.id)
                        imageDraft.restore(replacementImage)
                        composer.inputArea.text = " updated body "
                        composer.commands.clearSelection()
                        composer.modeSelector.selectMode(AgentMode.AGENT)
                        composer.modelSelector.restoreSelection("edited-model")
                        composer.promptContext.restore(context.copy(automaticEnabled = true))
                        composer.setRunning(false)
                        submit()
                        assertFalse(editing.isEditing)
                        assertSame(queue.snapshot().single(), idleSubmissions.single(), "idle submission uses the updated queued snapshot")
                        assertEquals(" updated body ", queue.snapshot().single().text, "saving an edit does not normalize the queued text")
                        assertNull(queue.snapshot().single().command)
                        assertEquals("edited-model", queue.snapshot().single().model)
                        assertEquals(AgentMode.AGENT, queue.snapshot().single().mode)
                        assertTrue(queue.snapshot().single().context!!.automaticEnabled)
                        assertEquals(60, queue.snapshot().single().image!!.width)
                        assertEquals(20, imageDraft.attachment!!.width)
                        assertEquals("original-command", composer.commands.selectedName)

                        editing.begin(updated.id)
                        composer.inputArea.text = "unsaved queue edit"
                        queue.edit(updated.id, "concurrent change")
                        submit()
                        assertTrue(editing.isEditing, "a stale edit must stay available for copying")
                        assertEquals("unsaved queue edit", composer.inputArea.text)
                        assertEquals("concurrent change", queue.snapshot().single().text)
                        editing.cancel()
                        assertEquals("draft\ntext", composer.inputArea.text)
                        assertTrue(queue.paused, "a concurrent explicit pause must survive cancellation")
                        assertTrue(queueReady > 0)

                        // Undo within a draft remains native; it cannot put queue text into the restored draft.
                        val file = TextEditorProvider.getInstance().getTextEditor(editor)
                        val undo = UndoManager.getInstance(project)
                        WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(editor.document.textLength, "!") }
                        undo.undo(file)
                        assertEquals("draft\ntext", composer.inputArea.text)
                        assertThrows(RuntimeException::class.java) { undo.undo(file) }
                        assertEquals("draft\ntext", composer.inputArea.text)

                        editing.begin(updated.id)
                        imageDraft.clear() // Removing the editing image cannot release the queued or hidden draft image.
                        imageDraft.import { ImageInput.clipboard(BufferedImage(60, 70, BufferedImage.TYPE_INT_ARGB)) }
                        submit()
                        assertTrue(editing.isEditing, "wait for attachment import before save")
                        editing.cancel() // Cancels pending import and restores the original independent lease.
                        assertEquals(20, imageDraft.attachment!!.width)
                        assertEquals(60, queue.snapshot().single().image!!.width)
                        editing.begin(updated.id)
                        editing.close() // Discarding a tab releases both the hidden draft and editing draft independently.
                        editing.close()
                        editing.begin(updated.id)
                        assertFalse(editing.isEditing)
                        assertEquals(2, errors.size)
                    } finally {
                        editing.close()
                        imageDraft.close()
                        queue.clear()
                        Disposer.dispose(lifetime)
                    }
                }
                worker.drain()
                runInEdtAndWait { ui.drain() }
                worker.drain()
                assertEquals(0, Files.walk(parent).use { paths -> paths.filter { it.fileName.toString().endsWith(".png") }.count() })
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
}
