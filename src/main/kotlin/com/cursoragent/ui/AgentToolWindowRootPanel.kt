package com.cursoragent.ui

import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTabs
import com.cursoragent.session.SessionTabsSnapshot
import com.cursoragent.settings.AgentSettingsConfigurable
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.browser.ManualBrowser
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.ToolWindowChatActions
import com.cursoragent.ui.mcp.McpServersDialog
import com.cursoragent.ui.session.SessionTabPresentation
import com.cursoragent.ui.session.SessionTabStrip
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.ide.ActivityTracker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Retain complete tab views so editor caret/selection and timeline scroll never cross sessions. */
class AgentToolWindowRootPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val sessions = SessionTabs()
    private val strip = SessionTabStrip()
    private val cards = JPanel(CardLayout()).apply { isOpaque = false }
    private data class TabView(val panel: JPanel, val composer: ComposerPanel, val timeline: ChatTimelinePanel, val controller: AgentUiController)
    private val views = mutableMapOf<String, TabView>()
    private var disposed = false
    private var openedChatsPopup: JBPopup? = null

    private val selectedView: TabView?
        get() = if (disposed || project.isDisposed) null else views[sessions.snapshot().selectedId]

    internal val actions = ToolWindowChatActions(
        settings = AgentSettingsState.getInstance(),
        available = { selectedView != null },
        running = { selectedView?.composer?.isRunning ?: false },
        transportState = { selectedView?.controller?.transportState() ?: (AgentTransport.PRINT to true) },
        onTransport = { selectedView?.controller?.selectTransport(it) },
        onSummarize = { selectedView?.controller?.sendPrompt("/summarize") },
        onNewChat = { open() },
        onHistory = { event -> history.showPopup(event) },
        onMcp = { McpServersDialog(project).show() },
        onSettings = { ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java) },
        onEditNotice = { Messages.showInfoMessage(project, ImmediateEditNotice().text, "ファイル編集について") },
        onOpenedChats = ::showOpenedChats,
        onCloseAllChats = ::confirmCloseAllChats,
        onFeedback = { BrowserUtil.browse(it) },
        previewEnabled = { UISettings.getInstance().openInPreviewTabIfPossible },
        onPreview = { enabled ->
            UISettings.getInstance().apply {
                openInPreviewTabIfPossible = enabled
                fireUISettingsChanged()
            }
        },
        onEditorSettings = {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project, { (it as? SearchableConfigurable)?.id == "editor.preferences.tabs" }, null,
            )
        },
        onIconVisibilityChanged = { ActivityTracker.getInstance().inc() },
        onBrowser = { ManualBrowser.open(project) },
        onExport = { TranscriptExport(project).export(selectedView?.controller?.conversationSnapshot()) },
    )
    private val history = PastChatsCoordinator(project, ChatHistoryState.getInstance(project), this,
        onChatResumed = { conversation, legacyId, match, query ->
            if (conversation != null) {
                sessions.open(conversation.providerId, conversationId = conversation.id, transport = conversation.transport)
            } else {
                sessions.open(legacyId)
            }
            showSelected(conversation, legacyId != null)
            if (match != null) {
                val view = selectedView
                javax.swing.SwingUtilities.invokeLater {
                    val current = view?.controller?.conversationSnapshot()
                    if (current != null) view.timeline.scrollToHistoryMatch(current, match.messageId, query)
                }
            }
        },
        isOpen = { id -> sessions.snapshot().tabs.any { it.conversationId == id } },
    )

    init {
        border = JBUI.Borders.empty()
        isOpaque = true
        background = AgentUiColors.panelBackground
        strip.onSelect = { id -> if (sessions.select(id)) showSelected() }
        strip.onClose = { id -> closeTabs(listOf(id)) }
        addHierarchyListener { if (!isShowing) openedChatsPopup?.cancel() }
        strip.onMove = { id, index -> if (sessions.move(id, index)) refreshStrip() }
        add(strip, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        showSelected()
    }

    internal fun installHeaderToolbar(toolbar: JComponent) {
        strip.add(toolbar, BorderLayout.EAST)
    }

    private fun showOpenedChats(event: AnActionEvent) {
        if (disposed || project.isDisposed || !isShowing) return
        openedChatsPopup?.takeUnless { it.isDisposed }?.let { it.cancel(); return }
        val snapshot = sessions.snapshot()
        val entries = openedChatEntries(snapshot)
        val next = JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
            .setTitle("開いているチャット")
            .setAccessibleName("開いているチャット")
            .setNamerForFiltering { it.second }
            .setFilterAlwaysVisible(true)
            .setRenderer(SimpleListCellRenderer.create("") { it: Pair<String, String> -> it.second })
            .setSelectedValue(entries.first { it.first == snapshot.selectedId }, true)
            .setItemChosenCallback { entry ->
                if (!disposed && !project.isDisposed && sessions.select(entry.first)) showSelected()
            }
            .setCancelOnWindowDeactivation(true)
            .createPopup()
        openedChatsPopup = next
        Disposer.register(next, Disposable { if (openedChatsPopup === next) openedChatsPopup = null })
        next.showInBestPositionFor(event.dataContext)
    }

    private fun confirmCloseAllChats() {
        if (disposed || project.isDisposed) return
        confirmCloseChats(sessions.snapshot(), confirm = { count, running ->
            Messages.showYesNoDialog(
                project,
                "このウィンドウのチャット $count 件を閉じます。\n" +
                    "実行中: $running 件（この確認を開いた時点）。閉じる時点で実行中の処理は停止します。\n\n" +
                    "保存済みの本文は履歴から表示できます。未送信の下書き・保存に失敗した本文は閉じると失われます。\n" +
                    "ファイルエディター・別プロジェクトのチャット・履歴一覧のデータは削除しません。",
                "すべてのチャットを閉じる",
                "すべて閉じる", "キャンセル", Messages.getWarningIcon(),
            ) == Messages.YES
        }, close = { closeTabs(it, confirmed = true) })
    }

    private fun closeTabs(ids: List<String>, confirmed: Boolean = false) {
        if (disposed || project.isDisposed) return
        if (!confirmed && ids.any { id -> views[id]?.let { it.controller.hasUnsavedBody || it.composer.isRunning || it.composer.inputArea.text.isNotBlank() } == true }) {
            if (Messages.showYesNoDialog(project, "未保存の本文・下書き、または実行中の応答があります。閉じると未保存分を失う可能性があります。閉じますか？", "チャットを閉じる", Messages.getWarningIcon()) != Messages.YES) return
        }
        sessions.closeAll(ids).forEach { tab ->
            views.remove(tab.id)?.let { view ->
                view.controller.dispose()
                cards.remove(view.panel)
            }
        }
        showSelected()
    }

    private fun open(chatId: String? = null) {
        if (disposed) return
        sessions.open(chatId)
        showSelected()
    }

    private fun showSelected(saved: com.cursoragent.history.Conversation? = null, legacyOnly: Boolean = false) {
        if (disposed) return
        val tab = sessions.snapshot().selected
        val view = views.getOrPut(tab.id) {
            val timeline = ChatTimelinePanel()
            val composer = ComposerPanel(project)
            val controller = AgentUiController(project, timeline, composer, sessions, tab.id, saved, legacyOnly)
            composer.onSend = controller::sendPrompt
            composer.onStop = controller::stopRun
            if (saved != null) {
                timeline.restore(saved)
                controller.showResumeAvailability()
            } else if (legacyOnly) {
                timeline.showStatus("本文は未保存です。作業場所の来歴を確認できないため、新しい会話を開始してください。")
                composer.setInputEnabled(false)
            }
            val panel = JPanel(BorderLayout()).apply {
                isOpaque = false
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
        openedChatsPopup?.cancel()
        openedChatsPopup = null
        history.dispose()
        sessions.stopAll()
        views.values.forEach { it.controller.dispose() }
        views.clear()
        cards.removeAll()
    }
}

/** Position distinguishes equal titles without renaming a chat or using an index as identity. */
internal fun openedChatEntries(snapshot: SessionTabsSnapshot): List<Pair<String, String>> =
    snapshot.tabs.mapIndexed { index, tab ->
        tab.id to "${index + 1}. ${tab.title}${if (tab.run != null) "（実行中）" else ""}"
    }

/** A modal confirmation can pump callbacks; freeze its targets before entering it. */
internal fun confirmCloseChats(
    snapshot: SessionTabsSnapshot,
    confirm: (count: Int, running: Int) -> Boolean,
    close: (List<String>) -> Unit,
) {
    val ids = snapshot.tabs.map { it.id }
    if (confirm(ids.size, snapshot.tabs.count { it.run != null })) close(ids)
}
