package com.cursoragent.ui

import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.header.HeaderOptionsPopup
import com.cursoragent.ui.session.SessionTabPresentation
import com.cursoragent.ui.session.SessionTabStrip
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel

/** Retain complete tab views so editor caret/selection and timeline scroll never cross sessions. */
class AgentToolWindowRootPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val sessions = SessionTabs()
    private val strip = SessionTabStrip()
    private val cards = JPanel(CardLayout())
    private data class TabView(val panel: JPanel, val composer: ComposerPanel, val timeline: ChatTimelinePanel, val controller: AgentUiController)
    private val views = mutableMapOf<String, TabView>()
    private var disposed = false

    init {
        border = JBUI.Borders.empty()
        isOpaque = true
        background = AgentUiColors.panelBackground
        strip.onSelect = { id -> if (sessions.select(id)) showSelected() }
        strip.onClose = { id ->
            sessions.close(id)?.let {
                views.remove(id)?.let { view ->
                    view.controller.dispose()
                    cards.remove(view.panel)
                }
                showSelected()
            }
        }
        strip.onMove = { id, index -> if (sessions.move(id, index)) refreshStrip() }
        add(strip, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        showSelected()
    }

    private fun open(chatId: String? = null) {
        if (disposed) return
        sessions.open(chatId)
        showSelected()
    }

    private fun showSelected() {
        if (disposed) return
        val tab = sessions.snapshot().selected
        val view = views.getOrPut(tab.id) {
            val timeline = ChatTimelinePanel()
            val composer = ComposerPanel(project)
            val header = AgentHeaderBar()
            val controller = AgentUiController(project, timeline, composer, header, sessions, tab.id)
            composer.onSend = controller::sendPrompt
            composer.onStop = controller::stopRun
            val options = HeaderOptionsPopup(project, header.optionsButton, controller::transportState, controller::selectTransport) { controller.sendPrompt("/summarize") }
            composer.onRunningChanged = options::setRunning
            header.onNewChat = { open() }
            val history = PastChatsCoordinator(project, header, ChatHistoryState.getInstance(project), ::open)
            header.onPastChatsClicked = history::showPopup
            if (tab.chatId != null) {
                timeline.showStatus("過去の会話本文は保存されていません。次の送信からこのセッションを再開します。")
            }
            val panel = JPanel(BorderLayout()).apply {
                add(header, BorderLayout.NORTH)
                add(timeline, BorderLayout.CENTER)
                add(composer, BorderLayout.SOUTH)
            }
            cards.add(panel, tab.id)
            TabView(panel, composer, timeline, controller)
        }
        views.forEach { (id, other) -> other.timeline.isActiveTab = id == tab.id }
        (cards.layout as CardLayout).show(cards, tab.id)
        refreshStrip()
        view.composer.inputArea.requestFocusInWindow()
    }

    private fun refreshStrip() {
        val snapshot = sessions.snapshot()
        strip.setTabs(snapshot.tabs.map { SessionTabPresentation(it.id, it.title) }, snapshot.selectedId)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        sessions.stopAll()
        views.values.forEach { it.controller.dispose() }
        views.clear()
        cards.removeAll()
    }
}
