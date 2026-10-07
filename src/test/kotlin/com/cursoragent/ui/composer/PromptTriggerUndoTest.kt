package com.cursoragent.ui.composer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.intellij.testFramework.runInEdtAndWait

class PromptTriggerUndoTest {
    @Test
    fun `candidate consumption stops undo at selection but later typing and other documents remain undoable`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("prompt trigger undo").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val project = fixture.project
                val undo = UndoManager.getInstance(project)
                val factory = EditorFactory.getInstance()
                val other = factory.createEditor(factory.createDocument("other"), project)
                val otherFile = TextEditorProvider.getInstance().getTextEditor(other)
                try {
                    WriteCommandAction.runWriteCommandAction(project) { other.document.insertString(5, " change") }
                    for (trigger in listOf('@', '/')) {
                        val field = GrowingPromptField(project)
                        val editor = factory.createEditor(field.document, project)
                        val file: TextEditor = TextEditorProvider.getInstance().getTextEditor(editor)
                        try {
                            WriteCommandAction.runWriteCommandAction(project) { field.document.insertString(0, "日本語 $trigger") }
                            assertTrue(undo.isUndoAvailable(file))
                            consumePromptTrigger(project, field.document, field.document.textLength - 1)
                            assertEquals("日本語 ", field.text)
                            val rejected = assertThrows(RuntimeException::class.java) { undo.undo(file) }
                            assertTrue(rejected.message.orEmpty().contains("cannot be undone"))
                            assertEquals("日本語 ", field.text, "Selection must not restore only the trigger")
                            assertFalse(undo.isRedoAvailable(file))
                            WriteCommandAction.runWriteCommandAction(project) { field.document.insertString(field.document.textLength, "続き") }
                            assertTrue(undo.isUndoAvailable(file))
                            undo.undo(file)
                            assertEquals("日本語 ", field.text)
                            val boundary = assertThrows(RuntimeException::class.java) { undo.undo(file) }
                            assertTrue(boundary.message.orEmpty().contains("cannot be undone"))
                            assertTrue(undo.isRedoAvailable(file))
                            undo.redo(file)
                            assertEquals("日本語 続き", field.text)
                            assertTrue(undo.isUndoAvailable(otherFile), "The barrier must stay document-local")
                        } finally {
                            factory.releaseEditor(editor)
                        }
                    }
                    undo.undo(otherFile)
                    assertEquals("other", other.document.text)
                    undo.redo(otherFile)
                    assertEquals("other change", other.document.text)
                } finally {
                    factory.releaseEditor(other)
                }
            }
        } finally {
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
