package com.cursoragent.ui.composer

import com.cursoragent.ui.unusedActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class PromptFocusTraversalTest {
    @Test
    fun `IDE shortcuts traverse without editing selections and do not steal popup focus`() = SwingUtilities.invokeAndWait {
        val ordinaryEditor = JTextArea("ordinary editor")
        val ordinaryTab = ordinaryEditor.inputMap.get(KeyStroke.getKeyStroke("TAB"))
        // Each field models a fresh editor, including recreation after send/enable.
        for (text in listOf("", "日本語", "日本語\nsecond line")) {
            val moved = mutableListOf<String>()
            var focused = true
            val prompt = object : JTextArea(text) {
                override fun isFocusOwner() = focused
                override fun transferFocus() { moved += "next" }
                override fun transferFocusBackward() { moved += "previous" }
            }
            installPromptFocusTraversal(prompt)
            val actions = ActionUtil.getActions(prompt)
            assertEquals(2, actions.size)
            for (selected in listOf(false, true)) {
                prompt.select(0, if (selected) text.length else 0)
                val selection = prompt.selectionStart to prompt.selectionEnd
                for ((index, action) in actions.withIndex()) {
                    val shortcut = action.shortcutSet.shortcuts.single() as KeyboardShortcut
                    assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, if (index == 1) InputEvent.SHIFT_DOWN_MASK else 0), shortcut.firstKeyStroke)
                    assertNull(shortcut.secondKeyStroke)
                    assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
                    val event = AnActionEvent(
                        DataContext.EMPTY_CONTEXT, action.templatePresentation.clone(), "test",
                        ActionUiKind.NONE, null, 0, unusedActionManager,
                    )
                    focused = true
                    prompt.isEnabled = true
                    action.update(event)
                    assertTrue(event.presentation.isEnabled)
                    action.actionPerformed(event)
                    assertEquals(if (index == 1) "previous" else "next", moved.last())
                    assertEquals(text, prompt.text)
                    assertEquals(selection, prompt.selectionStart to prompt.selectionEnd)

                    val count = moved.size
                    focused = false // Mention popup, another tab, or the regular editor.
                    action.actionPerformed(event) // Stale enabled presentation must also be safe.
                    action.update(event)
                    assertFalse(event.presentation.isEnabled)
                    focused = true
                    prompt.isEnabled = false
                    action.update(event)
                    assertFalse(event.presentation.isEnabled)
                    action.actionPerformed(event)
                    assertEquals(count, moved.size)
                }
            }
        }
        assertTrue(ActionUtil.getActions(ordinaryEditor).isEmpty())
        assertEquals(ordinaryTab, ordinaryEditor.inputMap.get(KeyStroke.getKeyStroke("TAB")))
    }

    @Test
    fun `Tab and Shift Tab are consumed for traversal without changing prompt text`() = SwingUtilities.invokeAndWait {
        val moved = mutableListOf<String>()
        val manager = object : DefaultKeyboardFocusManager() {
            override fun focusNextComponent(component: Component) { moved += "next" }
            override fun focusPreviousComponent(component: Component) { moved += "previous" }
        }
        val original = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(manager)
        try {
            val prompt = JTextArea("日本語\n\t貼り付けたタブ").apply {
                focusTraversalKeysEnabled = false
                installPromptFocusTraversal(this)
            }
            for (modifiers in listOf(0, InputEvent.SHIFT_DOWN_MASK)) {
                val pressed = KeyEvent(prompt, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_TAB, '\t')
                manager.processKeyEvent(prompt, pressed)
                assertTrue(pressed.isConsumed)
                val released = KeyEvent(prompt, KeyEvent.KEY_RELEASED, 0, modifiers, KeyEvent.VK_TAB, '\t')
                manager.processKeyEvent(prompt, released)
                assertTrue(released.isConsumed)
            }
            assertEquals(listOf("next", "previous"), moved)
            assertEquals("日本語\n\t貼り付けたタブ", prompt.text)
            val enter = KeyEvent(prompt, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n')
            manager.processKeyEvent(prompt, enter)
            assertFalse(enter.isConsumed)
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(original)
        }
    }

}
