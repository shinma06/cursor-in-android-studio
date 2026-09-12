package com.cursoragent.ui.composer

import com.cursoragent.settings.SendKeyMode
import com.intellij.openapi.actionSystem.KeyboardShortcut
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class PromptSendShortcutTest {
    @Test
    fun `switching send keys preserves per tab multiline drafts caret and native input actions`() = SwingUtilities.invokeAndWait {
        val fields = listOf(JTextArea("日本語\nsecond line"), JTextArea("別の下書き"))
        val actions = fields.map { PromptSendShortcut {} }
        fields.forEach { it.caretPosition = 2 }
        val documents = fields.map { it.document }
        val nativeEnter = fields.map { it.inputMap.get(KeyStroke.getKeyStroke("ENTER")) }
        val nativeShiftEnter = fields.map { it.inputMap.get(KeyStroke.getKeyStroke("shift ENTER")) }
        val originals = fields.map { it.text }
        for (isMac in listOf(true, false)) {
            for (mode in listOf(SendKeyMode.ENTER, SendKeyMode.MODIFIER_ENTER, SendKeyMode.ENTER)) {
                fields.forEachIndexed { i, field ->
                    actions[i].install(field, mode, isMac)
                    val expected = when (mode) {
                        SendKeyMode.ENTER -> 0
                        SendKeyMode.MODIFIER_ENTER -> if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
                    }
                    val shortcut = actions[i].shortcutSet.shortcuts.single() as KeyboardShortcut
                    assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, expected), shortcut.firstKeyStroke)
                    assertNull(shortcut.secondKeyStroke)
                    assertSame(documents[i], field.document)
                    assertEquals(originals[i], field.text)
                    assertEquals(2, field.caretPosition)
                    assertEquals(nativeEnter[i], field.inputMap.get(KeyStroke.getKeyStroke("ENTER")))
                    assertEquals(nativeShiftEnter[i], field.inputMap.get(KeyStroke.getKeyStroke("shift ENTER")))
                }
            }
        }
        assertEquals("Cmd+Enter", SendKeyMode.MODIFIER_ENTER.keyLabel(true))
        assertEquals("Ctrl+Enter", SendKeyMode.MODIFIER_ENTER.keyLabel(false))
    }
}
