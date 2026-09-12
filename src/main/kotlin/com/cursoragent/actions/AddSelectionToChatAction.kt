package com.cursoragent.actions

import com.cursoragent.ui.AgentToolWindowRootPanel
import com.cursoragent.ui.composer.context.EditorContextReader
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

/** Capture editor text before activating the chat; an existing target is bound before focus changes. */
class AddSelectionToChatAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selection = EditorContextReader.read(project, editor)?.selection ?: return
        val window = ToolWindowManager.getInstance(project).getToolWindow("Cursor Agent") ?: return
        fun panel() = window.contentManager.contents.map { it.component }.filterIsInstance<AgentToolWindowRootPanel>().firstOrNull()
        val existing = panel()?.selectionContextTarget()
        if (existing != null) {
            existing(selection)
            window.activate(null)
        } else {
            window.activate { if (!project.isDisposed && !window.isDisposed) panel()?.selectionContextTarget()?.invoke(selection) }
        }
    }
}
