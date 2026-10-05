package com.cursoragent.ui.composer

import com.cursoragent.service.AgentEvent
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.RoundedSurface
import com.cursoragent.ui.composer.mention.MentionPopupController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.messages.MessageBusConnection
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JPanel

class ComposerPanel(private val project: Project, newPrintConversation: Boolean = true) : JPanel(BorderLayout()), com.intellij.openapi.actionSystem.UiDataProvider {
    var onSend: (String) -> Unit = {}
    var onStop: () -> Unit = {}
    var onEnqueue: (String) -> Unit = {}
    var onShowQueue: () -> Unit = {}
    internal var onFocusQueue: (Boolean) -> Boolean = { false }
    internal var onSubmitQueueEdit: () -> Unit = {}
    internal var onCancelQueueEdit: () -> Unit = {}
    internal var isQueueEditing = false
        private set
    var isRunning = false
        private set
    private var acp = false
    internal var images: com.cursoragent.ui.composer.image.ImageDraft? = null
        private set
    private val imageContainer = JPanel(BorderLayout()).apply { isOpaque = false }
    internal fun installImages(draft: com.cursoragent.ui.composer.image.ImageDraft): com.cursoragent.ui.composer.image.ImageAttachmentPanel {
        images = draft
        val panel = com.cursoragent.ui.composer.image.ImageAttachmentPanel(draft)
        imageContainer.add(panel)
        inputArea.onImageTransfer = { value -> draft.import { com.cursoragent.ui.composer.image.ImageTransfer.read(value) }; true }
        return panel
    }
    private val sendShortcut = PromptSendShortcut(::submit)
    private var settingsConnection: MessageBusConnection? = null
    private var sendLabel = "送信（Enter）"

    val contextUsage = com.cursoragent.ui.composer.context.ContextUsageView()

    val inputArea = GrowingPromptField(project)

    val commands = com.cursoragent.ui.composer.command.CommandInputPanel(project, inputArea)

    val promptContext = com.cursoragent.ui.composer.context.PromptContextPanel(project)
    private val mentionPopupController = MentionPopupController(project, inputArea, promptContext::addMention)

    internal val canCycleMode: Boolean
        get() = modeSelector.isEnabled && (!acp || !isRunning)

    internal fun cycleMode() { if (canCycleMode) modeSelector.cycleMode() }

    internal val panelShortcutAvailable: Boolean
        get() = !inputArea.isComposing && !commands.popupOpen && !mentionPopupController.popupOpen

    /** The root additionally checks project lifetime, panel visibility and child popups. */
    private fun inputShortcutAvailable(focus: java.awt.Component?): Boolean =
        inputArea.isEnabled && panelShortcutAvailable && focus != null &&
            javax.swing.SwingUtilities.isDescendingFrom(focus, inputArea)

    internal fun canResetFrom(focus: java.awt.Component?): Boolean =
        inputShortcutAvailable(focus) && images?.importing != true

    internal fun canUnfocusFrom(focus: java.awt.Component?): Boolean =
        inputShortcutAvailable(focus) && !isQueueEditing

    internal val toolReviewInputAvailable: Boolean
        get() = inputArea.isEnabled && panelShortcutAvailable && !isQueueEditing && inputArea.text.isBlank() &&
            commands.selectedName == null && images?.attachment == null && images?.importing != true

    internal val canSubmitInitial: Boolean
        get() = !isRunning && !isQueueEditing && inputArea.isEnabled && panelShortcutAvailable &&
            inputArea.text.isNotBlank() && images?.importing != true &&
            !com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().isChildPopupFocused(this)

    /** The root also requires a loaded empty conversation and no preparing/executing turn. */
    internal fun submitInitial(event: com.intellij.openapi.actionSystem.AnActionEvent) {
        if (canSubmitInitial) sendShortcut.actionPerformed(event)
    }

    internal fun installInputShortcuts(parent: com.intellij.openapi.Disposable) {
        // The IDE collects local matches from the nearest component before checking availability.
        // Keep both Escape operations here so a disabled queue action cannot hide the parent action.
        // Pending acceptance and initial submit share this level with the configured send action.
        listOf(com.cursoragent.actions.AgentQueueCommand.RETURN_TO_INPUT.actionId,
            com.cursoragent.actions.AgentPanelCommand.UNFOCUS_INPUT.actionId,
            com.cursoragent.actions.AgentPanelCommand.ACCEPT_PENDING.actionId,
            com.cursoragent.actions.AgentPanelCommand.APPROVE_TOOL.actionId,
            com.cursoragent.actions.AgentPanelCommand.SKIP_TOOL.actionId,
            com.cursoragent.actions.AgentPanelCommand.SUBMIT_INITIAL.actionId).forEach { id ->
            com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(id)?.let { action ->
                action.registerCustomShortcutSet(action.shortcutSet, inputArea, parent)
            }
        }
    }

    internal val queueEditAvailable: Boolean
        get() = isQueueEditing && inputArea.isEnabled && panelShortcutAvailable &&
            !com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().isChildPopupFocused(this)

    private val queueEditActions = com.cursoragent.actions.AgentQueueActions(
        available = { command ->
            command == com.cursoragent.actions.AgentQueueCommand.RETURN_TO_INPUT && isShowing && queueEditAvailable &&
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.let {
                    javax.swing.SwingUtilities.isDescendingFrom(it, inputArea)
                } == true
        },
        perform = { _, _ -> if (queueEditAvailable) onCancelQueueEdit() },
    )

    override fun uiDataSnapshot(sink: com.intellij.openapi.actionSystem.DataSink) {
        if (queueEditActions.available(com.cursoragent.actions.AgentQueueCommand.RETURN_TO_INPUT)) {
            sink[com.cursoragent.actions.AgentQueueActions.KEY] = queueEditActions
        }
    }

    private val sendButton = SelectorButton().apply {
        text = "↑"
        horizontalAlignment = javax.swing.SwingConstants.CENTER
        toolTipText = "送信（Enter）"
        preferredSize = JBUI.size(24, 24)
        // Reserve 18px for the icon instead of the selector's 12px text area.
        border = JBUI.Borders.empty(3)
        font = font.deriveFont(font.size2D * 4f / 3f)
        isBorderPainted = false
        isContentAreaFilled = false
        margin = JBUI.emptyInsets()
        accessibleContext.accessibleName = "送信（Enter）"
    }

    private val enqueueButton = javax.swing.JButton("予約に追加").apply {
        isVisible = false
        toolTipText = "入力を次のターンに予約します。mode/modelと明示選択・添付は登録時に固定。自動context・参照内容と実行設定は送信開始時です。"
        addActionListener { if (isRunning && !isQueueEditing) submit() }
    }
    private val queueButton = javax.swing.JButton().apply {
        isVisible = false
        toolTipText = "予約を一時停止して一覧・編集・削除・順序を確認します。"
        addActionListener { onShowQueue() }
    }
    private val queueContainer = JPanel(BorderLayout()).apply { isOpaque = false; isVisible = false }
    private val queueEditSubmit = javax.swing.JButton().apply {
        addActionListener { if (queueEditAvailable) onSubmitQueueEdit() }
    }
    private val queueEditBanner = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
        isOpaque = false
        isVisible = false
        add(javax.swing.JLabel("予約した入力を編集中"))
        add(queueEditSubmit)
        add(javax.swing.JButton("キャンセル").apply { addActionListener { if (queueEditAvailable) onCancelQueueEdit() } })
    }

    internal fun showQueueEdit(editing: Boolean) {
        isQueueEditing = editing
        queueEditBanner.isVisible = editing
        queueButton.isEnabled = !editing
        enqueueButton.isVisible = isRunning && !editing
        accessoryPanel.isVisible = editing || isRunning || queueButton.isVisible
        updateSendLabel()
        revalidate()
        repaint()
    }

    /** Capturing retains an image lease; restoring consumes it, disposal must release an unused one. */
    internal fun captureDraft() = ComposerDraft(
        inputArea.text, inputArea.editor?.caretModel?.caretsAndSelections,
        selection.mode, selection.selectedModel, promptContext.draft.snapshot(), commands.selectedName,
        images?.retain(), images?.preview,
    )

    internal fun restoreDraft(draft: ComposerDraft) {
        images?.restore(draft.image, draft.imagePreview)
        promptContext.restore(draft.context)
        commands.clearSelection()
        draft.command?.let(commands::restoreSelection)
        modeSelector.selectMode(draft.mode)
        modelSelector.restoreSelection(draft.model)
        inputArea.replaceDraftText(draft.text, draft.carets)
    }

    internal fun installQueueList(list: com.cursoragent.ui.PromptQueueList) {
        list.fixedCellHeight = JBUI.scale(28)
        list.visibleRowCount = 3
        queueContainer.add(com.intellij.ui.components.JBScrollPane(list).apply {
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            minimumSize = JBUI.size(0, 28)
        })
    }

    fun showQueueState(count: Int, paused: Boolean) {
        queueButton.text = "予約 $count 件" + if (paused) "（停止中）" else ""
        queueButton.isVisible = count > 0
        queueContainer.isVisible = count > 0
        accessoryPanel.isVisible = isQueueEditing || isRunning || count > 0
        revalidate()
        repaint()
    }

    // Detached selector state: application settings supply defaults only for a new tab.
    val selection = AgentSettingsState.getInstance().composerSelection(newPrintConversation)
    val modeSelector = ModeSelector(selection)
    val modelSelector = ModelSelector(selection)

    /** Transient queue actions live above the input to preserve the compact selector row. */
    val accessoryPanel = JPanel(BorderLayout()).apply {
        isVisible = false
        isOpaque = false
    }

    init {
        border = JBUI.Borders.empty(5, 12, 8, 12)
        isOpaque = false
        inputArea.onSendKeyReleased = sendShortcut::release
        inputArea.onQueueNavigate = { reverse ->
            !isQueueEditing && panelShortcutAvailable && !com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().isChildPopupFocused(this) &&
                commands.selectedName == null && images?.hasUnsent != true &&
                onFocusQueue(reverse)
        }
        inputArea.clipboardContextAvailable = { !project.isDisposed && !commands.popupOpen && !mentionPopupController.popupOpen }
        inputArea.onClipboardContext = promptContext::addClipboard

        val inputWrapper = RoundedSurface(AgentUiColors.composerBackground).apply {
            border = AgentUiColors.RoundedBorder()
            add(inputArea, BorderLayout.CENTER)
        }

        accessoryPanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(queueButton)
            add(enqueueButton)
        })
        accessoryPanel.add(queueContainer, BorderLayout.NORTH)
        mentionPopupController.install()
        promptContext.onAddMention = { mentionPopupController.showPopup() }
        inputWrapper.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(imageContainer, BorderLayout.NORTH)
                add(commands, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(promptContext, BorderLayout.CENTER)
        }, BorderLayout.NORTH)

        refreshSendShortcut()

        sendButton.addActionListener { if (isRunning) onStop() else submit() }

        val controls = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 6, 6)
            val selectors = JPanel(SelectorRowLayout()).apply {
                isOpaque = false
                add(modeSelector)
                add(modelSelector)
            }
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(contextUsage.button)
                add(sendButton)
            }
            add(selectors, BorderLayout.CENTER)
            add(actions, BorderLayout.EAST)
        }
        inputWrapper.add(controls, BorderLayout.SOUTH)
        accessoryPanel.add(queueEditBanner, BorderLayout.SOUTH)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(accessoryPanel, BorderLayout.NORTH)
            add(contextUsage.panel, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(inputWrapper, BorderLayout.CENTER)
    }

    override fun addNotify() {
        super.addNotify()
        settingsConnection?.disconnect()
        settingsConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(AgentSettingsState.SEND_KEY_CHANGED, Runnable { refreshSendShortcut() })
        }
        refreshSendShortcut()
    }

    override fun removeNotify() {
        settingsConnection?.disconnect()
        settingsConnection = null
        super.removeNotify()
    }

    private fun refreshSendShortcut() {
        val mode = AgentSettingsState.getInstance().sendKeyMode
        sendShortcut.install(inputArea, mode, SystemInfo.isMac)
        sendLabel = "送信（${mode.keyLabel(SystemInfo.isMac)}）"
        updateSendLabel()
    }

    private fun updateSendLabel() {
        sendButton.toolTipText = if (isRunning) "停止" else sendLabel
        sendButton.accessibleContext.accessibleName = sendButton.toolTipText
        queueEditSubmit.text = if (isRunning) "予約を更新" else "送信"
        queueEditSubmit.toolTipText = if (isRunning) sendLabel.replace("送信", "予約を更新") else sendLabel
        enqueueButton.accessibleContext.accessibleName = sendLabel.replace("送信", "予約に追加")
        enqueueButton.toolTipText = "${enqueueButton.accessibleContext.accessibleName}。入力を次のターンに予約します。mode/modelと明示選択・添付は登録時に固定。自動context・参照内容と実行設定は送信開始時です。"
    }

    fun useAcp() {
        acp = true
        modelSelector.waitForAcp()
    }

    fun usePrint() { acp = false }

    fun showAcpConfiguration(state: AgentEvent.Configuration) {
        val mode = AgentMode.entries.firstOrNull { it.name.lowercase() == state.mode } ?: return
        val editingMode = selection.mode
        val editingModel = selection.selectedModel
        modeSelector.selectMode(if (isQueueEditing) editingMode else mode)
        modelSelector.setAcpModels(state.models, state.model)
        if (isQueueEditing) modelSelector.restoreSelection(editingModel)
        modeSelector.isEnabled = !isRunning
        modelSelector.isEnabled = !isRunning && state.models.isNotEmpty()
    }

    fun setInputEnabled(enabled: Boolean) {
        // sendButton is intentionally left enabled here -- setRunning() repurposes
        // it as a Stop button while a turn is in flight, so it must stay clickable.
        inputArea.isEnabled = enabled
        modeSelector.isEnabled = enabled
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        enqueueButton.isVisible = running && !isQueueEditing
        accessoryPanel.isVisible = isQueueEditing || running || queueButton.isVisible
        if (acp) {
            modeSelector.isEnabled = !running
            modelSelector.isEnabled = !running && selection.selectedModel.isNotEmpty()
        }
        sendButton.text = if (running) "" else "↑"
        sendButton.icon = if (running) StopIcon else null
        updateSendLabel()
    }

    fun clearInput() {
        images?.clear()
        inputArea.text = ""
        promptContext.clearExplicit()
        commands.clearSelection()
    }

    internal val canMovePresentation: Boolean
        get() = !inputArea.isComposing && !commands.popupOpen && !mentionPopupController.popupOpen &&
            !com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().isChildPopupFocused(this)

    internal val canToggleEditorWithShortcut: Boolean
        get() = canMovePresentation && selection.mode != AgentMode.ASK

    fun inputText(): String = if (commands.selectedName == null) inputArea.text.trim() else inputArea.text

    private fun submit() {
        if (!inputArea.isEnabled || !panelShortcutAvailable ||
            com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().isChildPopupFocused(this)) return
        if (isQueueEditing) { if (queueEditAvailable) onSubmitQueueEdit(); return }
        val text = inputText()
        if (images?.importing == true) return
        if (text.isNotEmpty() || commands.selectedName != null || images?.attachment != null) {
            if (isRunning) onEnqueue(text) else onSend(text)
        }
    }
}

internal data class ComposerDraft(
    val text: String,
    val carets: List<com.intellij.openapi.editor.CaretState>?,
    val mode: AgentMode,
    val model: String,
    val context: com.cursoragent.ui.composer.context.PromptContextSnapshot,
    val command: String?,
    val image: com.cursoragent.ui.composer.image.ImageAttachmentStore.ImageAttachment?,
    val imagePreview: java.awt.image.BufferedImage? = null,
)
