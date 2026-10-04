package com.cursoragent.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAwareAction

internal enum class AgentQueueCommand(val actionId: String) {
    PREVIOUS("CursorAgent.QueuePrevious"),
    NEXT("CursorAgent.QueueNext"),
    EDIT("CursorAgent.QueueEdit"),
    REMOVE("CursorAgent.QueueRemove"),
    RETURN_TO_INPUT("CursorAgent.QueueReturnToInput"),
}

/** Supplied by the focused queue list, or for ReturnToInput alone by the inline edit prompt. */
internal class AgentQueueActions(
    val available: (AgentQueueCommand) -> Boolean,
    val perform: (AgentQueueCommand) -> Unit,
) {
    companion object {
        val KEY = DataKey.create<AgentQueueActions>("CursorAgent.QueueActions")
    }
}

abstract class AgentQueueAction internal constructor(private val command: AgentQueueCommand) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(AgentQueueActions.KEY)?.available?.invoke(command) == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val target = e.getData(AgentQueueActions.KEY) ?: return
        if (target.available(command)) target.perform(command)
    }

    class Previous : AgentQueueAction(AgentQueueCommand.PREVIOUS)
    class Next : AgentQueueAction(AgentQueueCommand.NEXT)
    class Edit : AgentQueueAction(AgentQueueCommand.EDIT)
    class Remove : AgentQueueAction(AgentQueueCommand.REMOVE)
    class ReturnToInput : AgentQueueAction(AgentQueueCommand.RETURN_TO_INPUT)
}
