package com.cursoragent.ui

import com.cursoragent.actions.AgentPanelActions
import com.cursoragent.actions.AgentPanelCommand
import com.cursoragent.actions.AgentWindowCommand
import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationHistory
import com.cursoragent.ui.composer.context.SelectionContext
import com.cursoragent.service.AgentTransport
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.TurnSettings
import com.cursoragent.settings.AgentMode
import com.cursoragent.session.SessionTabs
import com.cursoragent.session.SessionTabsSnapshot
import com.cursoragent.session.SessionTab
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
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.KeyboardFocusManager
import java.awt.event.HierarchyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Retain complete tab views so editor caret/selection and timeline scroll never cross sessions. */
class AgentToolWindowRootPanel(
    private val project: Project,
    private val onLastTabClosed: () -> Unit,
) : JPanel(BorderLayout()), Disposable, UiDataProvider {
    private val sessions = SessionTabs()
    private val strip = SessionTabStrip()
    private val cards = JPanel(CardLayout()).apply { isOpaque = false }
    private val contentSplitter = OnePixelSplitter(false, 0.3f)
    private data class TabView(
        val panel: JPanel,
        val composer: ComposerPanel,
        val timeline: ChatTimelinePanel,
        val controller: AgentUiController,
        var lastShownNanos: Long? = null,
    )
    private val views = mutableMapOf<String, TabView>()
    private var disposed = false
    private var chatFocusGeneration = 0L
    private var openedChatsPopup: JBPopup? = null
    private val recentVisits = RecentChatVisits()
    private var recentChatsPopup: RecentChatsPopup? = null
    private var allChatsPopup: JBPopup? = null
    private var allChatsSidebar: AllChatsView? = null
    private val allChatsContainer = JPanel(BorderLayout())
    private val allChatsSidebarVisible: Boolean get() = contentSplitter.firstComponent === allChatsContainer
    private var sidebarNavigation: SidebarChatNavigation? = null
    private val uiSettingsConnection = ApplicationManager.getApplication().messageBus.connect(project)

    private val selectedView: TabView?
        get() = if (disposed || project.isDisposed) null else views[sessions.snapshot().selectedId]

    private val panelActions = AgentPanelActions(::shortcutAvailable, ::performShortcut)
    private val registeredShortcuts = mutableListOf<com.intellij.openapi.actionSystem.AnAction>()

    override fun uiDataSnapshot(sink: DataSink) {
        if (!disposed && !project.isDisposed) sink[AgentPanelActions.KEY] = panelActions
    }

    internal val windowShortcutAvailable: Boolean
        get() = selectedView?.composer?.panelShortcutAvailable == true && (!isShowing || !allChatsSidebarVisible || allChatsSidebar?.isComposing != true) &&
            !JBPopupFactory.getInstance().isChildPopupFocused(this)

    private fun shortcutAvailable(command: AgentPanelCommand): Boolean {
        val composer = selectedView?.composer ?: return false
        if (!isShowing || !windowShortcutAvailable) return false
        return when (command) {
            AgentPanelCommand.STOP -> composer.isRunning
            AgentPanelCommand.MODE_MENU -> composer.modeSelector.isEnabled
            AgentPanelCommand.MODEL_MENU -> composer.modelSelector.isEnabled
            AgentPanelCommand.ADD_CONTEXT -> composer.inputArea.isEnabled
            else -> true
        }
    }

    private fun performShortcut(command: AgentPanelCommand, event: AnActionEvent) {
        if (!shortcutAvailable(command)) return
        val view = selectedView ?: return
        when (command) {
            AgentPanelCommand.NEW_CHAT -> open()
            AgentPanelCommand.CLOSE_CHAT -> closeTabs(listOf(sessions.snapshot().selectedId))
            AgentPanelCommand.PREVIOUS_CHAT, AgentPanelCommand.NEXT_CHAT -> navigateChat(command == AgentPanelCommand.PREVIOUS_CHAT)
            AgentPanelCommand.PREVIOUS_AGENT, AgentPanelCommand.NEXT_AGENT -> {
                if (!allChatsSidebarVisible) navigateChat(command == AgentPanelCommand.PREVIOUS_AGENT)
                else navigateSidebar(command)
            }
            AgentPanelCommand.RECENT_CHAT, AgentPanelCommand.LEAST_RECENT_CHAT -> showRecentChats(command, event)
            AgentPanelCommand.STOP -> view.controller.stopRun()
            AgentPanelCommand.MODE_MENU -> view.composer.modeSelector.doClick()
            AgentPanelCommand.MODEL_MENU -> view.composer.modelSelector.doClick()
            AgentPanelCommand.ADD_CONTEXT -> view.composer.promptContext.onAddMention()
            AgentPanelCommand.HISTORY -> { view.controller.pauseQueue(); history.showPopup(event) }
            AgentPanelCommand.CHANGES -> view.controller.showChanges()
            AgentPanelCommand.SETTINGS -> ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java)
        }
    }

    internal val actions = ToolWindowChatActions(
        settings = AgentSettingsState.getInstance(),
        available = { selectedView != null },
        running = { selectedView?.composer?.isRunning ?: false },
        transportState = { selectedView?.controller?.transportState() ?: (AgentTransport.PRINT to true) },
        onTransport = { selectedView?.controller?.selectTransport(it) },
        onSummarize = { selectedView?.controller?.sendPrompt("/summarize") },
        onNewChat = { open() },
        onHistory = { event -> selectedView?.controller?.pauseQueue(); history.showPopup(event) },
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
        onChanges = { selectedView?.controller?.showChanges() },
        settingsUnavailableReason = { permission, sandbox, worktree ->
            project.getService(AgentProcessService::class.java).settingsUnavailableReason(
                AgentTransport.ACP, TurnSettings("", "", AgentMode.AGENT, permission, sandbox), worktree,
            )
        },
        requestIdSnapshot = { if (selectedView == null) null else sessions.snapshot() },
        onRequestIdCopyFeedback = { selectedView?.timeline?.showStatus(it) },
    )
    private val history = PastChatsCoordinator(project, ChatHistoryState.getInstance(project), this,
        onChatResumed = { conversation, legacyId, match, query ->
            openSavedChat(conversation, legacyId)
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
        strip.setWrapTabs(!UISettings.getInstance().scrollTabLayoutInEditor)
        uiSettingsConnection.subscribe(UISettingsListener.TOPIC, UISettingsListener { settings ->
            if (!disposed && !project.isDisposed) strip.setWrapTabs(!settings.scrollTabLayoutInEditor)
        })
        strip.onSelect = { id -> if (sessions.select(id)) showSelected() }
        strip.onClose = { id -> closeTabs(listOf(id)) }
        addHierarchyListener { event ->
            if (!isShowing) {
                openedChatsPopup?.cancel()
                recentChatsPopup?.dispose()
                allChatsPopup?.cancel()
                stopSidebarNavigation()
                allChatsSidebar?.suspendUpdates()
                if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) cancelPendingChatFocus()
            } else if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) allChatsSidebar?.reload()
        }
        strip.onMove = { id, index -> if (sessions.move(id, index)) refreshStrip() }
        add(strip, BorderLayout.NORTH)
        contentSplitter.secondComponent = cards
        add(contentSplitter, BorderLayout.CENTER)
        showSelected()
        if (PropertiesComponent.getInstance(project).getBoolean("CursorAgent.allChatsSidebar", false)) setAllChatsVisible(true)
        AgentPanelCommand.entries.forEach { command ->
            ActionManager.getInstance().getAction(command.actionId)?.let { action ->
                action.registerCustomShortcutSet(action.shortcutSet, this)
                registeredShortcuts.add(action)
            }
        }
    }

    fun selectionContextTarget(): ((com.cursoragent.ui.composer.context.SelectionContext) -> Unit)? {
        val id = sessions.snapshot().selectedId
        val owner = selectedView ?: return null
        return { selection ->
            if (!disposed && !project.isDisposed && views[id] === owner) {
                owner.composer.promptContext.addSelection(selection)
                owner.composer.inputArea.requestFocusInWindow()
            }
        }
    }

    internal fun cancelPendingChatFocus() { chatFocusGeneration++ }

    private fun recentChatId(tab: SessionTab, view: TabView): RecentChatId =
        if (view.controller.conversationSnapshot() == null && tab.chatId != null) RecentChatId.LegacyPrint(tab.chatId)
        else RecentChatId.Body(tab.conversationId)

    private fun openRecentEntries(): List<RecentChatEntry> = sessions.snapshot().tabs.map { tab ->
        val view = views.getValue(tab.id)
        val conversation = view.controller.conversationSnapshot()
        RecentChatEntry(recentChatId(tab, view), tab.title, conversation?.updatedMs ?: 0L, tab.transport, tab.chatId,
            open = true, running = view.composer.isRunning, description = conversation?.preview.orEmpty())
    }

    internal fun toggleAllChats(window: ToolWindow) {
        if (!windowShortcutAvailable || window.isDisposed || window.project !== project) return
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val focused = isShowing && focus != null && SwingUtilities.isDescendingFrom(focus, this)
        if (allChatsSidebarVisible && focused) {
            setAllChatsVisible(false)
            selectedView?.composer?.inputArea?.requestFocusInWindow()
            return
        }
        setAllChatsVisible(true)
        val ticket = ++chatFocusGeneration
        window.activate({
            if (!disposed && !project.isDisposed && !window.isDisposed && window.isAvailable && window.isVisible &&
                isShowing && allChatsSidebarVisible && ticket == chatFocusGeneration && windowShortcutAvailable) showAllChatsPicker()
        }, false)
    }

    private fun setAllChatsVisible(visible: Boolean) {
        stopSidebarNavigation()
        if (!visible) {
            cancelPendingChatFocus()
            allChatsPopup?.cancel()
            allChatsSidebar?.suspendUpdates()
            contentSplitter.firstComponent = null
        } else if (allChatsSidebar == null) {
            val view = AllChatsView(project, false, ::openRecentEntries,
                selectedId = { selectedView?.let { recentChatId(sessions.snapshot().selected, it) } },
                valid = { !disposed && !project.isDisposed && isShowing && allChatsSidebarVisible },
                onChoose = { if (windowShortcutAvailable) { stopSidebarNavigation(); openRecentChat(it) } },
            )
            allChatsSidebar = view
            val header = JPanel(BorderLayout()).apply {
                add(JLabel("All Agents"), BorderLayout.CENTER)
                add(JButton("閉じる").apply {
                    toolTipText = "チャット一覧を隠す"
                    addActionListener { setAllChatsVisible(false); selectedView?.composer?.inputArea?.requestFocusInWindow() }
                }, BorderLayout.EAST)
            }
            allChatsContainer.add(header, BorderLayout.NORTH)
            allChatsContainer.add(view, BorderLayout.CENTER)
        }
        if (visible) contentSplitter.firstComponent = allChatsContainer
        PropertiesComponent.getInstance(project).setValue("CursorAgent.allChatsSidebar", visible, false)
        allChatsSidebar?.reload()
        revalidate()
        repaint()
    }

    private fun showAllChatsPicker() {
        allChatsPopup?.cancel()
        val ticket = ++chatFocusGeneration
        lateinit var popup: JBPopup
        val view = AllChatsView(project, true, ::openRecentEntries, selectedId = { null },
            valid = { !disposed && !project.isDisposed && isShowing && ticket == chatFocusGeneration && allChatsPopup === popup },
            onChoose = { id -> popup.cancel(); openRecentChat(id) },
        )
        view.preferredSize = JBUI.size(560, 340)
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(view, view.focusComponent)
            .setTitle("All Agents")
            .setProject(project)
            .setRequestFocus(true)
            .setResizable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(false)
            .setKeyEventHandler { event ->
                if (!view.isComposing && event.id == java.awt.event.KeyEvent.KEY_PRESSED && event.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                    popup.cancel(); true
                } else false
            }
            .createPopup()
        allChatsPopup = popup
        Disposer.register(popup, Disposable { view.dispose(); if (allChatsPopup === popup) allChatsPopup = null })
        popup.showInCenterOf(this)
        view.reload()
    }

    private fun navigateSidebar(command: AgentPanelCommand) {
        val view = allChatsSidebar ?: return
        if (!view.navigate(command == AgentPanelCommand.PREVIOUS_AGENT)) return
        if (sidebarNavigation == null) {
            sidebarNavigation = SidebarChatNavigation(this,
                valid = { !disposed && !project.isDisposed && isShowing && allChatsSidebarVisible && allChatsSidebar === view && windowShortcutAvailable },
                onFinish = { accept ->
                    sidebarNavigation = null
                    if (accept) view.confirmNavigation() else view.cancelNavigation()
                },
            )
        }
        sidebarNavigation?.useShortcuts(ActionManager.getInstance().getAction(command.actionId)?.shortcutSet?.shortcuts.orEmpty())
    }

    private fun stopSidebarNavigation() {
        sidebarNavigation?.dispose()
        sidebarNavigation = null
        allChatsSidebar?.cancelNavigation()
    }

    private fun navigateChat(reverse: Boolean) {
        val snapshot = sessions.snapshot()
        if (snapshot.tabs.size > 1) {
            val index = Math.floorMod(snapshot.tabs.indexOfFirst { it.id == snapshot.selectedId } + if (reverse) -1 else 1, snapshot.tabs.size)
            if (sessions.select(snapshot.tabs[index].id)) showSelected()
            return
        }
        val owner = selectedView ?: return
        val selected = recentChatId(snapshot.selected, owner)
        val ticket = ++chatFocusGeneration
        val store = project.getService(ConversationHistory::class.java)
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || !isShowing || ticket != chatFocusGeneration ||
                selectedView !== owner || !windowShortcutAvailable) return@invokeLater
            val loaded = result.getOrNull()
            if (loaded == null) {
                owner.timeline.showStatus("履歴を読み込めませんでした。保存先の権限を確認してください。")
                return@invokeLater
            }
            val saved = loaded.conversations.filterNot { store.isDeleted(it.id) }
            val legacy = ChatHistoryState.getInstance(project).list()
            val target = adjacentSavedChat(availableChatEntries(openRecentEntries(), saved, legacy), selected, reverse)
            when (target) {
                is RecentChatId.Body -> saved.firstOrNull { it.id == target.id }?.let { openNavigatedChat(it, null, snapshot.selectedId) }
                is RecentChatId.LegacyPrint -> if (legacy.any { it.chatId == target.id }) openNavigatedChat(null, target.id, snapshot.selectedId)
                null -> Unit
            }
        } }
    }

    private fun openNavigatedChat(conversation: Conversation?, legacyId: String?, previousId: String) {
        openSavedChat(conversation, legacyId)
        // Replace an idle, saved tab so repeated navigation still walks history. Preserve live work and names.
        val snapshot = sessions.snapshot()
        val previous = snapshot.tabs.firstOrNull { it.id == previousId } ?: return
        if (snapshot.selectedId != previousId && canReplaceNavigationTab(previous, hasPendingChatWork(previousId))) {
            closeTabs(listOf(previousId), confirmed = true)
        }
    }

    private fun showRecentChats(command: AgentPanelCommand, event: AnActionEvent) {
        val ticket = ++chatFocusGeneration
        recentChatsPopup?.dispose()
        val popup = RecentChatsPopup(project, command == AgentPanelCommand.LEAST_RECENT_CHAT, recentVisits.snapshot(),
            openEntries = ::openRecentEntries,
            valid = { !disposed && !project.isDisposed && isShowing && ticket == chatFocusGeneration &&
                selectedView?.composer?.panelShortcutAvailable == true },
            onChoose = ::openRecentChat,
        )
        recentChatsPopup = popup
        popup.show(event)
    }

    private fun openRecentChat(id: RecentChatId) {
        if (disposed || project.isDisposed || !isShowing || !windowShortcutAvailable) return
        val open = sessions.snapshot().tabs.firstOrNull { tab -> views[tab.id]?.let { recentChatId(tab, it) == id } == true }
        if (open != null) {
            if (sessions.select(open.id)) showSelected()
            return
        }
        // The picker owns metadata only. A closed chat may have been saved/deleted while it was visible.
        val ticket = ++chatFocusGeneration
        val store = project.getService(ConversationHistory::class.java)
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || !isShowing || ticket != chatFocusGeneration || !windowShortcutAvailable) return@invokeLater
            val loaded = result.getOrNull()
            if (loaded == null) {
                selectedView?.timeline?.showStatus("履歴を読み込めませんでした。保存先の権限を確認してください。")
                return@invokeLater
            }
            val conversation = loaded.conversations.firstOrNull {
                !store.isDeleted(it.id) && when (id) {
                    is RecentChatId.Body -> it.id == id.id
                    is RecentChatId.LegacyPrint -> it.transport == AgentTransport.PRINT && it.providerId == id.id
                }
            }
            if (conversation != null) openSavedChat(conversation, null)
            else if (id is RecentChatId.LegacyPrint && ChatHistoryState.getInstance(project).list().any { it.chatId == id.id })
                openSavedChat(null, id.id)
            else selectedView?.timeline?.showStatus("この会話は削除済みか、本文を読み込めませんでした。")
        } }
    }

    private fun openSavedChat(conversation: Conversation?, legacyId: String?) {
        if (disposed || project.isDisposed) return
        if (conversation != null) sessions.open(conversation.providerId, conversationId = conversation.id, transport = conversation.transport)
        else sessions.open(legacyId)
        showSelected(conversation, legacyId != null)
    }

    internal fun enterChat(command: AgentWindowCommand, selection: SelectionContext?, window: ToolWindow) {
        if (!windowShortcutAvailable || window.isDisposed || window.project !== project) return
        val snapshot = sessions.snapshot()
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val focused = isShowing && focus != null && SwingUtilities.isDescendingFrom(focus, this)
        val tabs = snapshot.tabs.map { tab ->
            val view = views.getValue(tab.id)
            // Live composer state is authoritative: SessionTab.draft is only updated on send.
            val empty = view.composer.selection.mode == AgentMode.AGENT && tab.run == null &&
                !view.composer.isRunning && !view.controller.hasQueuedPrompts &&
                view.controller.conversationSnapshot()?.turns?.isEmpty() == true &&
                view.composer.inputArea.text.isBlank() && view.composer.commands.selectedName == null &&
                view.composer.images?.hasUnsent != true
            ChatEntryTab(tab.id, empty, view.composer.promptContext.draft.snapshot().selections.isNotEmpty(), view.lastShownNanos)
        }
        when (val entry = chatEntry(command, tabs, snapshot.selectedId, focused, window.isVisible, System.nanoTime())) {
            ChatEntry.Hide -> { cancelPendingChatFocus(); window.hide(null) }
            is ChatEntry.Focus -> {
                if (entry.id == null) {
                    open()
                    selectedView?.composer?.modeSelector?.selectMode(AgentMode.AGENT)
                } else if (entry.id != snapshot.selectedId && sessions.select(entry.id)) {
                    showSelected()
                }
                val owner = selectedView ?: return
                if (entry.insertSelection && selection != null) owner.composer.promptContext.addSelection(selection)
                owner.lastShownNanos = System.nanoTime()
                val generation = ++chatFocusGeneration
                window.activate({
                    if (!window.isDisposed && window.isAvailable && window.isVisible && generation == chatFocusGeneration &&
                        selectedView === owner && windowShortcutAvailable) owner.composer.inputArea.requestFocusInWindow()
                }, false)
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
        if (!confirmed && ids.any(::hasPendingChatWork)) {
            if (Messages.showYesNoDialog(project, "未保存の本文・下書き・予約した入力、または実行中の応答があります。閉じると未保存分を失う可能性があります。閉じますか？", "チャットを閉じる", Messages.getWarningIcon()) != Messages.YES) return
        }
        if (disposed || project.isDisposed) return
        // Modal confirmation may have opened another tab; decide against the current set.
        val closesAllTabs = sessions.snapshot().tabs.all { it.id in ids }
        sessions.closeAll(ids).forEach { tab ->
            views.remove(tab.id)?.let { view ->
                view.controller.dispose()
                cards.remove(view.panel)
            }
        }
        showSelected()
        if (closesAllTabs) onLastTabClosed()
    }

    private fun hasPendingChatWork(id: String): Boolean {
        val view = views[id] ?: return false
        return view.controller.hasUnsavedBody || view.controller.hasQueuedPrompts || view.composer.isRunning ||
            sessions.snapshot().tabs.any { it.id == id && it.run != null } ||
            view.composer.inputArea.text.isNotBlank() || view.composer.promptContext.draft.hasExplicit ||
            view.composer.commands.selectedName != null || view.composer.images?.hasUnsent == true
    }

    private fun open(chatId: String? = null) {
        if (disposed) return
        sessions.open(chatId)
        showSelected()
    }

    private fun showSelected(saved: com.cursoragent.history.Conversation? = null, legacyOnly: Boolean = false) {
        if (disposed) return
        cancelPendingChatFocus()
        recentChatsPopup?.dispose()
        allChatsPopup?.cancel()
        stopSidebarNavigation()
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
            cards.add(panel, tab.id)
            TabView(panel, composer, timeline, controller)
        }
        views.forEach { (id, other) ->
            other.timeline.isActiveTab = id == tab.id
            if (id != tab.id) other.controller.pauseQueue()
        }
        (cards.layout as CardLayout).show(cards, tab.id)
        refreshStrip()
        recentVisits.visit(recentChatId(tab, view))
        view.lastShownNanos = System.nanoTime()
        view.composer.inputArea.requestFocusInWindow()
        allChatsSidebar?.reload()
    }

    private fun refreshStrip() {
        val snapshot = sessions.snapshot()
        strip.setTabs(snapshot.tabs.map { SessionTabPresentation(it.id, it.title) }, snapshot.selectedId)
        allChatsSidebar?.refreshOpenEntries()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        registeredShortcuts.forEach { it.unregisterCustomShortcutSet(this) }
        registeredShortcuts.clear()
        uiSettingsConnection.disconnect()
        openedChatsPopup?.cancel()
        openedChatsPopup = null
        recentChatsPopup?.dispose()
        recentChatsPopup = null
        allChatsPopup?.cancel()
        allChatsPopup = null
        stopSidebarNavigation()
        allChatsSidebar?.dispose()
        allChatsSidebar = null
        allChatsContainer.removeAll()
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
