package com.cursoragent.ui.composer

import com.cursoragent.settings.SendKeyMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/** Replace only our send action. Native editor newline, paste and caret handling stay intact. */
internal class PromptSendShortcut(private val submit: () -> Unit) : AnAction() {
    private var enterPressed = false

    override fun actionPerformed(e: AnActionEvent) {
        val key = e.inputEvent as? KeyEvent
        if (key?.id == KeyEvent.KEY_PRESSED && key.keyCode == KeyEvent.VK_ENTER) {
            if (enterPressed) return
            enterPressed = true
        }
        submit()
    }

    fun release() { enterPressed = false }

    fun install(component: JComponent, mode: SendKeyMode, isMac: Boolean) {
        release()
        unregisterCustomShortcutSet(component)
        val modifiers = when (mode) {
            SendKeyMode.ENTER -> 0
            SendKeyMode.MODIFIER_ENTER -> if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        }
        registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifiers)), component)
    }
}
