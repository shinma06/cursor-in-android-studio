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
    override fun actionPerformed(e: AnActionEvent) = submit()

    fun install(component: JComponent, mode: SendKeyMode, isMac: Boolean) {
        unregisterCustomShortcutSet(component)
        val modifiers = when (mode) {
            SendKeyMode.ENTER -> 0
            SendKeyMode.MODIFIER_ENTER -> if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        }
        registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifiers)), component)
    }
}
