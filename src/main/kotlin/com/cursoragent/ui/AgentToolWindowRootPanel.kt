package com.cursoragent.ui

import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.header.HeaderOptionsPopup
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

class AgentToolWindowRootPanel(project: Project) : JPanel(BorderLayout()) {
    init {
        border = JBUI.Borders.empty()
        isOpaque = true
        background = AgentUiColors.panelBackground

        val timeline = ChatTimelinePanel()
        val composer = ComposerPanel(project)
        val header = AgentHeaderBar()
        val controller = AgentUiController(project, timeline, composer, header)

        composer.onSend = { text -> controller.sendPrompt(text) }
        composer.onStop = { controller.stopRun() }
        val options = HeaderOptionsPopup(project, header.optionsButton) { controller.sendPrompt("/summarize") }
        composer.onRunningChanged = options::setRunning
        header.onNewChat = { controller.startNewChat() }

        add(header, BorderLayout.NORTH)
        add(timeline, BorderLayout.CENTER)
        add(composer, BorderLayout.SOUTH)
    }
}
