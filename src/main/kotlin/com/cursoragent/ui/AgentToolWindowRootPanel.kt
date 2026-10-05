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
import com.cursoragent.ui.timeline.AgentRequestCard
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
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.HierarchyEvent
import javax.swing.JButton
import javax.swing.AbstractButton
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
        val presentation: com.cursoragent.ui.editor.ChatEditorPresentation,
        val editorActions: ToolWindowChatActions,
        var lastShownNanos: Long? = null,
    )
    private val views = mutableMapOf<String, TabView>()
    private var disposed = false
    private var projectClosing = false
    private var changingPresentation = false
    private var chatFocusGeneration = 0L
    private val inputFocusReturn = ChatFocusReturn(project)
    private val requestShortcutKeys = RequestShortcutKeys()
    private var openedChatsPopup: JBPopup? = null
    private val recentVisits = RecentChatVisits()
    private var recentChatsPopup: RecentChatsPopup? = null
    private var allChatsPopup: JBPopup? = null
    private var historyMenuPopup: JBPopup? = null
    private var historyMenuView: AllChatsView? = null
    private var allChatsSidebar: AllChatsView? = null
    private val allChatsContainer = JPanel(BorderLayout())
    private val allChatsSidebarVisible: Boolean get() = contentSplitter.firstComponent === allChatsContainer
    private var sidebarNavigation: SidebarChatNavigation? = null
    private val uiSettingsConnection = ApplicationManager.getApplication().messageBus.connect(project)

    private val selectedView: TabView?
        get() = if (disposed || projectClosing || project.isDisposed) null else views[sessions.snapshot().selectedId]

    private val selectedChatComponent: JComponent
        get() = selectedView?.takeIf { it.presentation.inEditor }?.presentation?.component ?: this
    private val selectedChatShowing: Boolean
        get() = selectedView?.let { if (it.presentation.inEditor) it.panel.isShowing else isShowing } == true
    private val selectedChatSidebar: Boolean
        get() = isShowing && allChatsSidebarVisible && (selectedView?.presentation?.inEditor != true ||
            KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.let { SwingUtilities.isDescendingFrom(it, this) } == true)

    internal fun ownsEditorContext(presentation: com.cursoragent.ui.editor.ChatEditorPresentation): Boolean =
        selectedView?.presentation === presentation && presentation.alive

    private val panelActions = AgentPanelActions(::shortcutAvailable, ::performShortcut)
    private val registeredShortcuts = mutableListOf<com.intellij.openapi.actionSystem.AnAction>()

    override fun uiDataSnapshot(sink: DataSink) {
        if (!disposed && !project.isDisposed) sink[AgentPanelActions.KEY] = panelActions
    }

    internal val windowShortcutAvailable: Boolean
        get() = selectedView?.composer?.panelShortcutAvailable == true && (!selectedChatSidebar || allChatsSidebar?.isComposing != true) &&
            !JBPopupFactory.getInstance().isChildPopupFocused(this) &&
            !JBPopupFactory.getInstance().isChildPopupFocused(selectedChatComponent)

    private fun shortcutAvailable(command: AgentPanelCommand): Boolean {
        val composer = selectedView?.composer ?: return false
        if ((!isShowing && !selectedChatShowing) || !windowShortcutAvailable) return false
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (command in setOf(
                AgentPanelCommand.ACCEPT_PENDING, AgentPanelCommand.STOP,
                AgentPanelCommand.APPROVE_TOOL, AgentPanelCommand.SKIP_TOOL,
            ) &&
            (focus == null || !focus.isShowing || !SwingUtilities.isDescendingFrom(focus, selectedChatComponent))) return false
        return when (command) {
            AgentPanelCommand.SUBMIT_INITIAL -> composer.canSubmitInitial && sessions.snapshot().selected.run == null &&
                selectedView?.controller?.conversationSnapshot()?.turns?.isEmpty() == true
            AgentPanelCommand.RESET_CHAT -> composer.canResetFrom(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
            AgentPanelCommand.UNFOCUS_INPUT -> composer.canUnfocusFrom(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
            AgentPanelCommand.ACCEPT_PENDING -> selectedView?.timeline?.pendingInput(focus)?.canRespond(true) == true
            AgentPanelCommand.APPROVE_TOOL, AgentPanelCommand.SKIP_TOOL -> selectedView?.let { view ->
                toolReviewTarget(composer, view.timeline, focus, command == AgentPanelCommand.APPROVE_TOOL)
            } != null
            AgentPanelCommand.STOP -> selectedView?.timeline?.let { timeline ->
                if (timeline.hasPendingInput) timeline.pendingInput(focus)?.canRespond(false) == true
                else composer.isRunning
            } == true
            AgentPanelCommand.MODE_MENU -> composer.canCycleMode
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
            AgentPanelCommand.RESET_CHAT -> resetChat()
            AgentPanelCommand.SUBMIT_INITIAL -> view.composer.submitInitial(event)
            AgentPanelCommand.UNFOCUS_INPUT -> { cancelPendingChatFocus(); inputFocusReturn.restore() }
            AgentPanelCommand.CLOSE_CHAT -> closeTabs(listOf(sessions.snapshot().selectedId))
            AgentPanelCommand.PREVIOUS_CHAT, AgentPanelCommand.NEXT_CHAT -> navigateChat(command == AgentPanelCommand.PREVIOUS_CHAT)
            AgentPanelCommand.PREVIOUS_AGENT, AgentPanelCommand.NEXT_AGENT -> {
                if (!selectedChatSidebar) navigateChat(command == AgentPanelCommand.PREVIOUS_AGENT)
                else navigateSidebar(command)
            }
            AgentPanelCommand.RECENT_CHAT, AgentPanelCommand.LEAST_RECENT_CHAT -> showRecentChats(command, event)
            AgentPanelCommand.ACCEPT_PENDING, AgentPanelCommand.STOP,
            AgentPanelCommand.APPROVE_TOOL, AgentPanelCommand.SKIP_TOOL -> {
                if (!requestShortcutKeys.accept(event.inputEvent as? java.awt.event.KeyEvent)) return
                if (view.timeline.hasPendingInput) {
                    view.timeline.pendingInput(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
                        ?.respond(command == AgentPanelCommand.ACCEPT_PENDING || command == AgentPanelCommand.APPROVE_TOOL)
                } else if (command == AgentPanelCommand.STOP) view.controller.stopRun()
            }
            AgentPanelCommand.MODE_MENU -> view.composer.cycleMode()
            AgentPanelCommand.MODEL_MENU -> view.composer.modelSelector.doClick()
            AgentPanelCommand.ADD_CONTEXT -> view.composer.promptContext.onAddMention()
            AgentPanelCommand.CHANGES -> view.controller.showChanges()
        }
    }

    internal val actions = chatActions()

    private fun chatActions(tabId: String? = null): ToolWindowChatActions {
        fun target() = if (disposed || projectClosing || project.isDisposed) null
            else if (tabId == null) selectedView else views[tabId]
        fun selectTarget(): Boolean {
            if (target() == null) return false
            if (tabId != null && sessions.snapshot().selectedId != tabId) {
                if (!sessions.select(tabId)) return false
                showSelected(focus = false)
            }
            return true
        }
        return ToolWindowChatActions(
            settings = AgentSettingsState.getInstance(),
            available = { target() != null },
            running = { target()?.composer?.isRunning ?: false },
            transportState = { target()?.controller?.transportState() ?: (AgentTransport.PRINT to true) },
            onTransport = { target()?.controller?.selectTransport(it) },
            onSummarize = { target()?.controller?.sendPrompt("/summarize") },
            onNewChat = { if (selectTarget()) open() },
            onHistory = { if (selectTarget()) history.request(toggle = true) },
            onMcp = { McpServersDialog(project).show() },
            onSettings = { ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java) },
            onEditNotice = { Messages.showInfoMessage(project, ImmediateEditNotice().text, "ファイル編集について") },
            onOpenedChats = { if (selectTarget()) showOpenedChats(it) },
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
            onToggleEditor = { if (selectTarget()) target()?.presentation?.toggle() },
        )
    }

    private val history = PastChatsCoordinator(project, ChatHistoryState.getInstance(project),
        onChatResumed = { conversation, legacyId, match, query ->
            openSavedChat(conversation, legacyId)
            if (match != null) {
                val view = selectedView
                javax.swing.SwingUtilities.invokeLater {
                    val current = if (view === selectedView) view?.controller?.conversationSnapshot() else null
                    if (current != null) view?.timeline?.scrollToHistoryMatch(current, match.messageId, query)
                }
            }
        },
        isOpen = { id -> sessions.snapshot().tabs.any { it.conversationId == id } },
        context = { HistoryPopupContext(selectedView?.let { sessions.snapshot().selectedId }, selectedChatShowing,
            selectedChatSidebar, historyMenuAllowed) },
        onShowing = { selectedView?.controller?.pauseQueue() },
        showMenu = ::showHistoryMenu,
    )

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
        addHierarchyListener { event ->
            if (!isShowing) {
                if (!selectedChatShowing) {
                    openedChatsPopup?.cancel()
                    recentChatsPopup?.dispose()
                    if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) cancelPendingChatFocus()
                }
                allChatsPopup?.cancel()
                stopSidebarNavigation()
                allChatsSidebar?.suspendUpdates()
            } else if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) allChatsSidebar?.reload()
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) history.refresh()
        }
        strip.onMove = { id, index -> if (sessions.move(id, index)) refreshStrip() }
        add(strip, BorderLayout.NORTH)
        contentSplitter.secondComponent = cards
        add(contentSplitter, BorderLayout.CENTER)
        val shortcutIds = AgentPanelCommand.entries.filterNot { it == AgentPanelCommand.UNFOCUS_INPUT }.map { it.actionId } +
            listOf(AgentWindowCommand.SETTINGS.actionId, AgentWindowCommand.HISTORY.actionId)
        shortcutIds.forEach { actionId ->
            ActionManager.getInstance().getAction(actionId)?.let { action ->
                action.registerCustomShortcutSet(action.shortcutSet, this)
                registeredShortcuts.add(action)
            }
        }
        showSelected()
        if (PropertiesComponent.getInstance(project).getBoolean("CursorAgent.allChatsSidebar", false)) setAllChatsVisible(true)
    }

    fun selectionContextTarget(): ((com.cursoragent.ui.composer.context.SelectionContext) -> Unit)? {
        val id = sessions.snapshot().selectedId
        val owner = selectedView ?: return null
        return { selection ->
            if (!disposed && !project.isDisposed && views[id] === owner) {
                inputFocusReturn.remember(ChatFocusOrigin.EDITOR)
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

    internal val historyShortcutAvailable: Boolean
        get() = selectedView != null && !selectedChatSidebar && windowShortcutAvailable

    internal fun requestHistory() { if (historyShortcutAvailable) history.request() }

    internal fun cancelPendingChatFocus() { chatFocusGeneration++ }

    private fun recentChatId(tab: SessionTab, view: TabView): RecentChatId =
        if (view.controller.conversationSnapshot() == null && tab.chatId != null) RecentChatId.LegacyPrint(tab.chatId)
        else RecentChatId.Body(tab.conversationId)

    private val chatArchive by lazy { ChatArchiveState(PropertiesComponent.getInstance(project)) }

    private fun openRecentEntries(): List<RecentChatEntry> = sessions.snapshot().tabs.map { tab ->
        val view = views.getValue(tab.id)
        val conversation = view.controller.conversationSnapshot()
        RecentChatEntry(recentChatId(tab, view), tab.title, conversation?.updatedMs ?: 0L, tab.transport, tab.chatId,
            open = tab.visible, running = view.composer.isRunning, description = conversation?.preview.orEmpty().ifBlank {
                view.composer.inputArea.text.trim().replace('\n', ' ').replace('\r', ' ').take(120).ifBlank {
                    view.composer.commands.selectedName?.let { "/$it" } ?: if (view.composer.images?.attachment != null) "画像付きの下書き" else ""
                }
            })
    }

    internal fun toggleAllChats(window: ToolWindow, focusOrigin: ChatFocusOrigin? = null) {
        if (!windowShortcutAvailable || window.isDisposed || window.project !== project) return
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val focused = isShowing && focus != null && SwingUtilities.isDescendingFrom(focus, this)
        if (allChatsSidebarVisible && focused) {
            setAllChatsVisible(false)
            selectedView?.composer?.inputArea?.requestFocusInWindow()
            return
        }
        inputFocusReturn.remember(focusOrigin)
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
            val view = AllChatsView(project, ChatListMode.SIDEBAR, ::openRecentEntries,
                selectedId = { selectedView?.let { recentChatId(sessions.snapshot().selected, it) } },
                valid = { !disposed && !project.isDisposed && isShowing && allChatsSidebarVisible },
                onChoose = { if (windowShortcutAvailable) { stopSidebarNavigation(); openRecentChat(it, allowArchived = true) } },
                onArchive = ::archiveChat,
                onArchivePrior = ::archivePriorChats,
                onOpenInNewTab = { id, isCurrent ->
                    openRecentChat(id, allowArchived = true, isCurrent = isCurrent)
                },
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
        history.refresh()
        PropertiesComponent.getInstance(project).setValue("CursorAgent.allChatsSidebar", visible, false)
        allChatsSidebar?.reload()
        revalidate()
        repaint()
    }

    private fun showAllChatsPicker() {
        allChatsPopup?.cancel()
        val ticket = ++chatFocusGeneration
        lateinit var popup: JBPopup
        val view = AllChatsView(project, ChatListMode.QUICK_ACCESS, ::openRecentEntries, selectedId = { null },
            valid = { !disposed && !project.isDisposed && isShowing && ticket == chatFocusGeneration && allChatsPopup != null && allChatsPopup === popup },
            onChoose = { id -> popup.cancel(); openRecentChat(id) },
            onArchive = ::archiveChat,
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

    private val historyMenuAllowed: Boolean
        get() {
            if (historyMenuView?.isComposing == true) return false
            if (windowShortcutAvailable) return true
            return selectedView?.composer?.panelShortcutAvailable == true && historyMenuPopup?.isVisible == true &&
                JBPopupFactory.getInstance().getChildFocusedPopup(selectedChatComponent) === historyMenuPopup
        }

    private fun showHistoryMenu(
        loaded: com.cursoragent.history.ConversationStore.Loaded,
        current: () -> Boolean,
        onClosed: () -> Unit,
    ): Disposable {
        lateinit var popup: JBPopup
        val ownerId = sessions.snapshot().selectedId
        val view = AllChatsView(project, ChatListMode.HISTORY, ::openRecentEntries,
            selectedId = { selectedView?.let { recentChatId(sessions.snapshot().selected, it) } },
            valid = { !disposed && !project.isDisposed && selectedChatShowing && historyMenuPopup != null && current() && historyMenuPopup === popup },
            onChoose = { id -> popup.cancel(); openRecentChat(id, allowArchived = true) },
            onArchive = ::archiveChat,
            onManage = {
                val ticket = chatFocusGeneration
                popup.cancel()
                SwingUtilities.invokeLater {
                    if (!disposed && !project.isDisposed && selectedChatShowing && ticket == chatFocusGeneration && sessions.snapshot().selectedId == ownerId &&
                        !selectedChatSidebar && windowShortcutAvailable) history.request(manage = true)
                }
            },
        )
        view.preferredSize = JBUI.size(500, 340)
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(view, view.focusComponent)
            .setTitle("履歴").setProject(project).setRequestFocus(true)
            .setResizable(true).setCancelOnClickOutside(true).setCancelOnOtherWindowOpen(true)
            .setCancelOnWindowDeactivation(true).setCancelKeyEnabled(false)
            .setKeyEventHandler { event ->
                if (!view.isComposing && event.id == java.awt.event.KeyEvent.KEY_PRESSED && event.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                    popup.cancel(); true
                } else false
            }.createPopup()
        historyMenuPopup = popup
        historyMenuView = view
        Disposer.register(popup, Disposable {
            view.dispose()
            if (historyMenuPopup === popup) {
                historyMenuPopup = null
                historyMenuView = null
            }
            onClosed()
        })
        popup.showUnderneathOf(historyPopupAnchor(popup))
        view.useLoadedHistory(loaded)
        return popup
    }

    private fun historyPopupAnchor(popup: JBPopup): Component {
        // The native toggle marker consumes a click on the opener instead of closing then reopening.
        // Resolve by action identity even when the global shortcut opened the menu first.
        val owner = selectedView
        val action = if (owner?.presentation?.inEditor == true) owner.editorActions.historyAction else actions.historyAction
        val component = selectedChatComponent
        val button = UIUtil.findComponentsOfType(SwingUtilities.getRootPane(component) ?: component, ActionButton::class.java)
            .firstOrNull { it.action === action && it.isShowing && (owner?.presentation?.inEditor != true || SwingUtilities.isDescendingFrom(it, component)) }
        PopupUtil.setPopupToggleComponent(popup, button)
        return button ?: if (owner?.presentation?.inEditor == true) component else strip
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
        val inEditor = selectedView?.presentation?.inEditor == true
        val snapshot = sessions.snapshot()
        val visible = snapshot.visibleTabs
        if (visible.size > 1) {
            val index = Math.floorMod(visible.indexOfFirst { it.id == snapshot.selectedId } + if (reverse) -1 else 1, visible.size)
            if (sessions.select(visible[index].id)) showSelected(inEditor = inEditor)
            return
        }
        val owner = selectedView ?: return
        val selected = recentChatId(snapshot.selected, owner)
        val ticket = ++chatFocusGeneration
        val store = project.getService(ConversationHistory::class.java)
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || !selectedChatShowing || ticket != chatFocusGeneration ||
                selectedView !== owner || !windowShortcutAvailable) return@invokeLater
            val loaded = result.getOrNull()
            if (loaded == null) {
                owner.timeline.showStatus("履歴を読み込めませんでした。保存先の権限を確認してください。")
                return@invokeLater
            }
            val saved = loaded.conversations.filterNot { store.isDeleted(it.id) }
            val legacy = ChatHistoryState.getInstance(project).list()
            val target = adjacentSavedChat(chatArchive.apply(availableChatEntries(openRecentEntries(), saved, legacy)), selected, reverse)
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
            valid = { !disposed && !project.isDisposed && selectedChatShowing && ticket == chatFocusGeneration &&
                selectedView?.composer?.panelShortcutAvailable == true },
            onChoose = { openRecentChat(it) },
        )
        recentChatsPopup = popup
        popup.show(event)
    }

    private fun openRecentChat(id: RecentChatId, allowArchived: Boolean = false, isCurrent: () -> Boolean = { true }) {
        if (disposed || project.isDisposed || (!isShowing && !selectedChatShowing) || !windowShortcutAvailable || !isCurrent()) return
        val inEditor = selectedView?.presentation?.inEditor == true
        val metadata = chatArchive.apply(RecentChatEntry(id, "", 0, null))
        if (metadata.archived && !allowArchived) return
        val open = sessions.snapshot().tabs.firstOrNull { tab -> views[tab.id]?.let { recentChatId(tab, it) == id } == true }
        if (open != null) {
            if (sessions.select(open.id)) showSelected(inEditor = inEditor)
            return
        }
        // The picker owns metadata only. A closed chat may have been saved/deleted while it was visible.
        val ticket = ++chatFocusGeneration
        val store = project.getService(ConversationHistory::class.java)
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || (!isShowing && !selectedChatShowing) || ticket != chatFocusGeneration || !windowShortcutAvailable ||
                !chatArchive.matches(metadata) || !isCurrent()) return@invokeLater
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

    private fun archiveChat(request: ChatArchiveRequest) {
        val entry = request.entry
        val store = project.getService(ConversationHistory::class.java)
        fun available(): Boolean = !disposed && !project.isDisposed && request.isCurrent() && chatArchive.matches(entry) &&
            !(entry.id is RecentChatId.Body && store.isDeleted(entry.id.id)) &&
            (entry.id !is RecentChatId.LegacyPrint || ChatHistoryState.getInstance(project).list().any { it.chatId == entry.id.id })
        fun target() = sessions.snapshot().tabs.firstOrNull { tab -> views[tab.id]?.let { recentChatId(tab, it) == entry.id } == true }
        if (!available()) return
        val originalTarget = target()
        if (!entry.archived) {
            if (request.fromSidebar && originalTarget != null &&
                (originalTarget.run != null || views[originalTarget.id]?.composer?.isRunning == true)) {
                if (com.intellij.openapi.ui.Messages.showYesNoDialog(project,
                        "この会話は実行中です。停止してアーカイブしますか？", "会話をアーカイブ",
                        "アーカイブ", "キャンセル", com.intellij.openapi.ui.Messages.getWarningIcon()) != com.intellij.openapi.ui.Messages.YES) return
                // A modal confirmation runs the event loop: selection, metadata and ownership may have changed.
                if (!available() || target()?.id != originalTarget.id || target()?.run != originalTarget.run) return
            }
            try {
                originalTarget?.let { views.getValue(it.id).controller.stopRun() }
            } catch (_: Exception) {
                selectedView?.timeline?.showStatus("この会話を停止できなかったため、アーカイブしませんでした。")
                return
            }
        }
        if (!chatArchive.set(entry, !entry.archived)) return
        if (!entry.archived && request.fromSidebar) {
            val properties = PropertiesComponent.getInstance(project)
            val pins = properties.getList("CursorAgent.pinnedChats").orEmpty()
            properties.setList("CursorAgent.pinnedChats", pins.filterNot { pinnedChatId(it) == entry.id })
            val selected = sessions.snapshot().selected
            val activeId = selectedView?.let { recentChatId(selected, it) }
            if (activeId != null && chatArchive.apply(openRecentEntries()).any { it.id == activeId && it.archived }) {
                openAfterArchive(archiveNeighbor(request.candidates, activeId), selected.id)
            }
        }
        allChatsSidebar?.refreshOpenEntries()
        historyMenuView?.refreshOpenEntries()
    }

    private fun archivePriorChats(request: ChatArchivePriorRequest) {
        val ticket = chatFocusGeneration
        val properties = PropertiesComponent.getInstance(project)
        fun current() = !disposed && !project.isDisposed && ticket == chatFocusGeneration && request.isCurrent() &&
            properties.getList("CursorAgent.pinnedChats").orEmpty().mapNotNull(::pinnedChatId).toSet() == request.pinned
        if (!current() || priorArchiveCandidates(request.candidates, request.anchor.updatedMs, request.pinned).isEmpty()) return
        val store = project.getService(ConversationHistory::class.java)
        // A closed row may have been saved/deleted since the menu opened. Re-read off EDT before stopping anything.
        store.load { result -> SwingUtilities.invokeLater {
            if (!current()) return@invokeLater
            val loaded = result.getOrNull()
            if (loaded == null || loaded.unreadable != 0) {
                selectedView?.timeline?.showStatus("履歴を確認できなかったため、一括アーカイブしませんでした。")
                return@invokeLater
            }
            val available = chatArchive.apply(availableChatEntries(openRecentEntries(),
                loaded.conversations.filterNot { store.isDeleted(it.id) }, ChatHistoryState.getInstance(project).list()))
            val anchor = available.firstOrNull { it.id == request.anchor.id } ?: return@invokeLater
            if (anchor.updatedMs != request.anchor.updatedMs || !chatArchive.matches(request.anchor)) return@invokeLater
            val requestedIds = request.candidates.map { it.id }.toSet()
            val candidates = priorArchiveCandidates(searchSidebarChats(available, request.query)
                .map { it.entry }.filter { it.id in requestedIds }, anchor.updatedMs, request.pinned)
            val now = System.currentTimeMillis()
            var failed = 0
            var archived = 0
            for (entry in candidates) {
                if (!current()) break
                if (!chatArchive.matches(entry) || entry.id is RecentChatId.Body && store.isDeleted(entry.id.id) ||
                    entry.id is RecentChatId.LegacyPrint && ChatHistoryState.getInstance(project).list().none { it.chatId == entry.id.id }) continue
                val target = sessions.snapshot().tabs.firstOrNull { tab -> views[tab.id]?.let { recentChatId(tab, it) == entry.id } == true }
                val view = target?.let { views[it.id] }
                val live = chatArchive.apply(openRecentEntries()).firstOrNull { it.id == entry.id }
                if (live != null && live.updatedMs != entry.updatedMs) continue
                try { view?.controller?.stopRun() } catch (_: Exception) { failed++; continue }
                if (!current()) break
                // Stop may synchronously finish/close a run. Never apply its result to a replacement owner/run.
                val after = target?.let { previous -> sessions.snapshot().tabs.firstOrNull { it.id == previous.id } }
                if (target != null && (views[target.id] !== view || after == null || after.run != null && after.run != target.run)) continue
                if (entry.id is RecentChatId.Body && store.isDeleted(entry.id.id) ||
                    entry.id is RecentChatId.LegacyPrint && ChatHistoryState.getInstance(project).list().none { it.chatId == entry.id.id }) continue
                if (chatArchive.set(entry, true, now)) archived++ else failed++
            }
            if (failed > 0 && !disposed && !project.isDisposed)
                selectedView?.timeline?.showStatus("${archived}件をアーカイブしました。${failed}件は停止または状態更新に失敗したため保持しました。")
            allChatsSidebar?.refreshOpenEntries()
            historyMenuView?.refreshOpenEntries()
        } }
    }

    private fun openAfterArchive(candidate: RecentChatEntry?, previousId: String) {
        val store = project.getService(ConversationHistory::class.java)
        val inEditor = selectedView?.presentation?.inEditor == true
        fun candidateAvailable() = candidate != null && chatArchive.matches(candidate) && !candidate.archived &&
            !(candidate.id is RecentChatId.Body && store.isDeleted(candidate.id.id)) &&
            (candidate.id !is RecentChatId.LegacyPrint || ChatHistoryState.getInstance(project).list().any { it.chatId == candidate.id.id })
        fun finish(conversation: Conversation? = null, legacyId: String? = null, existing: String? = null) {
            if (existing != null) sessions.select(existing)
            else if (conversation != null) sessions.open(conversation.providerId, conversationId = conversation.id, transport = conversation.transport)
            else sessions.open(legacyId)
            // Open the successor first; hiding the sole visible tab would otherwise create an extra empty tab.
            sessions.hide(previousId)
            showSelected(conversation, legacyId != null, inEditor = inEditor)
        }
        if (!candidateAvailable()) { finish(); return }
        val live = sessions.snapshot().tabs.firstOrNull { tab -> views[tab.id]?.let { recentChatId(tab, it) == candidate!!.id } == true }
        if (live != null) { finish(existing = live.id); return }
        val ticket = ++chatFocusGeneration
        val owner = selectedView
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || ticket != chatFocusGeneration || selectedView !== owner ||
                sessions.snapshot().selectedId != previousId || !isShowing || !allChatsSidebarVisible) return@invokeLater
            val conversation = (candidate?.id as? RecentChatId.Body)?.let { id -> result.getOrNull()?.conversations?.firstOrNull { it.id == id.id } }
            val legacy = (candidate?.id as? RecentChatId.LegacyPrint)?.id
            if (candidateAvailable() && (conversation != null || legacy != null)) finish(conversation, legacy)
            else {
                finish()
                selectedView?.timeline?.showStatus("次の会話を開けなかったため、新しい会話を表示しました。")
            }
        } }
    }

    private fun openSavedChat(conversation: Conversation?, legacyId: String?) {
        if (disposed || project.isDisposed) return
        val inEditor = selectedView?.presentation?.inEditor == true
        if (conversation != null) sessions.open(conversation.providerId, conversationId = conversation.id, transport = conversation.transport)
        else sessions.open(legacyId)
        showSelected(conversation, legacyId != null, inEditor = inEditor)
    }

    internal fun enterChat(command: AgentWindowCommand, selection: SelectionContext?, window: ToolWindow, focusOrigin: ChatFocusOrigin? = null) {
        if (!windowShortcutAvailable || window.isDisposed || window.project !== project) return
        val inEditor = selectedView?.presentation?.inEditor == true
        val snapshot = sessions.snapshot()
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val focused = isShowing && focus != null && SwingUtilities.isDescendingFrom(focus, this)
        val tabs = snapshot.visibleTabs.map { tab ->
            val view = views.getValue(tab.id)
            // Live composer state is authoritative: SessionTab.draft is only updated on send.
            val empty = view.composer.selection.mode == AgentMode.AGENT && reusableEmptyChat(tab, view)
            ChatEntryTab(tab.id, empty, view.composer.promptContext.draft.snapshot().selections.isNotEmpty(), view.lastShownNanos)
        }
        when (val entry = chatEntry(command, tabs, snapshot.selectedId, focused, window.isVisible, System.nanoTime())) {
            ChatEntry.Hide -> { cancelPendingChatFocus(); window.hide(null) }
            is ChatEntry.Focus -> {
                inputFocusReturn.remember(focusOrigin)
                if (entry.id == null) {
                    open()
                    selectedView?.composer?.modeSelector?.selectMode(AgentMode.AGENT)
                } else if (entry.id != snapshot.selectedId && sessions.select(entry.id)) {
                    showSelected(inEditor = inEditor)
                }
                val owner = selectedView ?: return
                if (entry.insertSelection && selection != null) owner.composer.promptContext.addSelection(selection)
                owner.lastShownNanos = System.nanoTime()
                if (owner.presentation.inEditor) {
                    owner.presentation.focus()
                    return
                }
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
        if (disposed || projectClosing || project.isDisposed || !selectedChatShowing) return
        openedChatsPopup?.takeUnless { it.isDisposed }?.let { it.cancel(); return }
        val snapshot = sessions.snapshot()
        val owner = selectedView
        val inEditor = owner?.presentation?.inEditor == true
        val entries = openedChatEntries(snapshot)
        val next = JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
            .setTitle("開いているチャット")
            .setAccessibleName("開いているチャット")
            .setNamerForFiltering { it.second }
            .setFilterAlwaysVisible(true)
            .setRenderer(SimpleListCellRenderer.create("") { it: Pair<String, String> -> it.second })
            .setSelectedValue(entries.first { it.first == snapshot.selectedId }, true)
            .setItemChosenCallback { entry ->
                if (!disposed && !projectClosing && !project.isDisposed && owner === selectedView && selectedChatShowing &&
                    sessions.select(entry.first)) showSelected(inEditor = inEditor)
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
        val closesAllTabs = sessions.snapshot().visibleTabs.all { it.id in ids }
        sessions.closeAll(ids).forEach { tab ->
            history.forget(tab.id)
            views.remove(tab.id)?.let { view ->
                view.controller.dispose()
                cards.remove(view.presentation.panel)
                Disposer.dispose(view.presentation)
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

    private fun open(chatId: String? = null, inEditor: Boolean = selectedView?.presentation?.inEditor == true) {
        if (disposed || projectClosing || project.isDisposed) return
        sessions.open(chatId)
        showSelected(inEditor = inEditor)
    }

    private fun reusableEmptyChat(tab: SessionTab, view: TabView): Boolean =
        tab.run == null && !view.composer.isRunning && !view.controller.hasQueuedPrompts &&
            !view.composer.isQueueEditing && view.controller.conversationSnapshot()?.turns?.isEmpty() == true &&
            view.composer.inputArea.text.isBlank() && view.composer.commands.selectedName == null &&
            view.composer.images?.hasUnsent != true

    private fun resetChat() {
        if (!shortcutAvailable(AgentPanelCommand.RESET_CHAT)) return
        val owner = selectedView ?: return
        val snapshot = sessions.snapshot()
        val inEditor = owner.presentation.inEditor
        val reset = chatReset(snapshot.visibleTabs.map { tab ->
            val view = views.getValue(tab.id)
            ChatResetTab(tab.id, reusableEmptyChat(tab, view),
                view.controller.conversationSnapshot()?.turns?.isNotEmpty() == true,
                view.composer.inputArea.text.isNotBlank() || view.composer.commands.selectedName != null ||
                    view.composer.images?.attachment != null, view.composer.selection.mode)
        }, snapshot.selectedId)
        if (reset.reuseId != null) {
            if (sessions.select(reset.reuseId)) showSelected(inEditor = inEditor)
            return
        }
        val draft = try { if (reset.copyDraft) owner.composer.captureDraft() else null } catch (_: IllegalStateException) {
            owner.timeline.showStatus("下書きの画像を保持できません。再添付してから新しい会話を開いてください。")
            return
        }
        var transferred = false
        try {
            sessions.replaceSelected()
            showSelected(inEditor = inEditor)
            val next = requireNotNull(selectedView).composer
            if (draft != null) {
                transferred = true
                next.restoreDraft(draft.copy(mode = reset.mode, model = next.selection.selectedModel))
            } else next.modeSelector.selectMode(reset.mode)
        } finally {
            if (!transferred) draft?.image?.let { project.getService(AgentProcessService::class.java).releaseImage(it) }
        }
    }

    private fun showSelected(saved: com.cursoragent.history.Conversation? = null, legacyOnly: Boolean = false, focus: Boolean = true, inEditor: Boolean = false) {
        if (disposed || projectClosing || project.isDisposed) return
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
            val editorActions = chatActions(tab.id)
            val ownedActions = AgentPanelActions(
                { command ->
                    val owner = views[tab.id]
                    sessions.snapshot().selectedId == tab.id && owner?.composer === composer &&
                        (!owner.presentation.inEditor || owner.panel.isShowing) && shortcutAvailable(command)
                },
                { command, event ->
                    if (event.project === project && sessions.snapshot().selectedId == tab.id && views[tab.id]?.composer === composer) performShortcut(command, event)
                },
            )
            val presentation = com.cursoragent.ui.editor.ChatEditorPresentation(
                project, panel, composer.inputArea,
                canMove = { !disposed && !projectClosing && composer.canMovePresentation },
                selectOwner = {
                    if (!disposed && !projectClosing && !changingPresentation && sessions.snapshot().selectedId != tab.id && sessions.select(tab.id)) showSelected(focus = false)
                },
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
                headerActions = { editorActions.titleActions + com.cursoragent.ui.header.ChatOptionsActionGroup(editorActions.gearActions) },
                shortcutContext = { sink -> if (!disposed && !projectClosing && views[tab.id]?.composer === composer) sink[AgentPanelActions.KEY] = ownedActions },
                contextShortcuts = registeredShortcuts.toList(),
            )
            composer.inputArea.setDisposedWith(presentation)
            panel.addHierarchyListener { event ->
                if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) SwingUtilities.invokeLater {
                    if (!disposed && !projectClosing && selectedView?.composer === composer) {
                        if (!selectedChatShowing) {
                            openedChatsPopup?.cancel()
                            recentChatsPopup?.dispose()
                            cancelPendingChatFocus()
                        }
                        history.refresh()
                    }
                }
            }
            cards.add(presentation.panel, tab.id)
            TabView(panel, composer, timeline, controller, presentation, editorActions)
        }
        views.forEach { (id, other) ->
            other.timeline.isActiveTab = id == tab.id
            if (id != tab.id) other.controller.pauseQueue()
        }
        (cards.layout as CardLayout).show(cards, tab.id)
        refreshStrip()
        recentVisits.visit(recentChatId(tab, view))
        view.lastShownNanos = System.nanoTime()
        if (inEditor) view.presentation.focusInEditor() else if (focus) view.presentation.focus()
        allChatsSidebar?.reload()
        history.refresh()
    }

    private fun refreshStrip() {
        val snapshot = sessions.snapshot()
        // Hidden owners retain their view/run, but must not leave a visible native editor tab behind.
        val wasChangingPresentation = changingPresentation
        changingPresentation = true
        try {
            snapshot.tabs.filterNot { it.visible }.forEach { tab ->
                views[tab.id]?.presentation?.takeIf { it.inEditor }?.returnToPanel(false, notify = false)
            }
        } finally { changingPresentation = wasChangingPresentation }
        strip.setTabs(snapshot.visibleTabs.map { SessionTabPresentation(it.id, it.title) }, snapshot.selectedId)
        allChatsSidebar?.refreshOpenEntries()
        historyMenuView?.refreshOpenEntries()
        snapshot.tabs.forEach { views[it.id]?.presentation?.updateTitle(it.title) }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        requestShortcutKeys.dispose()
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
        views.values.forEach { it.controller.dispose(); Disposer.dispose(it.presentation) }
        views.clear()
        cards.removeAll()
    }
}

/** Empty-draft review keys target one current permission; ordinary controls keep their own keys. */
internal fun toolReviewTarget(
    composer: ComposerPanel,
    timeline: ChatTimelinePanel,
    focus: Component?,
    accept: Boolean,
): AgentRequestCard? {
    if (!composer.toolReviewInputAvailable || focus == null ||
        !(SwingUtilities.isDescendingFrom(focus, composer.inputArea) || SwingUtilities.isDescendingFrom(focus, timeline)) ||
        accept && focus is AbstractButton) return null
    // Buttons keep their native activation; an empty input cannot choose a permission group.
    return timeline.pendingInput(focus)?.takeIf { it.isToolPermission && it.canRespond(accept) }
}

/** Position distinguishes equal titles without renaming a chat or using an index as identity. */
internal fun openedChatEntries(snapshot: SessionTabsSnapshot): List<Pair<String, String>> =
    snapshot.visibleTabs.mapIndexed { index, tab ->
        tab.id to "${index + 1}. ${tab.title}${if (tab.run != null) "（実行中）" else ""}"
    }

/** A modal confirmation can pump callbacks; freeze its targets before entering it. */
internal fun confirmCloseChats(
    snapshot: SessionTabsSnapshot,
    confirm: (count: Int, running: Int) -> Boolean,
    close: (List<String>) -> Unit,
) {
    val ids = snapshot.visibleTabs.map { it.id }
    if (confirm(ids.size, snapshot.visibleTabs.count { it.run != null })) close(ids)
}
