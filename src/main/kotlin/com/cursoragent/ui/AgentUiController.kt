package com.cursoragent.ui

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
import com.cursoragent.settings.ChatHistoryState
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
) {
    private var turnGeneration = 0L
    private var disposed = false
    private var activeRun: AgentRun? = null
    private var modelLoad: java.util.concurrent.Future<*>? = null
    private var activeToken: SessionRunToken? = null
    private val agentService = project.getService(AgentProcessService::class.java)
    private val checkpointService = project.getService(CheckpointService::class.java)
    private val chatHistoryState = ChatHistoryState.getInstance(project)
    private val promptContextBuilder = PromptContextBuilder(project, MentionResolver(project))
    private val turnListenerFactory = AgentTurnListenerFactory(
        project = project,
        timeline = timeline,
        composer = composer,
        chatHistoryState = chatHistoryState,
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
        disposed = true
        modelLoad?.cancel(true)
        modelLoad = null
        turnGeneration++
        activeToken?.let(sessions::finishTurn)
        activeRun?.detachListener()
        activeRun?.stop()
        activeRun = null
        agentService.closeSession(tabId)
    }

    fun sendPrompt(userText: String) {
        if (disposed || userText.isBlank() || activeRun != null) return

        val shared = AgentSettingsState.getInstance()
        val tab = sessions.snapshot().tabs.firstOrNull { it.id == tabId } ?: return
        val settings = TurnSettings(
            shared.agentExecutablePath, composer.selection.selectedModel, composer.selection.mode,
            shared.permissionMode, shared.sandboxMode,
        )
        val workspace = agentService.captureWorkspace(tab.chatId, shared.worktreeMode)
        agentService.settingsUnavailableReason(tab.transport, settings, workspace.mode)?.let { reason ->
            timeline.showStatus(reason)
            return
        }
        val generation = turnGeneration + 1
        sessions.updateComposer(tabId, settings.mode, settings.model, userText, userText.length)
        val sessionTurn = sessions.beginTurn(tabId) ?: return
        activeToken = sessionTurn.token
        lateinit var run: AgentRun
        val turn = agentService.prepareTurn(workspace, settings) {
            val usageTicket = composer.contextUsage.beginTurn()
            turnListenerFactory.create(
                userText,
                usageTicket,
                isCurrent = { !disposed && turnGeneration == generation && sessions.accepts(sessionTurn.token) },
                onSession = { id -> sessions.bindChat(sessionTurn.token, id) },
                isStopped = { run.wasStopped },
                restoreTarget = { workspace.restoreTarget },
            )
        }
        if (turn == null) {
            sessions.finishTurn(sessionTurn.token)
            activeToken = null
            timeline.showStatus(RestorePolicy.BUSY)
            return
        }
        run = turn.run
        activeRun = run
        turnGeneration = generation
        composer.clearInput()
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
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (!run.isActive) return@executeOnPooledThread
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

                agentService.sendPrompt(fullPrompt, turn, tabId, sessionTurn.transport)
            } catch (error: Exception) {
                run.reportError("送信の準備に失敗しました: ${error.message}")
                run.complete(-1)
            } finally {
                turn.preparation.close()
            }
        }
    }

    private fun requestRollback(checkpointId: String) {
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
        composer.contextUsage.reset()
        activeRun?.stop()
    }

    private fun finishRun() {
        activeToken?.let(sessions::finishTurn)
        activeToken = null
        activeRun = null
        composer.setInputEnabled(true)
        composer.setRunning(false)
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
