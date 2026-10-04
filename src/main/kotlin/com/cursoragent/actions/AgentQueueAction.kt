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
    SUBMIT("CursorAgent.QueueSubmit"),
    RETURN_TO_INPUT("CursorAgent.QueueReturnToInput"),
}

/** Supplied by the focused queue list, or for ReturnToInput alone by the inline edit prompt. */
internal class AgentQueueActions(
    val available: (AgentQueueCommand) -> Boolean,
    val perform: (AgentQueueCommand, AnActionEvent) -> Unit,
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
        if (target.available(command)) target.perform(command, e)
    }

    class Previous : AgentQueueAction(AgentQueueCommand.PREVIOUS)
    class Next : AgentQueueAction(AgentQueueCommand.NEXT)
    class Edit : AgentQueueAction(AgentQueueCommand.EDIT)
    class Remove : AgentQueueAction(AgentQueueCommand.REMOVE)
    class Submit : AgentQueueAction(AgentQueueCommand.SUBMIT) {
        private fun accepts(e: AnActionEvent): Boolean {
            val key = e.inputEvent as? java.awt.event.KeyEvent ?: return true
            return acceptsQueueSubmitShortcut(key, com.cursoragent.settings.AgentSettingsState.getInstance().sendKeyMode,
                com.intellij.openapi.util.SystemInfo.isMac)
        }
        override fun update(e: AnActionEvent) { super.update(e); e.presentation.isEnabled = e.presentation.isEnabled && accepts(e) }
        override fun actionPerformed(e: AnActionEvent) { if (accepts(e)) super.actionPerformed(e) }
    }
    class ReturnToInput : AgentQueueAction(AgentQueueCommand.RETURN_TO_INPUT)
}

/** Both default queue keys mean Send Now until a transport supplies steering; custom remaps remain usable. */
internal fun acceptsQueueSubmitShortcut(key: java.awt.event.KeyEvent, mode: com.cursoragent.settings.SendKeyMode, isMac: Boolean): Boolean {
    if (key.keyCode != java.awt.event.KeyEvent.VK_ENTER) return true
    val primary = if (isMac) java.awt.event.InputEvent.META_DOWN_MASK else java.awt.event.InputEvent.CTRL_DOWN_MASK
    return when (key.modifiersEx) {
        0 -> mode == com.cursoragent.settings.SendKeyMode.ENTER
        primary -> true
        primary or java.awt.event.InputEvent.ALT_DOWN_MASK -> mode == com.cursoragent.settings.SendKeyMode.MODIFIER_ENTER
        else -> true
    }
}
