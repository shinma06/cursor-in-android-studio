package com.cursoragent.ui.composer

import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/** Configure each newly created prompt editor, without changing IDE-wide keyboard defaults. */
internal fun installPromptFocusTraversal(component: JComponent) {
    component.focusTraversalKeysEnabled = true
    component.setFocusTraversalKeys(
        KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
        component.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS) +
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
    )
    component.setFocusTraversalKeys(
        KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
        component.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS) +
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
    )
}
