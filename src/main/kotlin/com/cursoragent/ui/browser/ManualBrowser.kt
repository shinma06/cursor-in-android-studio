package com.cursoragent.ui.browser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

/** Native ToolWindow ownership releases content on close, project close and plugin unload. */
class ManualBrowser : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = false

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "ブラウザー"
        toolWindow.title = "ブラウザー"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        addBrowser(project, toolWindow)
    }

    companion object {
        private const val ID = "Cursor Manual Browser"

        /** Menu entry point; never reuses another project's browser. */
        fun open(project: Project) {
            ApplicationManager.getApplication().assertIsDispatchThread()
            if (project.isDisposed) return
            val window = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
            if (window.isDisposed) return
            window.isAvailable = true
            if (window.contentManager.contentCount == 0) addBrowser(project, window)
            window.activate(null)
        }

        private fun addBrowser(project: Project, window: ToolWindow) {
            val panel = ManualBrowserPanel(project)
            val content = ContentFactory.getInstance().createContent(panel, "Browser", false)
            content.setDisposer(panel)
            content.preferredFocusableComponent = panel.preferredFocusableComponent
            window.contentManager.addContent(content)
        }
    }
}
