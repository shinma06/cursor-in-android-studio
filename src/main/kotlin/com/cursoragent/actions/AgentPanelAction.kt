package com.cursoragent.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.project.DumbAwareAction
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/** A panel supplies live availability and dispatch; application actions never retain a project. */
internal class AgentPanelActions(
    val available: (AgentPanelCommand) -> Boolean,
    val perform: (AgentPanelCommand, AnActionEvent) -> Unit,
) {
    companion object {
        val KEY = DataKey.create<AgentPanelActions>("CursorAgent.PanelActions")
    }
}

internal enum class AgentPanelCommand(val actionId: String) {
    NEW_CHAT("CursorAgent.NewChat"),
    RESET_CHAT("CursorAgent.ResetChat"),
    SUBMIT_INITIAL("CursorAgent.SubmitInitialChat"),
    UNFOCUS_INPUT("CursorAgent.UnfocusInput"),
    CLOSE_CHAT("CursorAgent.CloseChat"),
    PREVIOUS_CHAT("CursorAgent.PreviousChat"),
    NEXT_CHAT("CursorAgent.NextChat"),
    PREVIOUS_AGENT("CursorAgent.PreviousAgent"),
    NEXT_AGENT("CursorAgent.NextAgent"),
    RECENT_CHAT("CursorAgent.RecentChat"),
    LEAST_RECENT_CHAT("CursorAgent.LeastRecentChat"),
    STOP("CursorAgent.Stop"),
    ACCEPT_PENDING("CursorAgent.AcceptPending"),
    APPROVE_TOOL("CursorAgent.ApproveTool"),
    SKIP_TOOL("CursorAgent.SkipTool"),
    MODE_MENU("CursorAgent.ModeMenu"),
    MODEL_MENU("CursorAgent.ModelMenu"),
    ADD_CONTEXT("CursorAgent.AddContext"),
    CHANGES("CursorAgent.Changes"),
}

/** Registered in plugin.xml so Keymap changes are also used by panel-local shortcuts. */
abstract class AgentPanelAction internal constructor(private val command: AgentPanelCommand) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(AgentPanelActions.KEY)?.available?.invoke(command) == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val target = e.getData(AgentPanelActions.KEY) ?: return
        if (target.available(command)) target.perform(command, e)
    }

    class NewChat : AgentPanelAction(AgentPanelCommand.NEW_CHAT)
    class ResetChat : AgentPanelAction(AgentPanelCommand.RESET_CHAT)
    class SubmitInitialChat : AgentPanelAction(AgentPanelCommand.SUBMIT_INITIAL)
    class UnfocusInput : AgentPanelAction(AgentPanelCommand.UNFOCUS_INPUT) {
        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = e.presentation.isEnabled && !toolReviewOwnsShortcut(e, AgentPanelCommand.SKIP_TOOL)
        }
        override fun actionPerformed(e: AnActionEvent) {
            if (!toolReviewOwnsShortcut(e, AgentPanelCommand.SKIP_TOOL)) super.actionPerformed(e)
        }
    }
    class CloseChat : AgentPanelAction(AgentPanelCommand.CLOSE_CHAT)
    class PreviousChat : AgentPanelAction(AgentPanelCommand.PREVIOUS_CHAT)
    class NextChat : AgentPanelAction(AgentPanelCommand.NEXT_CHAT)
    class PreviousAgent : AgentPanelAction(AgentPanelCommand.PREVIOUS_AGENT)
    class NextAgent : AgentPanelAction(AgentPanelCommand.NEXT_AGENT)
    class RecentChat : AgentPanelAction(AgentPanelCommand.RECENT_CHAT)
    class LeastRecentChat : AgentPanelAction(AgentPanelCommand.LEAST_RECENT_CHAT)
    class Stop : AgentPanelAction(AgentPanelCommand.STOP)
    class ApproveTool : AgentPanelAction(AgentPanelCommand.APPROVE_TOOL)
    class SkipTool : AgentPanelAction(AgentPanelCommand.SKIP_TOOL)
    class AcceptPending : AgentPanelAction(AgentPanelCommand.ACCEPT_PENDING) {
        private fun accepts(e: AnActionEvent): Boolean {
            val key = e.inputEvent as? java.awt.event.KeyEvent ?: return true
            return acceptsPendingShortcut(key, com.cursoragent.settings.AgentSettingsState.getInstance().sendKeyMode,
                com.intellij.openapi.util.SystemInfo.isMac)
        }
        override fun update(e: AnActionEvent) { super.update(e); e.presentation.isEnabled = e.presentation.isEnabled && accepts(e) }
        override fun actionPerformed(e: AnActionEvent) { if (accepts(e)) super.actionPerformed(e) }
    }
    class ModeMenu : AgentPanelAction(AgentPanelCommand.MODE_MENU)
    class ModelMenu : AgentPanelAction(AgentPanelCommand.MODEL_MENU)
    class AddContext : AgentPanelAction(AgentPanelCommand.ADD_CONTEXT)
    class Changes : AgentPanelAction(AgentPanelCommand.CHANGES)
}

/** Only a live review and its current Keymap can take a key from send or focus return. */
internal fun toolReviewOwnsShortcut(e: AnActionEvent, command: AgentPanelCommand): Boolean {
    val key = e.inputEvent as? KeyEvent ?: return false
    val action = e.actionManager.getAction(command.actionId) ?: return false
    val stroke = KeyStroke.getKeyStrokeForEvent(key)
    return action.shortcutSet.shortcuts.filterIsInstance<KeyboardShortcut>().any {
        it.firstKeyStroke == stroke
    } && e.getData(AgentPanelActions.KEY)?.available?.invoke(command) == true
}

/** The default acceptance key changes with send mode; unrelated user remaps remain usable. */
internal fun acceptsPendingShortcut(key: java.awt.event.KeyEvent, mode: com.cursoragent.settings.SendKeyMode, isMac: Boolean): Boolean {
    if (key.keyCode != java.awt.event.KeyEvent.VK_ENTER) return true
    val primary = if (isMac) java.awt.event.InputEvent.META_DOWN_MASK else java.awt.event.InputEvent.CTRL_DOWN_MASK
    return when (key.modifiersEx) {
        primary -> mode == com.cursoragent.settings.SendKeyMode.ENTER
        primary or java.awt.event.InputEvent.ALT_DOWN_MASK -> mode == com.cursoragent.settings.SendKeyMode.MODIFIER_ENTER
        else -> true
    }
}
