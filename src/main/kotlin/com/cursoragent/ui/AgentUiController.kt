package com.cursoragent.ui

import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.CheckpointService
import com.cursoragent.settings.ChatHistoryRecord
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.composer.mention.MentionResolver
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtil
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

        val checkpointId = checkpointService.createSnapshot(userText, agentService.currentChatId())
        val userBubble = timeline.addUserMessage(userText)
        userBubble.setCheckpointAvailable(checkpointId != null)
        if (checkpointId != null) {
            userBubble.onRollbackRequested = { requestRollback(checkpointId) }
        }

        val contextPrefix = buildActiveFileContext()
        val mentionContext = mentionResolver.buildContext(userText)
        val fullPrompt = buildString {
            if (!contextPrefix.isNullOrBlank()) append(contextPrefix).append("\n\n")
            if (!mentionContext.isNullOrBlank()) append(mentionContext).append("\n\n")
            append(userText)
        }

        header.setSessionStatus("Running...")

        agentService.sendPrompt(fullPrompt, createListener(userText))
    }

    private fun createListener(userText: String): AgentProcessListener {
        var assistantStarted = false
        val assistantBuffer = StringBuilder()

        return object : AgentProcessListener {
            override fun onAssistantDelta(text: String) {
                runOnEdt {
                    val chunk = dedupeAssistantChunk(text, assistantBuffer) ?: return@runOnEdt
                    if (!assistantStarted) {
                        timeline.ensureAssistantBubble()
                        assistantStarted = true
                    }
                    timeline.appendAssistantText(chunk)
                }
            }

            override fun onResultFallback(text: String) {
                runOnEdt {
                    if (assistantStarted) return@runOnEdt
                    timeline.setAssistantText(text)
                    assistantStarted = true
                }
            }

            override fun onThinking(text: String) {
                runOnEdt {
                    timeline.showStatus("Thinking: ${text.take(80)}")
                }
            }

            override fun onToolCall(toolName: String) {
                runOnEdt {
                    timeline.showStatus("Running: $toolName")
                }
            }

            override fun onSessionUpdated(chatId: String?, model: String?) {
                runOnEdt {
                    val parts = listOfNotNull(
                        chatId?.let { "session=${it.take(8)}…" },
                        model?.let { "model=$it" },
                    )
                    if (parts.isNotEmpty()) {
                        header.setSessionStatus(parts.joinToString(" | "))
                    }
                    if (chatId != null) {
                        chatHistoryState.recordTurn(chatId, userText)
                    }
                }
            }

            override fun onError(message: String) {
                runOnEdt {
                    timeline.clearStatus()
                    timeline.showError(message)
                    Messages.showErrorDialog(project, message, "Cursor Agent")
                    finishRun()
                }
            }

            override fun onCompleted(exitCode: Int) {
                runOnEdt {
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    if (exitCode != 0) {
                        timeline.showError("Agent exited with code $exitCode")
                    }
                    finishRun()
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
        // The CLI has no way to dump a past session's transcript back to us (see
        // requirements doc §13), so this only resumes the *session* for the next
        // turn -- it can't replay the prior turns' bubbles into the timeline.
        agentService.resumeChat(record.chatId)
        timeline.clearTimeline()
        header.setSessionStatus("session=${record.chatId.take(8)}… (resumed)")
    }

    private fun formatTimestamp(epochMs: Long): String =
        SimpleDateFormat("MM/dd HH:mm").format(Date(epochMs))

    private fun finishRun() {
        composer.setInputEnabled(true)
        if (header.sessionLabel.text == "Running...") {
            header.setSessionStatus("Ready")
        }
    }

    private fun buildActiveFileContext(): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null

        val selectedText = editor.selectionModel.selectedText?.trim().orEmpty()
        val relativePath = VfsUtil.getRelativePath(file, project.baseDir) ?: file.path

        return buildString {
            append("Active file: $relativePath")
            if (selectedText.isNotEmpty()) {
                append("\nSelection:\n```\n$selectedText\n```")
            }
        }
    }

    private fun dedupeAssistantChunk(text: String, buffer: StringBuilder): String? {
        if (text.isEmpty()) return null
        val current = buffer.toString()
        val chunk = when {
            current.isEmpty() -> text
            text.startsWith(current) -> text.substring(current.length)
            current.endsWith(text) || current.contains(text) -> return null
            else -> text
        }
        if (chunk.isEmpty()) return null
        buffer.append(chunk)
        return chunk
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
