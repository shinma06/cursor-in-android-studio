package com.cursoragent.ui.composer.context

import com.cursoragent.ui.composer.ComposerPanel
import com.intellij.codeInsight.editorActions.CopyHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCopyPasteHelper
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.lang.reflect.Proxy
import java.awt.event.InputMethodEvent
import java.awt.image.BufferedImage
import java.text.AttributedString
import java.nio.file.Files
import java.nio.file.Path

class ClipboardContextTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `native copy carries its source and external changed foreign and stale copies stay plain`() = withFixture { project, _ ->
        val path = directory.resolve("日本語.txt")
        Files.writeString(path, "first line\n日本語 second\nlast")
        val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
        val psi = requireNotNull(PsiManager.getInstance(project).findFile(file))
        val editor = EditorFactory.getInstance().createEditor(document, project)
        try {
            editor.selectionModel.setSelection(0, document.textLength)
            val transfer = requireNotNull(CopyHandler.getSelection(editor, project, psi, EditorCopyPasteHelper.CopyPasteOptions.DEFAULT))
            val copied = requireNotNull(clipboardContext(transfer, project))
            assertEquals(file.url, copied.selections.single().fileUrl)
            assertEquals(1, copied.selections.single().startLine)
            assertEquals(3, copied.selections.single().endLine)
            assertEquals(document.text, copied.selections.single().text)
            assertNull(clipboardContext(StringSelection(copied.text), project), "equal text without copy metadata has no provenance")
            assertNull(clipboardContext(ContextTransferable(StringSelection("different\ncopy"), copied), project))
            val foreign = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
                when (method.name) { "isDisposed" -> false; "getLocationHash" -> "another project"; else -> error(method.name) }
            } as Project
            assertNull(clipboardContext(transfer, foreign))
            assertNotNull(clipboardContext(ContextTransferable(StringSelection(copied.text.replace("\n", "\r\n") + "\r\n"), copied), project))
            editor.selectionModel.setSelection(0, 5)
            val second = requireNotNull(editor.caretModel.addCaret(VisualPosition(1, 0)))
            second.setSelection(document.getLineStartOffset(1), document.getLineEndOffset(1))
            val multi = requireNotNull(CopyHandler.getSelection(editor, project, psi, EditorCopyPasteHelper.CopyPasteOptions.DEFAULT))
            val ranges = requireNotNull(clipboardContext(multi, project)).selections
            assertEquals(listOf("first", "日本語 second"), ranges.map { it.text })
            assertEquals(listOf(1, 2), ranges.map { it.startLine })
            editor.caretModel.removeSecondaryCarets()
            val draft = PromptContextDraft().apply { add(copied.selections.single()) }
            val queued = draft.snapshot(EditorContextReader::isCurrent)
            WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "changed ") }
            assertNull(clipboardContext(transfer, project), "new paste cannot silently attach a changed file")
            assertEquals(copied.text, queued.selections.single().text, "accepted queue context remains its own snapshot")
            assertEquals(copied.text, transfer.getTransferData(DataFlavor.stringFlavor))
            assertThrows(IllegalArgumentException::class.java) { draft.snapshot(EditorContextReader::isCurrent) }
            editor.selectionModel.setSelection(0, 4)
            val single = requireNotNull(CopyHandler.getSelection(editor, project, psi, EditorCopyPasteHelper.CopyPasteOptions.DEFAULT))
            assertNull(clipboardContext(single, project), "single-line text follows the fixed Cursor paste condition")
        } finally { EditorFactory.getInstance().releaseEditor(editor) }
    }

    @Test
    fun `marked native prompt consumes context while plain action replaces text with native undo`() = withFixture { project, clipboard ->
        val lifetime = Disposer.newDisposable()
        val composer = ComposerPanel(project)
        composer.inputArea.setDisposedWith(lifetime)
        try {
            composer.inputArea.text = "keep draft"
            var editor = requireNotNull(composer.inputArea.getEditor(true))
            val terminal = TerminalContext("copy-1", "Build", 2, 3, "build log\n日本語")
            clipboard.setContents(ContextTransferable(StringSelection(terminal.text), ClipboardContextData(project.locationHash, terminal.text, terminal = terminal)))
            val paste = requireNotNull(ActionManager.getInstance().getAction("EditorPaste"))
            val plain = requireNotNull(ActionManager.getInstance().getAction("CursorAgent.PastePlain"))
            assertTrue(ActionUtil.getActions(editor.contentComponent).contains(plain))
            editor.selectionModel.setSelection(1, 4)
            assertTrue(requireNotNull(editor.getUserData(PROMPT_CONTEXT_PASTE)).available())
            assertNotNull(clipboardContext(requireNotNull(clipboard.contents), project))
            paste.actionPerformed(event(paste, project, editor))
            assertEquals("keep draft", composer.inputArea.text)
            assertEquals(1 to 4, editor.selectionModel.selectionStart to editor.selectionModel.selectionEnd)
            assertEquals(listOf(terminal), composer.promptContext.snapshot().terminals)
            val queued = composer.promptContext.snapshot()
            plain.actionPerformed(event(plain, project, editor))
            assertEquals("k${terminal.text} draft", composer.inputArea.text)
            assertEquals(listOf(terminal), composer.promptContext.snapshot().terminals, "plain paste must not add another context")
            val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
            UndoManager.getInstance(project).undo(textEditor)
            assertEquals("keep draft", composer.inputArea.text)
            composer.promptContext.clearExplicit()
            assertEquals(listOf(terminal), queued.terminals)
            assertFalse(composer.promptContext.draft.hasExplicit)
            composer.inputArea.clipboardContextAvailable = { false }
            val blocked = event(plain, project, editor)
            plain.update(blocked)
            assertFalse(blocked.presentation.isEnabled)
            plain.actionPerformed(blocked)
            assertEquals("keep draft", composer.inputArea.text)
            composer.inputArea.clipboardContextAvailable = { true }
            val composing = InputMethodEvent(editor.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                AttributedString("未確定").iterator, 0, null, null)
            editor.contentComponent.inputMethodListeners.forEach { it.inputMethodTextChanged(composing) }
            assertTrue(composer.inputArea.isComposing)
            plain.actionPerformed(event(plain, project, editor))
            assertEquals("keep draft", composer.inputArea.text)
            assertFalse(pasteClipboardContext(editor, requireNotNull(clipboard.contents)))
            composer.setInputEnabled(false)
            plain.actionPerformed(event(plain, project, editor)) // Stale editor before recreation.
            composer.setInputEnabled(true)
            editor = requireNotNull(composer.inputArea.getEditor(true))
            assertTrue(ActionUtil.getActions(editor.contentComponent).contains(plain))
            assertNotNull(editor.getUserData(PROMPT_CONTEXT_PASTE))
        } finally { Disposer.dispose(lifetime) }
    }

    @Test
    fun `native paste delegates unknown text and ordinary editors while preserving prompt image input`() = withFixture { project, clipboard ->
        val lifetime = Disposer.newDisposable()
        val composer = ComposerPanel(project)
        composer.inputArea.setDisposedWith(lifetime)
        val ordinary = EditorFactory.getInstance().createEditor(EditorFactory.getInstance().createDocument(""), project)
        try {
            val prompt = requireNotNull(composer.inputArea.getEditor(true))
            val paste = requireNotNull(ActionManager.getInstance().getAction("EditorPaste"))
            val plain = requireNotNull(ActionManager.getInstance().getAction("CursorAgent.PastePlain"))
            val terminal = TerminalContext("copy", "Build", 1, 2, "copied\nlog")
            clipboard.setContents(ContextTransferable(StringSelection(terminal.text), ClipboardContextData(project.locationHash, terminal.text, terminal = terminal)))
            paste.actionPerformed(event(paste, project, ordinary))
            assertEquals(terminal.text, ordinary.document.text)
            assertFalse(composer.promptContext.draft.hasExplicit)
            val foreign = event(plain, project, ordinary)
            plain.update(foreign)
            assertFalse(foreign.presentation.isEnabled)
            plain.actionPerformed(foreign)
            assertEquals(terminal.text, ordinary.document.text)

            clipboard.setContents(StringSelection("外部コピー\nsecond line"))
            paste.actionPerformed(event(paste, project, prompt))
            assertEquals("外部コピー\nsecond line", composer.inputArea.text)
            assertFalse(composer.promptContext.draft.hasExplicit)
            val text = composer.inputArea.text
            var images = 0
            composer.inputArea.onImageTransfer = { images++; true }
            val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            clipboard.setContents(object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
                override fun getTransferData(flavor: DataFlavor): Any = image
            })
            paste.actionPerformed(event(paste, project, prompt))
            assertEquals(1, images, "the actual native Paste action must still reach the image importer")
            assertEquals(text, composer.inputArea.text)
        } finally {
            EditorFactory.getInstance().releaseEditor(ordinary)
            Disposer.dispose(lifetime)
        }
    }

    @Test
    fun `invalid or failing metadata leaves the original transferable available for native fallback`() = withFixture { project, _ ->
        val malformed = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(ClipboardContextData.FLAVOR, DataFlavor.stringFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = true
            override fun getTransferData(flavor: DataFlavor): Any = if (flavor == DataFlavor.stringFlavor) "keep\ntext" else "not context metadata"
        }
        assertNull(clipboardContext(malformed, project))
        assertEquals("keep\ntext", malformed.getTransferData(DataFlavor.stringFlavor))
        val failing = object : Transferable by malformed {
            override fun getTransferData(flavor: DataFlavor): Any = throw IllegalStateException("private clipboard failure")
        }
        assertNull(clipboardContext(failing, project))
        val terminal = TerminalContext("id", "log", 1, 2, "actual\noutput")
        val inconsistent = ClipboardContextData(project.locationHash, "wrong\ntext", terminal = terminal)
        assertNull(clipboardContext(ContextTransferable(StringSelection(inconsistent.text), inconsistent), project))
    }

    private fun event(action: AnAction, project: Project, editor: Editor) = AnActionEvent(
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, editor)
            .add(PlatformDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
            .build(), action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, ActionManager.getInstance(),
    )

    private fun withFixture(check: (Project, MemoryClipboard) -> Unit) {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("context clipboard").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val lifetime = Disposer.newDisposable()
                val clipboard = MemoryClipboard()
                ApplicationManager.getApplication().replaceService(CopyPasteManager::class.java, clipboard, lifetime)
                try { check(fixture.project, clipboard) } finally { Disposer.dispose(lifetime) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
}

/** Test native actions without reading or changing the user's OS clipboard. */
internal class MemoryClipboard : CopyPasteManager() {
    private var value: Transferable? = null
    override fun getContents() = value
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getContents(flavor: DataFlavor): T? = if (value?.isDataFlavorSupported(flavor) == true) value?.getTransferData(flavor) as T? else null
    override fun getAllContents(): Array<Transferable> = listOfNotNull(value).toTypedArray()
    override fun setContents(content: Transferable) { value = content }
    override fun areDataFlavorsAvailable(vararg flavors: DataFlavor) = flavors.any { value?.isDataFlavorSupported(it) == true }
    override fun isCutElement(element: Any?) = false
    override fun stopKillRings() {}
    override fun stopKillRings(document: com.intellij.openapi.editor.Document) {}
    override fun addContentChangedListener(listener: ContentChangedListener) {}
    override fun addContentChangedListener(listener: ContentChangedListener, parentDisposable: com.intellij.openapi.Disposable) {}
    override fun removeContentChangedListener(listener: ContentChangedListener) {}
}
