package com.cursoragent.ui.composer

import com.cursoragent.actions.AgentPanelActions
import com.cursoragent.actions.AgentPanelCommand
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
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
internal fun installPromptFocusTraversal(component: JComponent, modeAction: AnAction? = null) {
    // The native editor owns these local registrations; no global listener retains it.
    modeAction?.registerCustomShortcutSet(modeAction.shortcutSet, component)
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
    // IDE actions run before Swing traversal. Keep the mode action at the same
    // component so its current Keymap can win without re-enabling selection indentation.
    fun modeOwnsShiftTab(e: AnActionEvent): Boolean =
        modeAction?.shortcutSet?.shortcuts?.filterIsInstance<KeyboardShortcut>()?.any {
            it.firstKeyStroke == KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
        } == true && e.getData(AgentPanelActions.KEY)?.available?.invoke(AgentPanelCommand.MODE_MENU) == true
    for (backwards in listOf(false, true)) {
        object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = component.isEnabled && component.isFocusOwner && !(backwards && modeOwnsShiftTab(e))
            }

            override fun actionPerformed(e: AnActionEvent) {
                // A popup or another editor may have taken focus since update().
                if (!component.isEnabled || !component.isFocusOwner || backwards && modeOwnsShiftTab(e)) return
                if (backwards) component.transferFocusBackward() else component.transferFocus()
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, if (backwards) InputEvent.SHIFT_DOWN_MASK else 0)),
            component,
        )
    }
}
