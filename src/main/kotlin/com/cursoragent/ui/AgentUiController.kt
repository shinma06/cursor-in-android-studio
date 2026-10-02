package com.cursoragent.ui

import com.cursoragent.PluginBrand
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.composer.mention.MentionResolver
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.CheckpointService
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

class AgentUiController(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val header: AgentHeaderBar,
) {
    private val agentService = project.getService(AgentProcessService::class.java)
    @Volatile
    private var preparationGeneration = 0L

    private val checkpointService = project.getService(CheckpointService::class.java)
    private val chatHistoryState = ChatHistoryState.getInstance(project)
    private val promptContextBuilder = PromptContextBuilder(project, MentionResolver(project))
    private val turnListenerFactory = AgentTurnListenerFactory(
        project = project,
        timeline = timeline,
        composer = composer,
        header = header,
        chatHistoryState = chatHistoryState,
        onRunFinished = ::finishRun,
    )
    private val pastChatsCoordinator = PastChatsCoordinator(
        project = project,
        timeline = timeline,
        header = header,
        agentService = agentService,
        chatHistoryState = chatHistoryState,
        onChatResumed = { composer.contextUsage.reset() },
    )

    init {
        checkpointService.pruneExpired()
        loadModels()
        header.onPastChatsClicked = { pastChatsCoordinator.showPopup() }
    }

    private fun loadModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val models = agentService.listModels()
            runOnEdt { composer.modelSelector.setModels(models) }
        }
    }

    fun startNewChat() {
        preparationGeneration++
        composer.contextUsage.reset()
        agentService.startNewChat()
        timeline.clearTimeline()
        header.setSessionStatus("Ready")
    }

    fun sendPrompt(userText: String) {
        if (userText.isBlank() || project.isDisposed) return
        val generation = ++preparationGeneration

        val usageTicket = composer.contextUsage.beginTurn()
        composer.clearInput()
        composer.setInputEnabled(false)
        composer.setRunning(true)

        val userBubble = timeline.addUserMessage(userText)
        header.setSessionStatus("Preparing…")

        val edtContext = promptContextBuilder.buildEdtContext(userText)

        ApplicationManager.getApplication().executeOnPooledThread {
            val listener = turnListenerFactory.create(userText, usageTicket)
            try {
                // Rabbit saves VFS bytes asynchronously; Git and the CLI read disk.
                ManagingFS.getInstance().flushPendingUpdates()
                if (project.isDisposed || generation != preparationGeneration) return@executeOnPooledThread
                val checkpointId = checkpointService.createSnapshot(userText, agentService.currentChatId())
                val backgroundContext = promptContextBuilder.buildBackgroundContext(userText)
                val fullContext = listOfNotNull(edtContext, backgroundContext)
                    .joinToString("\n\n")
                    .takeIf { it.isNotBlank() }
                val fullPrompt = promptContextBuilder.assemble(fullContext, userText)

                runOnEdt {
                    if (project.isDisposed || generation != preparationGeneration) return@runOnEdt
                    userBubble.setCheckpointAvailable(checkpointId != null)
                    if (checkpointId != null) {
                        userBubble.onRollbackRequested = { requestRollback(checkpointId) }
                    }
                    header.setSessionStatus("Running...")
                }

                if (!project.isDisposed && generation == preparationGeneration) {
                    agentService.sendPrompt(fullPrompt, listener)
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                if (!project.isDisposed && generation == preparationGeneration) {
                    listener.onError("保存内容の確認または送信準備に失敗しました")
                }
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

        val restored = try {
            var result = false
            // Modal execution preserves the existing exclusive restore interaction,
            // while the disk barrier and Git work run outside EDT/write actions.
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                Runnable {
                    ManagingFS.getInstance().flushPendingUpdates()
                    if (!project.isDisposed) result = checkpointService.restore(checkpointId)
                },
                "チェックポイントを復元中",
                false,
                project,
            )
            result
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (project.isDisposed) return
        if (restored) {
            timeline.showStatus("Rolled back to checkpoint")
        } else {
            Messages.showErrorDialog(project, "ロールバックに失敗しました", PluginBrand.NAME)
        }
    }

    fun stopRun() {
        preparationGeneration++
        composer.contextUsage.reset()
        agentService.killActiveProcess()
        finishRun()
    }

    private fun finishRun() {
        composer.setInputEnabled(true)
        composer.setRunning(false)
        if (header.sessionLabel.text in setOf("Preparing…", "Running...")) {
            header.setSessionStatus("Ready")
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
