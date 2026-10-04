package com.cursoragent.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAwareAction

internal enum class ConversationFindCommand(val actionId: String) {
    OPEN("CursorAgent.FindConversation"),
    NEXT("CursorAgent.FindConversationNext"),
    PREVIOUS("CursorAgent.FindConversationPrevious"),
    CLOSE("CursorAgent.FindConversationClose"),
}

internal class ConversationFindTarget(
    val available: (ConversationFindCommand) -> Boolean,
    val perform: (ConversationFindCommand) -> Unit,
) {
    companion object {
        val KEY = DataKey.create<ConversationFindTarget>("CursorAgent.ConversationFind")
    }
}

/** The DataContext selects the live panel. An application Action never retains a tab or project. */
abstract class ConversationFindAction internal constructor(private val command: ConversationFindCommand) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(ConversationFindTarget.KEY)?.available?.invoke(command) == true
    }
    override fun actionPerformed(e: AnActionEvent) {
        val target = e.getData(ConversationFindTarget.KEY) ?: return
        if (target.available(command)) target.perform(command)
    }
    class Open : ConversationFindAction(ConversationFindCommand.OPEN)
    class Next : ConversationFindAction(ConversationFindCommand.NEXT)
    class Previous : ConversationFindAction(ConversationFindCommand.PREVIOUS)
    class Close : ConversationFindAction(ConversationFindCommand.CLOSE)
}
