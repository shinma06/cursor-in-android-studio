package com.cursoragent.ui.composer

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.awt.event.InputMethodEvent
import java.text.AttributedString
import javax.swing.JPanel

class PromptQueueNavigationTest {
    @Test
    fun `native vertical action only leaves a focused empty marked prompt at its caret boundary`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("queue prompt navigation").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val lifetime = Disposer.newDisposable()
                val field = GrowingPromptField(fixture.project)
                field.setDisposedWith(lifetime)
                val real = requireNotNull(field.getEditor(true))
                var focused = true
                val component = object : JPanel() { override fun isFocusOwner() = focused }
                val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Editor::class.java)) { _, method, args ->
                    if (method.name == "getContentComponent") component else method.invoke(real, *(args ?: emptyArray()))
                } as Editor
                var fallbacks = 0
                val original = object : EditorActionHandler() {
                    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) { fallbacks++ }
                }
                val up = PromptQueueNavigation.Up(original)
                val down = PromptQueueNavigation.Down(original)
                val visits = mutableListOf<Boolean>()
                var available = true
                field.onQueueNavigate = { reverse -> if (available) visits.add(reverse) else false }
                fun execute(action: EditorActionHandler) = action.execute(editor, real.caretModel.currentCaret, DataContext.EMPTY_CONTEXT)
                try {
                    execute(up); execute(down)
                    assertEquals(listOf(true, false), visits)
                    assertEquals(0, fallbacks)
                    focused = false
                    execute(up)
                    focused = true
                    available = false // No queue, an IME/mention popup, a draft attachment, or a lost owner.
                    execute(down)
                    available = true
                    assertEquals(2, fallbacks)
                    val ime = real.contentComponent.inputMethodListeners.filterIsInstance<PromptImeGuard>().single()
                    ime.inputMethodTextChanged(InputMethodEvent(real.contentComponent, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        AttributedString("入力中").iterator, 0, null, null))
                    execute(up)
                    assertEquals(3, fallbacks)
                    assertEquals(2, visits.size, "IME must be checked by the actual prompt callback")
                    ime.reset()
                    WriteCommandAction.runWriteCommandAction(fixture.project) { real.document.setText("draft") }
                    real.caretModel.moveToOffset(0)
                    execute(up)
                    real.caretModel.moveToOffset(5)
                    execute(down)
                    assertEquals(5, fallbacks)
                    assertEquals("draft", real.document.text)
                    WriteCommandAction.runWriteCommandAction(fixture.project) { real.document.setText(" \n ") }
                    real.caretModel.moveToOffset(1)
                    execute(up); execute(down)
                    assertEquals(7, fallbacks)
                    real.caretModel.moveToOffset(0)
                    execute(up)
                    real.caretModel.moveToOffset(real.document.textLength)
                    execute(down)
                    assertEquals(listOf(true, false, true, false), visits)
                    real.selectionModel.setSelection(0, real.document.textLength)
                    execute(down)
                    assertEquals(8, fallbacks)
                    real.selectionModel.removeSelection()
                    real.caretModel.moveToOffset(0)
                    assertNotNull(real.caretModel.addCaret(VisualPosition(1, 1)))
                    execute(up)
                    assertEquals(9, fallbacks)
                    assertEquals(" \n ", real.document.text)
                    val ordinary = EditorFactory.getInstance().createEditor(EditorFactory.getInstance().createDocument(""), fixture.project)
                    try {
                        up.execute(ordinary, ordinary.caretModel.currentCaret, DataContext.EMPTY_CONTEXT)
                        assertEquals(10, fallbacks)
                        assertEquals(4, visits.size)
                    } finally { EditorFactory.getInstance().releaseEditor(ordinary) }
                } finally { Disposer.dispose(lifetime) }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }
}
