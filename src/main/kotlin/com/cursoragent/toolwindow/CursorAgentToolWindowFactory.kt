package com.cursoragent.toolwindow

import com.cursoragent.PluginBrand
import com.cursoragent.ui.AgentToolWindowRootPanel
import com.cursoragent.ui.header.ChatOptionsActionGroup
import com.cursoragent.ui.header.compactHeaderIcon
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
            val more = ChatOptionsActionGroup(toolWindow.createPopupGroup(true))
            val hideIcon = IconLoader.getIcon("/icons/hide-agent-panel.svg", javaClass)
            val hide = object : DumbAwareAction(
                "Agentパネルを隠す", "会話と実行を保持したまま、パネルを隠します。",
                compactHeaderIcon(hideIcon, panel),
            ) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun update(e: AnActionEvent) {
                    e.presentation.icon = compactHeaderIcon(hideIcon, panel)
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
                // Native ActionButton adds 2px on each side: 4 × (22 + 4) + 4 = 108px.
                setMinimumButtonSize(JBUI.size(22, 34))
                component.border = JBUI.Borders.emptyRight(4)
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
