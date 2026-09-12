package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationHistory
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.PluginBrand
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.AgentRun
import com.cursoragent.service.AgentTransport
import com.cursoragent.service.CheckpointService
import com.cursoragent.service.RestorePolicy
import com.cursoragent.service.RestoreResult
import com.cursoragent.service.TurnSettings
import com.cursoragent.session.SessionRunToken
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.composer.mention.MentionResolver
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

class AgentUiController(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val sessions: SessionTabs,
    private val tabId: String,
    private val restored: Conversation? = null,
    private val legacyOnly: Boolean = false,
) {
    private var turnGeneration = 0L
    private var disposed = false
    private var activeRun: AgentRun? = null
    private var modelLoad: java.util.concurrent.Future<*>? = null
    private var activeToken: SessionRunToken? = null
    private val agentService = project.getService(AgentProcessService::class.java)
    private val checkpointService = project.getService(CheckpointService::class.java)
    private val history = project.getService(ConversationHistory::class.java)
    private var resumeAllowed = restored == null && !legacyOnly
    private var saveRevision = 0L
    var hasUnsavedBody = false
        private set
    private val recorder = ConversationRecorder(restored ?: Conversation(id = sessions.snapshot().tabs.first { it.id == tabId }.conversationId)) { value ->
        val revision = ++saveRevision
        hasUnsavedBody = true
        timeline.setSaveStatus("保存中…")
        history.save(value) { saved -> runOnEdt {
            if (revision == saveRevision) {
                hasUnsavedBody = !saved
                if (!disposed) timeline.setSaveStatus(if (saved) "保存済み" else "保存できませんでした。上限（100会話・各8 MiB）と保存先の空き容量・権限を確認してください。")
                else if (!saved && !project.isDisposed) com.cursoragent.notification.AgentNotificationService.notifyError(project, "閉じた会話の本文を保存できませんでした。保存先の容量・権限・保存上限を確認してください。")
            }
        } }
    }
    private val queue = PromptQueue(recorder.conversation.id)
    private var queueDialog: PromptQueueDialog? = null
    val hasQueuedPrompts: Boolean get() = queue.size > 0

    fun enqueuePrompt(text: String) {
        if (disposed || project.isDisposed || activeRun?.isActive != true || !isSelectedConversation()) return
        if (queue.add(text, composer.selection.mode, composer.selection.selectedModel)) {
            composer.clearInput()
            refreshQueue()
        }
    }

    private fun isSelectedConversation(): Boolean {
        val selected = sessions.snapshot().selected
        return selected.id == tabId && selected.conversationId == queue.conversationId
    }

    fun pauseQueue() { queue.pause(); refreshQueue() }
    private fun refreshQueue() { composer.showQueueState(queue.size, queue.paused) }

    fun showQueue() {
        if (disposed || project.isDisposed || !isSelectedConversation() || queueDialog != null) return
        pauseQueue()
        val dialog = PromptQueueDialog(project, queue,
            isCurrent = { !disposed && !project.isDisposed && isSelectedConversation() },
            onChanged = ::refreshQueue,
            onResume = {
                if (!disposed && !project.isDisposed && isSelectedConversation()) {
                    queue.resume()
                    refreshQueue()
                    scheduleNextQueuedPrompt()
                }
            },
        )
        queueDialog = dialog
        dialog.show()
        if (queueDialog === dialog) queueDialog = null
    }

    private fun scheduleNextQueuedPrompt() {
        val ticket = queue.ticket(turnGeneration) ?: return
        SwingUtilities.invokeLater {
            val ownerIsIdle = !disposed && !project.isDisposed && activeRun == null && isSelectedConversation()
            queue.dispatch(ticket, turnGeneration, ownerIsIdle) { startPrompt(it.text, it) }
            if (!disposed) refreshQueue()
        }
    }

    private val promptContextBuilder = PromptContextBuilder(project, MentionResolver(project))
    private val turnListenerFactory = AgentTurnListenerFactory(
        project = project,
        timeline = timeline,
        composer = composer,
        recorder = recorder,
        onRunFinished = ::finishRun,
    )
    init {
        checkpointService.pruneExpired()
        loadModels()
    }

    private fun loadModels() {
        modelLoad = ApplicationManager.getApplication().executeOnPooledThread {
            val models = agentService.listModels()
            runOnEdt { if (!disposed && !project.isDisposed && transportState().first == AgentTransport.PRINT) composer.modelSelector.setModels(models) }
        }
    }

    fun transportState(): Pair<AgentTransport, Boolean> {
        val tab = sessions.snapshot().tabs.firstOrNull { it.id == tabId }
        return (tab?.transport ?: AgentTransport.PRINT) to (tab == null || tab.transportLocked || tab.chatId != null)
    }

    fun selectTransport(transport: AgentTransport) {
        if (disposed || !sessions.selectTransport(tabId, transport)) return
        if (transport == AgentTransport.ACP) {
            modelLoad?.cancel(false)
            composer.useAcp()
            timeline.showStatus("ACPを選択しました。初回は接続先の既定モデルを使い、確定後に一覧から選べます。標準設定でも即時編集が起こり得ます。")
        } else {
            composer.usePrint()
            loadModels()
        }
    }

    fun dispose() {
        recorder.finish("interrupted")
        disposed = true
        queue.clear()
        queueDialog?.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE)
        queueDialog = null
        modelLoad?.cancel(true)
        modelLoad = null
        turnGeneration++
        activeToken?.let(sessions::finishTurn)
        activeRun?.detachListener()
        activeRun?.stop()
        activeRun = null
        agentService.closeSession(tabId)
    }

    fun showResumeAvailability() {
        val saved = restored ?: return
        val root = project.basePath
        val mode = AgentSettingsState.getInstance().worktreeMode
        composer.setInputEnabled(false)
        timeline.showStatus("再開できる作業場所か確認中…")
        ApplicationManager.getApplication().executeOnPooledThread {
            val allowed = saved.canResume(root, mode)
            runOnEdt {
                if (!disposed && !project.isDisposed) {
                    resumeAllowed = allowed
                    composer.setInputEnabled(allowed)
                    timeline.showStatus(if (allowed) "次の送信でprintセッションの再開を試みます。過去のRevertは利用できません。" else "保存本文の閲覧のみです。Agentの再開には新しい会話を開始してください。")
                }
            }
        }
    }

    fun sendPrompt(userText: String) {
        if (activeRun != null || userText.isBlank()) return
        pauseQueue()
        startPrompt(userText)
    }

    private fun startPrompt(userText: String, queued: QueuedPrompt? = null): Boolean {
        if (disposed || project.isDisposed || legacyOnly || userText.isBlank() || activeRun != null) return false

        val shared = AgentSettingsState.getInstance()
        val tab = sessions.snapshot().tabs.firstOrNull { it.id == tabId } ?: return false
        if (queued != null && tab.chatId.isNullOrBlank()) {
            timeline.showStatus("会話の継続IDを取得できないため予約送信を一時停止しました。新しい会話への自動送信は行いません。")
            return false
        }
        if (!resumeAllowed) {
            timeline.showStatus("保存本文の閲覧のみです。この接続・作業場所からAgentを再開できないため、新しい会話を開始してください。")
            return false
        }
        val settings = TurnSettings(
            shared.agentExecutablePath, queued?.model ?: composer.selection.selectedModel, queued?.mode ?: composer.selection.mode,
            shared.permissionMode, shared.sandboxMode,
        )
        val workspace = agentService.captureWorkspace(tab.chatId, shared.worktreeMode)
        agentService.settingsUnavailableReason(tab.transport, settings, workspace.mode)?.let { reason ->
            timeline.showStatus(reason)
            return false
        }
        val generation = turnGeneration + 1
        sessions.updateComposer(tabId, settings.mode, settings.model, userText, userText.length)
        val sessionTurn = sessions.beginTurn(tabId) ?: return false
        activeToken = sessionTurn.token
        recorder.conversation = recorder.conversation.copy(transport = tab.transport)
        recorder.begin(sessionTurn.token.turnId, userText)
        lateinit var run: AgentRun
        var preparationFailure = RestorePolicy.BUSY
        val turn = try {
            agentService.prepareTurn(workspace, settings) {
                val usageTicket = composer.contextUsage.beginTurn()
                turnListenerFactory.create(
                    usageTicket,
                    isCurrent = { !disposed && turnGeneration == generation && sessions.accepts(sessionTurn.token) },
                    onSession = { id -> sessions.bindChat(sessionTurn.token, id) },
                    isStopped = { run.wasStopped },
                    restoreTarget = { workspace.restoreTarget },
                )
            }
        } catch (_: Exception) {
            preparationFailure = "送信の準備を開始できませんでした。"
            null
        }
        if (turn == null) {
            recorder.finish("failed")
            sessions.finishTurn(sessionTurn.token)
            activeToken = null
            timeline.showStatus(preparationFailure)
            return false
        }
        run = turn.run
        activeRun = run
        turnGeneration = generation
        if (queued == null) composer.clearInput()
        else sessions.updateComposer(tabId, composer.selection.mode, composer.selection.selectedModel, composer.inputArea.text, composer.inputArea.editor?.caretModel?.offset ?: 0)
        composer.setInputEnabled(true)
        composer.setRunning(true)

        timeline.clearStatus()
        timeline.finalizeAssistantMessage()
        val userBubble = timeline.addUserMessage(userText)
        timeline.showStatus("送信を準備中…")

        val edtContext = try {
            promptContextBuilder.buildEdtContext(userText)
        } catch (error: Exception) {
            run.reportError("送信の準備に失敗しました: ${error.message}")
            run.complete(-1)
            turn.preparation.close()
            return true
        }

        try {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    if (!run.isActive) return@executeOnPooledThread
                    val commandTarget = workspace.commandTarget
                    check(restored == null || restored.canResume(commandTarget.rootPath, workspace.mode)) {
                        "保存時と現在の作業場所が異なるため会話を再開できません。新しい会話を開始してください。"
                    }
                    runOnEdt { if (!disposed && sessions.accepts(sessionTurn.token)) recorder.provenance(commandTarget.rootPath, workspace.mode) }
                    val target = workspace.restoreTarget
                    val checkpointId = checkpointService.createSnapshot(userText, workspace.resumeId, target)
                    val checkpointReason = if (checkpointId == null) {
                        checkpointService.unavailableReason(target) ?: RestorePolicy.SNAPSHOT_UNAVAILABLE
                    } else null
                    if (!run.isActive) return@executeOnPooledThread
                    val backgroundContext = promptContextBuilder.buildBackgroundContext(userText)
                    val fullContext = listOfNotNull(edtContext, backgroundContext)
                        .joinToString("\n\n")
                        .takeIf { it.isNotBlank() }
                    val fullPrompt = promptContextBuilder.assemble(fullContext, userText)

                    if (!run.isActive) return@executeOnPooledThread
                    runOnEdt {
                        if (disposed || project.isDisposed || turnGeneration != generation || run.wasStopped) return@runOnEdt
                        userBubble.setCheckpointAvailable(checkpointId != null, checkpointReason)
                        if (checkpointId != null) {
                            userBubble.onRollbackRequested = { requestRollback(checkpointId) }
                        }
                        timeline.showStatus("実行中…")
                    }

                    // Context/checkpoint preparation may take time; do not trust the earlier path check.
                    check(restored == null || restored.canResume(commandTarget.rootPath, workspace.mode)) {
                        "準備中に作業場所が変わったため会話を再開しません。"
                    }
                    agentService.sendPrompt(fullPrompt, turn, tabId, sessionTurn.transport)
                } catch (error: Exception) {
                    run.reportError("送信の準備に失敗しました: ${error.message}")
                    run.complete(-1)
                } finally {
                    turn.preparation.close()
                }
            }
        } catch (_: Exception) {
            run.reportError("送信の準備を開始できませんでした。")
            run.complete(-1)
            turn.preparation.close()
        }
        return true
    }

    private fun requestRollback(checkpointId: String) {
        pauseQueue()
        val confirmed = Messages.showYesNoDialog(
            project,
            "このプロンプトを送信する直前の状態までファイルを復元します。この操作は取り消せません。続行しますか？",
            "チェックポイントへロールバック",
            Messages.getWarningIcon(),
        ) == Messages.YES
        if (!confirmed) return

        val reservation = agentService.tryRestore()
        if (reservation == null) {
            Messages.showInfoMessage(project, agentService.restoreUnavailableReason(), PluginBrand.NAME)
            return
        }
        val generation = turnGeneration
        composer.setInputEnabled(false)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                checkpointService.restoreResult(checkpointId)
            } catch (_: Exception) {
                RestoreResult(RestorePolicy.RESTORE_FAILED)
            } finally {
                reservation.close()
            }
            runOnEdt {
                if (disposed || project.isDisposed) return@runOnEdt
                if (generation != turnGeneration) return@runOnEdt
                composer.setInputEnabled(true)
                if (result.restored) timeline.showStatus("チェックポイントへ復元しました")
                else Messages.showErrorDialog(project, result.rejectionReason!!, PluginBrand.NAME)
            }
        }
    }

    fun stopRun() {
        pauseQueue()
        composer.contextUsage.reset()
        activeRun?.stop()
    }

    private fun finishRun(successful: Boolean) {
        if (!successful) queue.pause()
        activeToken?.let(sessions::finishTurn)
        activeToken = null
        activeRun = null
        composer.setInputEnabled(true)
        composer.setRunning(false)
        refreshQueue()
        if (successful) scheduleNextQueuedPrompt()
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
