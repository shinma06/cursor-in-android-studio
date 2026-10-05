package com.cursoragent.ui

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import java.awt.AWTEvent
import java.awt.event.KeyEvent

/** A held key cannot dispatch the next row/request, or turn a rejection into a subsequent run Stop. */
internal class RequestShortcutKeys : IdeEventQueue.NonLockedEventDispatcher, Disposable {
    private val pressed = mutableSetOf<Int>()
    private var consumeTyped = false

    fun accept(key: KeyEvent?): Boolean {
        if (key == null || key.id != KeyEvent.KEY_PRESSED) return true
        if (!pressed.add(key.keyCode)) return false
        consumeTyped = true
        // IDE actions run before KeyboardFocusManager dispatchers. Intercept repeats before both.
        if (pressed.size == 1) IdeEventQueue.getInstance().addDispatcher(this, null)
        return true
    }

    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent) return false
        return when (e.id) {
            KeyEvent.KEY_PRESSED -> (e.keyCode in pressed).also { consumeTyped = it }
            KeyEvent.KEY_TYPED -> consumeTyped
            KeyEvent.KEY_RELEASED -> {
                consumeTyped = false
                if (pressed.remove(e.keyCode) && pressed.isEmpty()) {
                    IdeEventQueue.getInstance().removeDispatcher(this)
                }
                false
            }
            else -> false
        }
    }

    override fun dispose() {
        if (pressed.isNotEmpty()) IdeEventQueue.getInstance().removeDispatcher(this)
        pressed.clear()
        consumeTyped = false
    }
}
