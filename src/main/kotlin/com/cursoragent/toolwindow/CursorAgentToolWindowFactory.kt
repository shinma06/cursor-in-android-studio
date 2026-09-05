package com.cursoragent.toolwindow

import com.cursoragent.PluginBrand
import com.cursoragent.service.AgentProcessService
import com.cursoragent.ui.AgentToolWindowRootPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CursorAgentToolWindowFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = PluginBrand.NAME
        toolWindow.title = PluginBrand.NAME
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AgentToolWindowRootPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        Disposer.register(content, Disposable {
            project.getService(AgentProcessService::class.java).killActiveProcess()
        })
        toolWindow.contentManager.addContent(content)
    }
}
