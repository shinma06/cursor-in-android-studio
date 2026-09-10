package com.cursoragent.toolwindow

import com.cursoragent.PluginBrand
import com.cursoragent.ui.AgentToolWindowRootPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI

class CursorAgentToolWindowFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = PluginBrand.NAME
        toolWindow.title = PluginBrand.NAME
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AgentToolWindowRootPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.setAdditionalGearActions(panel.actions.gearActions)
        // Keep SDK-specific decoration access here; other ToolWindow implementations retain the native header.
        val decorator = (toolWindow as? ToolWindowImpl)?.decorator
        val headerWasVisible = decorator?.isHeaderVisible
        if (toolWindow is ToolWindowImpl) {
            val more = toolWindow.createPopupGroup(true).apply {
                isPopup = true
                templatePresentation.apply {
                    text = "その他の操作"
                    icon = AllIcons.Actions.MoreHorizontal
                    putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
                }
            }
            val hide = object : DumbAwareAction(
                "Agentパネルを隠す", "会話と実行を保持したまま、パネルを隠します。",
                IconLoader.getIcon("/icons/hide-agent-panel.svg", javaClass),
            ) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !toolWindow.isDisposed
                }
                override fun actionPerformed(e: AnActionEvent) {
                    if (!toolWindow.isDisposed) toolWindow.hide(null)
                }
            }
            val toolbar = ActionManager.getInstance().createActionToolbar(
                "CursorAgent.SessionHeader", DefaultActionGroup(panel.actions.titleActions + listOf(more, hide)), true,
            ).apply {
                targetComponent = panel
                layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
                setMinimumButtonSize(JBUI.size(30, 34))
                component.border = JBUI.Borders.empty()
                component.isOpaque = false
            }
            panel.installHeaderToolbar(toolbar.component)
            decorator?.isHeaderVisible = false
        } else {
            toolWindow.setTitleActions(panel.actions.titleActions)
        }
        Disposer.register(content, Disposable {
            panel.dispose()
            if (!toolWindow.isDisposed) {
                toolWindow.setTitleActions(emptyList())
                toolWindow.setAdditionalGearActions(null)
                if (headerWasVisible != null) decorator.isHeaderVisible = headerWasVisible
            }
        })
        toolWindow.contentManager.addContent(content)
    }
}
