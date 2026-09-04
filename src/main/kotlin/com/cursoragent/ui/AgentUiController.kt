package com.cursoragent.ui

import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.composer.mention.MentionResolver
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.CheckpointService
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtil
import com.cursoragent.settings.ChatHistoryRecord
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.SwingUtilities

class AgentUiController(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val header: AgentHeaderBar,
) {
    private val agentService = project.getService(AgentProcessService::class.java)
    private val checkpointService = project.getService(CheckpointService::class.java)
    private val chatHistoryState = ChatHistoryState.getInstance(project)
    private val mentionResolver = MentionResolver(project)
    private val turnListenerFactory = AgentTurnListenerFactory(
        project = project,
        timeline = timeline,
        composer = composer,
        header = header,
        chatHistoryState = chatHistoryState,
        onRunFinished = ::finishRun,
    )

    init {
        checkpointService.pruneExpired()
        loadModels()
        header.onPastChatsClicked = { showPastChats() }
    }

    private fun loadModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val models = agentService.listModels()
            runOnEdt { composer.modelSelector.setModels(models) }
        }
    }

    fun startNewChat() {
        agentService.startNewChat()
        timeline.clearTimeline()
        header.setSessionStatus("Ready")
    }

    fun sendPrompt(userText: String) {
        if (userText.isBlank()) return

        composer.clearInput()
        composer.setInputEnabled(false)
        composer.setRunning(true)

        val userBubble = timeline.addUserMessage(userText)
        header.setSessionStatus("Preparing…")

        val contextPrefix = buildActiveFileContext()
        val fileMentionContext = mentionResolver.buildFileAndFolderContext(userText)

        ApplicationManager.getApplication().executeOnPooledThread {
            val checkpointId = checkpointService.createSnapshot(userText, agentService.currentChatId())
            val shellMentionContext = mentionResolver.buildShellBackedContext(userText)

            val fullPrompt = buildString {
                if (!contextPrefix.isNullOrBlank()) append(contextPrefix).append("\n\n")
                if (!fileMentionContext.isNullOrBlank()) append(fileMentionContext).append("\n\n")
                if (!shellMentionContext.isNullOrBlank()) append(shellMentionContext).append("\n\n")
                append(userText)
            }

            runOnEdt {
                userBubble.setCheckpointAvailable(checkpointId != null)
                if (checkpointId != null) {
                    userBubble.onRollbackRequested = { requestRollback(checkpointId) }
                }
                header.setSessionStatus("Running...")
            }

            agentService.sendPrompt(fullPrompt, turnListenerFactory.create(userText))
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
            Messages.showErrorDialog(project, "ロールバックに失敗しました", "Cursor Agent")
        }
    }

    private fun showPastChats() {
        val records = chatHistoryState.list()
        if (records.isEmpty()) {
            Messages.showInfoMessage(project, "過去のチャットはまだありません", "Past Chats")
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(records)
            .setTitle("Past Chats")
            .setRenderer { _, value: ChatHistoryRecord, _, _, _ ->
                javax.swing.JLabel(" ${value.firstPromptPreview}  (${formatTimestamp(value.lastUpdatedMs)})")
            }
            .setItemChosenCallback { record -> resumeChat(record) }
            .createPopup()
            .showUnderneathOf(header.pastChatsButton)
    }

    private fun resumeChat(record: ChatHistoryRecord) {
        agentService.resumeChat(record.chatId)
        timeline.clearTimeline()
        header.setSessionStatus("session=${record.chatId.take(8)}… (resumed)")
    }

    private fun formatTimestamp(epochMs: Long): String =
        SimpleDateFormat("MM/dd HH:mm").format(Date(epochMs))

    fun stopRun() {
        agentService.killActiveProcess()
    }

    private fun finishRun() {
        composer.setInputEnabled(true)
        composer.setRunning(false)
        if (header.sessionLabel.text == "Running...") {
            header.setSessionStatus("Ready")
        }
    }

    private fun buildActiveFileContext(): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null

        val selectedText = editor.selectionModel.selectedText?.trim().orEmpty()
        val projectDir = project.guessProjectDir()
        val relativePath = projectDir?.let { VfsUtil.getRelativePath(file, it) } ?: file.path

        return buildString {
            append("Active file: $relativePath")
            if (selectedText.isNotEmpty()) {
                append("\nSelection:\n```\n$selectedText\n```")
            }
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
