package com.cursoragent.ui.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** The current view supplies its owner; a code editor or another project cannot toggle a chat. */
class ChatEditorToggleAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    private fun target(event: AnActionEvent) = event.getData(ChatEditorPresentation.KEY)?.takeIf {
        it.project === event.project && it.available
    }
    override fun update(e: AnActionEvent) {
        val target = target(e)
        e.presentation.isEnabled = target != null
        e.presentation.text = if (target?.inEditor == true) "会話をAgentパネルに戻す" else "会話をエディターで開く"
    }
    override fun actionPerformed(e: AnActionEvent) { target(e)?.toggle() }
    companion object { const val ID = "CursorAgent.ToggleChatEditor" }
}
