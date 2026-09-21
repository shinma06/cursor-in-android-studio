package com.cursoragent.ui.composer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.DumbAwareAction
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
    // IDE selection-indent actions run before Swing traversal and ignore the
    // embedded-editor flag. Local shortcuts take precedence over those actions.
    for (backwards in listOf(false, true)) {
        object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = component.isEnabled && component.isFocusOwner
            }

            override fun actionPerformed(e: AnActionEvent) {
                // A popup or another editor may have taken focus since update().
                if (!component.isEnabled || !component.isFocusOwner) return
                if (backwards) component.transferFocusBackward() else component.transferFocus()
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, if (backwards) InputEvent.SHIFT_DOWN_MASK else 0)),
            component,
        )
    }
}
