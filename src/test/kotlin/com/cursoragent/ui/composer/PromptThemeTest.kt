package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.JBColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import javax.swing.UIManager

class PromptThemeTest {
    @Test
    fun `UI refresh preserves composer background and the existing draft editor across themes`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("prompt theme").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val disposable = Disposer.newDisposable()
                val oldTextFieldBackground = UIManager.get("TextField.background")
                val wasDark = !JBColor.isBright()
                try {
                    JBColor.setDark(false)
                    val field = GrowingPromptField(fixture.project)
                    field.setDisposedWith(disposable)
                    val editor = requireNotNull(field.getEditor(true))
                    assertEquals(AgentUiColors.composerBackground.rgb, editor.backgroundColor.rgb)
                    WriteCommandAction.runWriteCommandAction(fixture.project) {
                        field.document.setText("日本語の下書き\nsecond line")
                    }
                    editor.caretModel.moveToOffset(5)
                    editor.selectionModel.setSelection(1, 5)
                    for (dark in listOf(true, false, true, false)) {
                        JBColor.setDark(dark)
                        // The surrounding composer deliberately differs from the IDE's generic text field.
                        UIManager.put("TextField.background", if (dark) Color(0x454545) else Color(0xEEEEEE))
                        field.updateUI()
                        assertEquals(AgentUiColors.composerBackground.rgb, field.background.rgb)
                        assertEquals(AgentUiColors.composerBackground.rgb, editor.backgroundColor.rgb)
                        assertSame(editor, field.editor)
                        assertEquals("日本語の下書き\nsecond line", field.text)
                        assertEquals(5, editor.caretModel.offset)
                        assertEquals(1, editor.selectionModel.selectionStart)
                        assertEquals(5, editor.selectionModel.selectionEnd)
                    }
                } finally {
                    UIManager.put("TextField.background", oldTextFieldBackground)
                    JBColor.setDark(wasDark)
                    Disposer.dispose(disposable)
                }
            }
        } finally {
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
