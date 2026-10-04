package com.cursoragent.actions

import com.cursoragent.ui.AgentToolWindowRootPanel
import com.cursoragent.ui.composer.context.EditorContextReader
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager

internal enum class AgentWindowCommand(val actionId: String) {
    TOGGLE("CursorAgent.TogglePanel"),
    SWAP_SIDE("CursorAgent.SwapPanelSide"),
    OPEN_CHAT("CursorAgent.OpenChat"),
    FOLLOW_UP("CursorAgent.FollowUp"),
    NEW_AGENT("CursorAgent.NewAgent"),
    ALL_CHATS("CursorAgent.AllChats"),
}

/** Resolve the owning project's live ToolWindow for every event, including before content creation. */
abstract class AgentWindowAction internal constructor(
    private val command: AgentWindowCommand,
    private val windowFor: (Project) -> ToolWindow? = { ToolWindowManager.getInstance(it).getToolWindow("Cursor Agent") },
) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = target(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val window = target(e) ?: return
        panel(window)?.cancelPendingChatFocus()
        when (command) {
            AgentWindowCommand.TOGGLE -> if (window.isVisible) window.hide(null) else window.show(null)
            AgentWindowCommand.SWAP_SIDE -> {
                window.setAnchor(if (window.anchor == ToolWindowAnchor.LEFT) ToolWindowAnchor.RIGHT else ToolWindowAnchor.LEFT, null)
                window.show(null)
            }
            AgentWindowCommand.ALL_CHATS -> {
                val origin = com.cursoragent.ui.chatFocusOrigin(window.project)
                window.contentManager.contents.firstNotNullOfOrNull { it.component as? AgentToolWindowRootPanel }
                    ?.toggleAllChats(window, origin)
            }
            AgentWindowCommand.OPEN_CHAT, AgentWindowCommand.FOLLOW_UP, AgentWindowCommand.NEW_AGENT -> {
                // Read the event editor before content creation/activation can move focus.
                val selection = e.getData(CommonDataKeys.EDITOR)?.takeIf { it.selectionModel.hasSelection() }
                    ?.let { EditorContextReader.read(window.project, it)?.selection }
                val origin = com.cursoragent.ui.chatFocusOrigin(window.project)
                window.contentManager.contents.firstNotNullOfOrNull { it.component as? AgentToolWindowRootPanel }
                    ?.enterChat(command, selection, window, origin)
            }
        }
    }

    private fun target(e: AnActionEvent): ToolWindow? {
        val project = e.project?.takeUnless { it.isDisposed } ?: return null
        val window = windowFor(project)?.takeUnless { it.isDisposed || !it.isAvailable || it.project !== project } ?: return null
        return window.takeUnless { panel(window)?.windowShortcutAvailable == false }
    }

    private fun panel(window: ToolWindow) =
        window.contentManagerIfCreated?.contents?.firstNotNullOfOrNull { it.component as? AgentToolWindowRootPanel }

    class Toggle : AgentWindowAction(AgentWindowCommand.TOGGLE)
    class SwapSide : AgentWindowAction(AgentWindowCommand.SWAP_SIDE)
    class OpenChat : AgentWindowAction(AgentWindowCommand.OPEN_CHAT)
    class FollowUp : AgentWindowAction(AgentWindowCommand.FOLLOW_UP)
    class NewAgent : AgentWindowAction(AgentWindowCommand.NEW_AGENT)
    class AllChats : AgentWindowAction(AgentWindowCommand.ALL_CHATS)
}
