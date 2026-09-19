package com.cursoragent.ui.composer

import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import javax.swing.SwingUtilities

/** Includes the commit event's EDT turn; an older reset cannot clear a newer composition. */
internal class PromptImeGuard : InputMethodListener {
    var isComposing = false
        private set
    private var revision = 0L

    override fun inputMethodTextChanged(event: InputMethodEvent) {
        val current = ++revision
        isComposing = true
        val pending = event.text?.let { it.endIndex - it.beginIndex > event.committedCharacterCount } == true
        if (!pending) SwingUtilities.invokeLater { if (current == revision) isComposing = false }
    }

    fun reset() {
        revision++
        isComposing = false
    }

    override fun caretPositionChanged(event: InputMethodEvent) = Unit
}
