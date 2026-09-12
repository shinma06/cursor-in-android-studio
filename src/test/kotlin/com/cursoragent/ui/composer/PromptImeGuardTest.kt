package com.cursoragent.ui.composer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.event.InputMethodEvent
import java.text.AttributedString
import javax.swing.JTextField
import javax.swing.SwingUtilities

class PromptImeGuardTest {
    @Test
    fun `commit stays guarded for its event turn and an older reset cannot clear new composition`() {
        val guard = PromptImeGuard()
        val field = JTextField()
        fun change(committed: Int) = guard.inputMethodTextChanged(InputMethodEvent(
            field, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, AttributedString("日本").iterator, committed, null, null,
        ))
        SwingUtilities.invokeAndWait {
            assertFalse(guard.isComposing)
            change(2)
            assertTrue(guard.isComposing)
            change(0)
        }
        SwingUtilities.invokeAndWait {
            assertTrue(guard.isComposing)
            change(2)
            assertTrue(guard.isComposing)
        }
        SwingUtilities.invokeAndWait { assertFalse(guard.isComposing) }
    }
    @Test
    fun `new editor resets abandoned composition and invalidates old callbacks`() {
        val guard = PromptImeGuard()
        val field = JTextField()
        fun change(committed: Int) = guard.inputMethodTextChanged(InputMethodEvent(
            field, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, AttributedString("日本").iterator, committed, null, null,
        ))
        SwingUtilities.invokeAndWait {
            change(2)
            guard.reset()
            assertFalse(guard.isComposing)
            change(0)
        }
        SwingUtilities.invokeAndWait { assertTrue(guard.isComposing) }
    }
}
