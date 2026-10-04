package com.cursoragent.actions

import com.cursoragent.ui.AgentToolWindowRootPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager

internal enum class AgentWindowCommand(val actionId: String) {
    TOGGLE("CursorAgent.TogglePanel"),
    SWAP_SIDE("CursorAgent.SwapPanelSide"),
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
        when (command) {
            AgentWindowCommand.TOGGLE -> if (window.isVisible) window.hide(null) else window.show(null)
            AgentWindowCommand.SWAP_SIDE -> {
                window.setAnchor(if (window.anchor == ToolWindowAnchor.LEFT) ToolWindowAnchor.RIGHT else ToolWindowAnchor.LEFT, null)
                window.show(null)
            }
        }
    }

    private fun target(e: AnActionEvent): ToolWindow? {
        val project = e.project?.takeUnless { it.isDisposed } ?: return null
        val window = windowFor(project)?.takeUnless { it.isDisposed || !it.isAvailable || it.project !== project } ?: return null
        val panel = window.contentManagerIfCreated?.contents?.firstNotNullOfOrNull { it.component as? AgentToolWindowRootPanel }
        return window.takeUnless { panel?.windowShortcutAvailable == false }
    }

    class Toggle : AgentWindowAction(AgentWindowCommand.TOGGLE)
    class SwapSide : AgentWindowAction(AgentWindowCommand.SWAP_SIDE)
}
