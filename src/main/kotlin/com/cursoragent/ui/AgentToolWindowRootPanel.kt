package com.cursoragent.ui

import com.cursoragent.service.AgentTransport
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.TurnSettings
import com.cursoragent.settings.AgentMode
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
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
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
class AgentToolWindowRootPanel(
    private val project: Project,
    private val onLastTabClosed: () -> Unit,
) : JPanel(BorderLayout()), Disposable {
    private val sessions = SessionTabs()
    private val strip = SessionTabStrip()
    private val cards = JPanel(CardLayout()).apply { isOpaque = false }
    private data class TabView(val presentation: com.cursoragent.ui.editor.ChatEditorPresentation, val composer: ComposerPanel, val timeline: ChatTimelinePanel, val controller: AgentUiController, val history: PastChatsCoordinator)
    private val views = mutableMapOf<String, TabView>()
    private var disposed = false
    private var projectClosing = false
    private var openedChatsPopup: JBPopup? = null
    private var openedChatsOwner: JComponent? = null
    private val uiSettingsConnection = ApplicationManager.getApplication().messageBus.connect(project)

    private val selectedView: TabView?
        get() = if (disposed || project.isDisposed) null else views[sessions.snapshot().selectedId]

    internal val actions = chatActions()

    private fun chatActions(tabId: String? = null): ToolWindowChatActions {
        fun target() = if (disposed || projectClosing || project.isDisposed) null
            else if (tabId == null) selectedView else views[tabId]
        return ToolWindowChatActions(
            settings = AgentSettingsState.getInstance(),
            available = { target() != null },
            running = { target()?.composer?.isRunning ?: false },
            transportState = { target()?.controller?.transportState() ?: (AgentTransport.PRINT to true) },
            onTransport = { target()?.controller?.selectTransport(it) },
            onSummarize = { target()?.controller?.sendPrompt("/summarize") },
            onNewChat = { open(inEditor = target()?.presentation?.inEditor == true) },
            onHistory = { event -> target()?.let { it.controller.pauseQueue(); it.history.showPopup(event) } },
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
            onExport = { TranscriptExport(project).export(target()?.controller?.conversationSnapshot()) },
            onChanges = { target()?.controller?.showChanges() },
            settingsUnavailableReason = { permission, sandbox, worktree ->
                project.getService(AgentProcessService::class.java).settingsUnavailableReason(
                    AgentTransport.ACP, TurnSettings("", "", AgentMode.AGENT, permission, sandbox), worktree,
                )
            },
            requestIdSnapshot = { if (target() == null) null else sessions.snapshot().let { if (tabId == null) it else it.copy(selectedId = tabId) } },
            onRequestIdCopyFeedback = { target()?.timeline?.showStatus(it) },
            onToggleEditor = { target()?.presentation?.toggle() },
        )
    }

    private fun resumeHistory(
        origin: String,
        conversation: com.cursoragent.history.Conversation?,
        legacyId: String?,
        match: com.cursoragent.history.ConversationMatch?,
        query: String,
    ) {
        val source = views[origin] ?: return
        if (disposed || projectClosing || project.isDisposed) return
        val inEditor = source.presentation.inEditor
        if (conversation != null) {
            sessions.open(conversation.providerId, conversationId = conversation.id, transport = conversation.transport)
        } else {
            sessions.open(legacyId)
        }
        showSelected(conversation, legacyId != null, focus = !inEditor)
        if (inEditor) selectedView?.presentation?.focusInEditor()
        if (match != null) {
            val view = selectedView
            javax.swing.SwingUtilities.invokeLater {
                val current = if (!disposed && !projectClosing && !project.isDisposed && view === selectedView) view?.controller?.conversationSnapshot() else null
                if (current != null) view?.timeline?.scrollToHistoryMatch(current, match.messageId, query)
            }
        }
    }

    init {
        border = JBUI.Borders.empty()
        isOpaque = true
        background = AgentUiColors.panelBackground
        strip.setWrapTabs(!UISettings.getInstance().scrollTabLayoutInEditor)
        uiSettingsConnection.subscribe(com.intellij.openapi.project.ProjectManager.TOPIC, object : com.intellij.openapi.project.ProjectManagerListener {
            override fun projectClosing(closingProject: Project) {
                if (closingProject === project) projectClosing = true
            }
        })
        uiSettingsConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { settings ->
            if (!disposed && !project.isDisposed) strip.setWrapTabs(!settings.scrollTabLayoutInEditor)
        })
        strip.onSelect = { id -> if (sessions.select(id)) showSelected() }
        strip.onClose = { id -> closeTabs(listOf(id)) }
        addHierarchyListener { if (openedChatsOwner?.isShowing == false) openedChatsPopup?.cancel() }
        strip.onMove = { id, index -> if (sessions.move(id, index)) refreshStrip() }
        add(strip, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        showSelected()
    }

    fun selectionContextTarget(): ((com.cursoragent.ui.composer.context.SelectionContext) -> Unit)? {
        val id = sessions.snapshot().selectedId
        val owner = selectedView ?: return null
        return { selection ->
            if (!disposed && !project.isDisposed && views[id] === owner) {
                owner.composer.promptContext.addSelection(selection)
                if (owner.presentation.inEditor) owner.presentation.focus()
                else com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Cursor Agent")?.activate {
                    if (!disposed && !projectClosing && !project.isDisposed && views[id] === owner && sessions.snapshot().selectedId == id) {
                        owner.presentation.focus()
                    }
                }
            }
        }
    }

    internal fun installHeaderToolbar(toolbar: JComponent) {
        strip.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(toolbar, BorderLayout.NORTH)
        }, BorderLayout.EAST)
    }

    private fun showOpenedChats(event: AnActionEvent) {
        if (disposed || projectClosing || project.isDisposed) return
        val editorOwner = event.getData(com.cursoragent.ui.editor.ChatEditorPresentation.KEY)
        val ownerId = if (editorOwner == null) sessions.snapshot().selectedId
            else views.entries.firstOrNull { it.value.presentation === editorOwner }?.key ?: return
        val owner = views[ownerId]?.composer ?: return
        if (!owner.isShowing) return
        openedChatsPopup?.takeUnless { it.isDisposed }?.let { it.cancel(); return }
        val snapshot = sessions.snapshot().copy(selectedId = ownerId)
        val fromEditor = views[ownerId]?.presentation?.inEditor == true
        val entries = openedChatEntries(snapshot)
        val next = JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
            .setTitle("開いているチャット")
            .setAccessibleName("開いているチャット")
            .setNamerForFiltering { it.second }
            .setFilterAlwaysVisible(true)
            .setRenderer(SimpleListCellRenderer.create("") { it: Pair<String, String> -> it.second })
            .setSelectedValue(entries.first { it.first == snapshot.selectedId }, true)
            .setItemChosenCallback { entry ->
                if (!disposed && !projectClosing && !project.isDisposed && views.containsKey(ownerId) && sessions.select(entry.first)) {
                    showSelected(focus = !fromEditor)
                    if (fromEditor) selectedView?.presentation?.focusInEditor()
                }
            }
            .setCancelOnWindowDeactivation(true)
            .createPopup()
        openedChatsPopup = next
        openedChatsOwner = owner
        Disposer.register(next, Disposable { if (openedChatsPopup === next) { openedChatsPopup = null; openedChatsOwner = null } })
        next.showInBestPositionFor(event.dataContext)
    }

    private fun confirmCloseAllChats() {
        if (disposed || project.isDisposed) return
        views.values.forEach { it.controller.pauseQueue() }
        confirmCloseChats(sessions.snapshot(), confirm = { count, running ->
            Messages.showYesNoDialog(
                project,
                "このウィンドウのチャット $count 件を閉じます。\n" +
                    "実行中: $running 件（この確認を開いた時点）。閉じる時点で実行中の処理は停止します。\n\n" +
                    "保存済みの本文は履歴から表示できます。未送信の下書き・予約した入力・保存に失敗した本文は閉じると失われます。\n" +
                    "ファイルエディター・別プロジェクトのチャット・履歴一覧のデータは削除しません。",
                "すべてのチャットを閉じる",
                "すべて閉じる", "キャンセル", Messages.getWarningIcon(),
            ) == Messages.YES
        }, close = { closeTabs(it, confirmed = true) })
    }

    private fun closeTabs(ids: List<String>, confirmed: Boolean = false) {
        if (disposed || project.isDisposed) return
        ids.forEach { views[it]?.controller?.pauseQueue() }
        if (!confirmed && ids.any { id -> views[id]?.let { it.controller.hasUnsavedBody || it.controller.hasQueuedPrompts || it.composer.isRunning || (it.composer.inputArea.text.isNotBlank() || it.composer.promptContext.draft.hasExplicit || it.composer.commands.selectedName != null || it.composer.images?.hasUnsent == true) } == true }) {
            if (Messages.showYesNoDialog(project, "未保存の本文・下書き・予約した入力、または実行中の応答があります。閉じると未保存分を失う可能性があります。閉じますか？", "チャットを閉じる", Messages.getWarningIcon()) != Messages.YES) return
        }
        if (disposed || project.isDisposed) return
        // Modal confirmation may have opened another tab; decide against the current set.
        val closesAllTabs = sessions.snapshot().tabs.all { it.id in ids }
        sessions.closeAll(ids).forEach { tab ->
            views.remove(tab.id)?.let { view ->
                view.history.dispose()
                view.controller.dispose()
                cards.remove(view.presentation.panel)
                Disposer.dispose(view.presentation)
            }
        }
        showSelected()
        if (closesAllTabs) onLastTabClosed()
    }

    private fun open(chatId: String? = null, inEditor: Boolean = false) {
        if (disposed || projectClosing || project.isDisposed) return
        sessions.open(chatId)
        showSelected(focus = !inEditor)
        if (inEditor) selectedView?.presentation?.focusInEditor()
    }

    private fun showSelected(saved: com.cursoragent.history.Conversation? = null, legacyOnly: Boolean = false, focus: Boolean = true) {
        if (disposed || projectClosing || project.isDisposed) return
        val tab = sessions.snapshot().selected
        val view = views.getOrPut(tab.id) {
            val timeline = ChatTimelinePanel()
            val composer = ComposerPanel(project, newPrintConversation =
                tab.transport == com.cursoragent.service.AgentTransport.PRINT && tab.chatId == null && saved == null && !legacyOnly)
            val controller = AgentUiController(project, timeline, composer, sessions, tab.id, saved, legacyOnly,
                onShowConversation = { if (!disposed && sessions.select(tab.id)) showSelected() },
                onTitleChanged = { if (!disposed) refreshStrip() },
            )
            composer.onSend = controller::sendPrompt
            composer.onStop = controller::stopRun
            composer.onEnqueue = controller::enqueuePrompt
            composer.onShowQueue = controller::showQueue
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
            val presentation = com.cursoragent.ui.editor.ChatEditorPresentation(
                project, panel, composer.inputArea,
                canMove = { !disposed && !projectClosing && composer.canMovePresentation },
                selectOwner = { if (!disposed && sessions.snapshot().selectedId != tab.id && sessions.select(tab.id)) showSelected(focus = false) },
                onReturn = { focusInput ->
                    if (!disposed && !projectClosing && !project.isDisposed) {
                        val window = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Cursor Agent")
                        if (focusInput) window?.activate {
                            if (!disposed && !projectClosing && !project.isDisposed && sessions.snapshot().selectedId == tab.id) {
                                views[tab.id]?.composer?.inputArea?.requestFocusInWindow()
                            }
                        } else window?.show(null)
                    }
                },
                onFailure = { timeline.showStatus("エディターで会話を開けませんでした。パネルから続けて操作できます。") },
                shortcutAllowed = { composer.canToggleEditorWithShortcut },
                headerActions = {
                    val editorActions = chatActions(tab.id)
                    editorActions.titleActions + com.cursoragent.ui.header.ChatOptionsActionGroup(editorActions.gearActions)
                },
            )
            // Reparenting must not release the native input editor, its Undo history or its carets.
            composer.inputArea.setDisposedWith(presentation)
            cards.add(presentation.panel, tab.id)
            val history = PastChatsCoordinator(project, ChatHistoryState.getInstance(project), panel,
                onChatResumed = { conversation, legacyId, match, query -> resumeHistory(tab.id, conversation, legacyId, match, query) },
                isOpen = { id -> sessions.snapshot().tabs.any { it.conversationId == id } },
            )
            panel.addHierarchyListener { if (openedChatsOwner === composer && !composer.isShowing) openedChatsPopup?.cancel() }
            TabView(presentation, composer, timeline, controller, history)
        }
        views.forEach { (id, other) ->
            other.timeline.isActiveTab = id == tab.id
            if (id != tab.id) { other.controller.pauseQueue(); other.history.cancel() }
        }
        (cards.layout as CardLayout).show(cards, tab.id)
        refreshStrip()
        if (focus) view.presentation.focus()
    }

    private fun refreshStrip() {
        val snapshot = sessions.snapshot()
        strip.setTabs(snapshot.tabs.map { SessionTabPresentation(it.id, it.title) }, snapshot.selectedId)
        snapshot.tabs.forEach { views[it.id]?.presentation?.updateTitle(it.title) }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        uiSettingsConnection.disconnect()
        openedChatsPopup?.cancel()
        openedChatsPopup = null
        sessions.stopAll()
        views.values.forEach { it.history.dispose(); it.controller.dispose(); Disposer.dispose(it.presentation) }
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
