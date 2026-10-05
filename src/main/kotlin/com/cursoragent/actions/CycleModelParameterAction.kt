package com.cursoragent.actions

import com.cursoragent.ui.composer.ComposerPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/** Native Keymap action, restricted to the focused conversation composer. */
class CycleModelParameterAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    private fun composer(event: AnActionEvent): ComposerPanel? {
        val source = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
        val composer = (source as? ComposerPanel) ?:
            SwingUtilities.getAncestorOfClass(ComposerPanel::class.java, source) as? ComposerPanel ?: return null
        val editor = composer.inputArea.editor ?: return null
        return composer.takeIf {
            event.project?.isDisposed == false && editor.project === event.project &&
                event.getData(AgentPanelActions.KEY)?.available?.invoke(AgentPanelCommand.MODEL_MENU) == true &&
                it.isShowing && editor.contentComponent.isFocusOwner &&
                it.canConfigureModel && it.modelSelector.isEnabled &&
                it.modelSelector.acpConfiguration?.parameters?.any { parameter -> parameter.options.size > 1 } == true
        }
    }

    override fun update(e: AnActionEvent) { e.presentation.isEnabled = composer(e) != null }

    internal fun ownsShortcut(e: AnActionEvent): Boolean {
        val key = e.inputEvent as? KeyEvent ?: return false
        return shortcutSet.shortcuts.filterIsInstance<com.intellij.openapi.actionSystem.KeyboardShortcut>().any {
            it.firstKeyStroke == javax.swing.KeyStroke.getKeyStrokeForEvent(key)
        } && composer(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val composer = composer(e) ?: return
        if (e.inputEvent is KeyEvent) {
            if (composer.inputArea.modelParameterKeyHeld) return
            composer.inputArea.modelParameterKeyHeld = true
        }
        composer.modelSelector.cycleParameter()
    }
}
