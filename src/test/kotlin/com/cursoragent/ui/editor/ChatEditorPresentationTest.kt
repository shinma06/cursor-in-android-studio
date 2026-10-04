package com.cursoragent.ui.editor

import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.composer.GrowingPromptField
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ChatEditorPresentationTest {
    @Test
    fun `split views preserve the same input editor caret draft and running session until final close`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("chat editor ownership").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val project = fixture.project
                val sessions = SessionTabs()
                val id = sessions.snapshot().selectedId
                sessions.updateComposer(id, AgentMode.AGENT, "model", "prompt", 6)
                val token = requireNotNull(sessions.beginTurn(id)?.token)
                val field = GrowingPromptField(project)
                val content = JPanel(BorderLayout()).apply { add(field) }
                var returned = 0
                var selected = 0
                val presentation = ChatEditorPresentation(project, content, field, { true }, { selected++ }, { returned++ }, { fail("editor failed") })
                field.setDisposedWith(presentation)
                try {
                    val native = requireNotNull(field.getEditor(true))
                    WriteCommandAction.runWriteCommandAction(project) { field.document.setText("日本語の下書き\nsecond line") }
                    native.caretModel.moveToOffset(5)
                    native.selectionModel.setSelection(1, 5)
                    field.removeNotify()
                    field.addNotify()
                    val textEditor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(native)
                    val undo = com.intellij.openapi.command.undo.UndoManager.getInstance(project)
                    assertTrue(undo.isUndoAvailable(textEditor))
                    val sessionBefore = sessions.snapshot()
                    val first = presentation.createEditor()
                    val file = requireNotNull(first.file)
                    val provider = ChatFileEditorProvider()
                    assertTrue(provider.accept(project, file))
                    assertFalse(provider.accept(com.intellij.openapi.project.ProjectManager.getInstance().defaultProject, file))
                    assertFalse(provider.accept(project, LightVirtualFile("ordinary.kt", "val x = 1")))
                    val second = provider.createEditor(project, file)
                    assertEquals(0, selected, "Constructing a background split must not select its chat")
                    assertTrue(SwingUtilities.isDescendingFrom(content, first.component))
                    second.selectNotify()
                    assertTrue(SwingUtilities.isDescendingFrom(content, second.component))
                    first.selectNotify()
                    assertTrue(SwingUtilities.isDescendingFrom(content, first.component))
                    second.dispose()
                    assertTrue(presentation.inEditor)
                    assertEquals(0, returned)
                    first.dispose()
                    assertFalse(presentation.inEditor)
                    assertEquals(1, returned)
                    assertTrue(SwingUtilities.isDescendingFrom(content, presentation.panel))
                    assertSame(native, field.editor)
                    assertEquals("日本語の下書き\nsecond line", field.text)
                    assertEquals(5, native.caretModel.offset)
                    assertEquals(1, native.selectionModel.selectionStart)
                    assertEquals(5, native.selectionModel.selectionEnd)
                    assertTrue(sessions.accepts(token))
                    assertEquals(sessionBefore, sessions.snapshot())
                    assertTrue(undo.isUndoAvailable(textEditor))
                    undo.undo(textEditor)
                    assertEquals("", field.text)
                    undo.redo(textEditor)
                    assertEquals("日本語の下書き\nsecond line", field.text)
                    assertEquals(AgentTransport.PRINT, sessions.snapshot().selected.transport)
                    assertEquals(2, selected)
                    Disposer.dispose(presentation)
                    com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
                    assertTrue(native.isDisposed)
                    assertFalse(provider.accept(project, file))
                    assertFalse(first.isValid)
                    assertFalse(file.isValid)
                    first.selectNotify()
                    first.dispose()
                    assertEquals(1, returned)
                } finally { if (presentation.alive) Disposer.dispose(presentation) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

    @Test
    fun `native file editor open and close return the live view without treating it as a text document`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("native chat editor").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val field = GrowingPromptField(fixture.project)
                var failures = 0
                val presentation = ChatEditorPresentation(fixture.project, field, field, { true }, {}, {}, { failure -> failures++; if (failure != null) throw AssertionError("native editor open failed", failure) })
                field.setDisposedWith(presentation)
                try {
                    // The SDK's TestEditorManagerImpl intentionally consults this key, not provider EPs.
                    val prepared = presentation.createEditor()
                    val preparedFile = requireNotNull(prepared.file)
                    preparedFile.putUserData(com.intellij.openapi.fileEditor.FileEditorProvider.KEY, ChatFileEditorProvider())
                    prepared.dispose()
                    presentation.toggle()
                    assertEquals(0, failures)
                    assertTrue(presentation.inEditor)
                    presentation.updateTitle("日本語の会話.kt")
                    assertEquals("日本語の会話.kt", preparedFile.name)
                    val manager = FileEditorManager.getInstance(fixture.project)
                    val file = manager.openFiles.single { ChatFileEditorProvider().accept(fixture.project, it) }
                    assertTrue(manager.getAllEditors(file).all { it !is com.intellij.openapi.fileEditor.TextEditor })
                    manager.closeFile(file)
                    assertFalse(presentation.inEditor)
                    assertTrue(SwingUtilities.isDescendingFrom(field, presentation.panel))
                } finally { Disposer.dispose(presentation) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
    @Test
    fun `toggle uses the view data context and rechecks project composition and disposed ownership`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("chat editor action").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val composer = com.cursoragent.ui.composer.ComposerPanel(fixture.project)
                val field = composer.inputArea
                var enabled = true
                var failures = 0
                val presentation = ChatEditorPresentation(fixture.project, composer, field, { enabled && composer.canMovePresentation }, {}, {}, { failures++ })
                field.setDisposedWith(presentation)
                try {
                    val prepared = presentation.createEditor()
                    val file = requireNotNull(prepared.file)
                    file.putUserData(com.intellij.openapi.fileEditor.FileEditorProvider.KEY, ChatFileEditorProvider())
                    prepared.dispose()
                    val context = com.intellij.openapi.actionSystem.impl.Utils.createAsyncDataContext(field)
                    assertSame(presentation, ChatEditorPresentation.KEY.getData(context))
                    val action = com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(ChatEditorToggleAction.ID)
                    fun event(project: com.intellij.openapi.project.Project?) = com.intellij.openapi.actionSystem.AnActionEvent.createEvent(
                        com.intellij.openapi.actionSystem.DataContext { key ->
                            if (com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.`is`(key)) project else context.getData(key)
                        }, null, "test", com.intellij.openapi.actionSystem.ActionUiKind.NONE, null)
                    val wrong = event(null)
                    action.update(wrong)
                    assertFalse(wrong.presentation.isEnabled)
                    action.actionPerformed(wrong)
                    assertFalse(presentation.inEditor)
                    val own = event(fixture.project)
                    enabled = false
                    action.update(own)
                    assertFalse(own.presentation.isEnabled)
                    action.actionPerformed(own)
                    assertFalse(presentation.inEditor)
                    enabled = true
                    val native = requireNotNull(field.getEditor(true))
                    val composing = java.awt.event.InputMethodEvent(native.contentComponent,
                        java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        java.text.AttributedString("変換").iterator, 0, null, null)
                    native.contentComponent.inputMethodListeners.forEach { it.inputMethodTextChanged(composing) }
                    action.update(own)
                    assertFalse(own.presentation.isEnabled)
                    action.actionPerformed(own)
                    assertFalse(presentation.inEditor)
                    val committed = java.awt.event.InputMethodEvent(native.contentComponent,
                        java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        java.text.AttributedString("変換").iterator, 2, null, null)
                    native.contentComponent.inputMethodListeners.forEach { it.inputMethodTextChanged(committed) }
                    com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
                    action.update(own)
                    assertTrue(own.presentation.isEnabled)
                    val keymaps = com.intellij.openapi.keymap.ex.KeymapManagerEx.getInstanceEx()
                    val previous = keymaps.activeKeymap
                    val originalShortcuts = action.shortcutSet.shortcuts.toList()
                    val custom = previous.deriveKeymap("Chat editor test")
                    val shortcut = com.intellij.openapi.actionSystem.KeyboardShortcut(javax.swing.KeyStroke.getKeyStroke("control alt F9"), null)
                    try {
                        custom.removeAllActionShortcuts(ChatEditorToggleAction.ID)
                        custom.addShortcut(ChatEditorToggleAction.ID, shortcut)
                        keymaps.setActiveKeymap(custom)
                        assertEquals(listOf(shortcut), action.shortcutSet.shortcuts.toList())
                        custom.removeAllActionShortcuts(ChatEditorToggleAction.ID)
                        assertTrue(action.shortcutSet.shortcuts.isEmpty())
                    } finally { keymaps.setActiveKeymap(previous) }
                    assertEquals(originalShortcuts, action.shortcutSet.shortcuts.toList())
                    com.intellij.openapi.actionSystem.ex.ActionUtil.performAction(action, own)
                    assertTrue(presentation.inEditor)
                    action.update(own)
                    assertEquals("会話をAgentパネルに戻す", own.presentation.text)
                    com.intellij.openapi.actionSystem.ex.ActionUtil.performAction(action, own)
                    assertFalse(presentation.inEditor)
                    Disposer.dispose(presentation)
                    action.update(own)
                    assertFalse(own.presentation.isEnabled)
                    action.actionPerformed(own)
                    assertFalse(file.isValid)
                    assertEquals(0, failures)
                } finally { if (presentation.alive) Disposer.dispose(presentation) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

}
