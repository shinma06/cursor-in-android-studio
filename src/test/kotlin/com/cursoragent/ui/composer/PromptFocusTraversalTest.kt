package com.cursoragent.ui.composer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class PromptFocusTraversalTest {
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
