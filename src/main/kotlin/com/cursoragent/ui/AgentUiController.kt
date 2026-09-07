package com.cursoragent.ui

import com.cursoragent.PluginBrand
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.composer.mention.MentionResolver
import com.cursoragent.service.AgentRun
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.CheckpointService
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

class AgentUiController(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val header: AgentHeaderBar,
) {
    private var turnGeneration = 0L
    private val agentService = project.getService(AgentProcessService::class.java)
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
        onChatResumed = {
            turnGeneration++
            agentService.killActiveProcess()
            composer.contextUsage.reset()
            finishRun()
        },
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
        turnGeneration++
        composer.contextUsage.reset()
        agentService.startNewChat()
        timeline.clearTimeline()
        finishRun()
        header.setSessionStatus("Ready")
    }

    fun sendPrompt(userText: String) {
        if (userText.isBlank()) return

        val generation = ++turnGeneration
        val usageTicket = composer.contextUsage.beginTurn()
        lateinit var run: AgentRun
        run = agentService.prepareTurn(
            turnListenerFactory.create(
                userText,
                usageTicket,
                isCurrent = { turnGeneration == generation },
                isStopped = { run.wasStopped },
            ),
        )
        composer.clearInput()
        composer.setInputEnabled(false)
        composer.setRunning(true)

        timeline.clearStatus()
        timeline.finalizeAssistantMessage()
        val userBubble = timeline.addUserMessage(userText)
        header.setSessionStatus("Preparing…")

        val edtContext = try {
            promptContextBuilder.buildEdtContext(userText)
        } catch (error: Exception) {
            run.reportError("送信の準備に失敗しました: ${error.message}")
            run.complete(-1)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            if (!run.isActive) return@executeOnPooledThread
            try {
                val checkpointId = checkpointService.createSnapshot(userText, agentService.currentChatId())
                if (!run.isActive) return@executeOnPooledThread
                val backgroundContext = promptContextBuilder.buildBackgroundContext(userText)
                val fullContext = listOfNotNull(edtContext, backgroundContext)
                    .joinToString("\n\n")
                    .takeIf { it.isNotBlank() }
                val fullPrompt = promptContextBuilder.assemble(fullContext, userText)

                if (!run.isActive) return@executeOnPooledThread
                runOnEdt {
                    if (project.isDisposed || turnGeneration != generation || run.wasStopped) return@runOnEdt
                    userBubble.setCheckpointAvailable(checkpointId != null)
                    if (checkpointId != null) {
                        userBubble.onRollbackRequested = { requestRollback(checkpointId) }
                    }
                    header.setSessionStatus("Running...")
                }

                agentService.sendPrompt(fullPrompt, run)
            } catch (error: Exception) {
                run.reportError("送信の準備に失敗しました: ${error.message}")
                run.complete(-1)
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

        if (checkpointService.restore(checkpointId)) {
            timeline.showStatus("Rolled back to checkpoint")
        } else {
            Messages.showErrorDialog(project, "ロールバックに失敗しました", PluginBrand.NAME)
        }
    }

    fun stopRun() {
        composer.contextUsage.reset()
        agentService.killActiveProcess()
    }

    private fun finishRun() {
        composer.setInputEnabled(true)
        composer.setRunning(false)
        if (header.sessionLabel.text == "Running...") {
            header.setSessionStatus("Ready")
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
