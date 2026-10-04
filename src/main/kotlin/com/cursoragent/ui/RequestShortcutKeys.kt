package com.cursoragent.ui

import com.intellij.openapi.Disposable
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/** A held key cannot dispatch the next row/request, or turn a rejection into a subsequent run Stop. */
internal class RequestShortcutKeys : KeyEventDispatcher, Disposable {
    private val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val pressed = mutableSetOf<Int>()

    fun accept(key: KeyEvent?): Boolean {
        if (key == null || key.id != KeyEvent.KEY_PRESSED) return true
        if (!pressed.add(key.keyCode)) return false
        if (pressed.size == 1) manager.addKeyEventDispatcher(this)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.id == KeyEvent.KEY_RELEASED && pressed.remove(event.keyCode) && pressed.isEmpty()) {
            manager.removeKeyEventDispatcher(this)
        }
        return false
    }

    override fun dispose() {
        pressed.clear()
        manager.removeKeyEventDispatcher(this)
    }
}
