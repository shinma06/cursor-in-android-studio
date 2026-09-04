package com.cursoragent.ui

import com.cursoragent.notification.AgentNotificationService
import com.cursoragent.parser.AssistantChunkDeduper
import com.cursoragent.ui.DiffViewerHelper
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
import com.intellij.openapi.project.guessProjectDir
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
        composer.setRunning(true)

        val userBubble = timeline.addUserMessage(userText)
        header.setSessionStatus("Preparing…")

        // Active-file context and @file/@folder mentions are VFS reads (fast,
        // already-cached, and IntelliJ Platform APIs like FileEditorManager expect
        // EDT anyway) so they stay here. Checkpoint snapshotting and @git-diff both
        // spawn a `git` subprocess -- slow enough that doing them synchronously on
        // the EDT would freeze the UI on every single send -- so those move to a
        // background thread before the actual CLI process is started.
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

            // OSProcessHandler creation + resolveAgentExecutable's File.canExecute()
            // stat calls are cheap but still I/O -- start it here rather than back on
            // the EDT. The listener callbacks below marshal their own UI work via
            // runOnEdt, so this is safe to call off-EDT.
            agentService.sendPrompt(fullPrompt, createListener(userText))
        }
    }

    private fun createListener(userText: String): AgentProcessListener {
        var assistantStarted = false
        val assistantDeduper = AssistantChunkDeduper()

        return object : AgentProcessListener {
            override fun onAssistantDelta(text: String) {
                runOnEdt {
                    val chunk = assistantDeduper.dedupe(text) ?: return@runOnEdt
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
                    AgentNotificationService.notifyToolCall(project, toolName)
                }
            }

            override fun onToolCallStarted(payload: com.cursoragent.parser.ParsedToolCall) {
                runOnEdt {
                    timeline.showStatus(payload.summary)
                    timeline.addToolCallStarted(payload)
                    AgentNotificationService.notifyToolCall(project, payload.summary)
                }
            }

            override fun onToolCallCompleted(payload: com.cursoragent.parser.ParsedToolCall) {
                runOnEdt {
                    timeline.clearStatus()
                    val edit = payload.fileEdit
                    if (edit != null && payload.subtype == "completed") {
                        timeline.addFileEditCard(
                            details = edit,
                            onViewDiff = {
                                val before = edit.beforeContent.orEmpty()
                                val after = edit.afterContent.orEmpty()
                                DiffViewerHelper.showFileEditDiff(project, edit.path, before, after)
                            },
                            onRevert = {
                                val before = edit.beforeContent
                                if (before != null && DiffViewerHelper.revertFileContent(project, edit.path, before)) {
                                    timeline.showStatus("Reverted ${edit.path}")
                                } else {
                                    Messages.showErrorDialog(project, "Could not revert ${edit.path}", "Cursor Agent")
                                }
                            },
                        )
                        return@runOnEdt
                    }
                    if (payload.shellResult != null) {
                        timeline.addShellResultCard(payload)
                        return@runOnEdt
                    }
                    timeline.addToolCallSummary(payload.summary)
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
                    AgentNotificationService.notifyError(project, message)
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
                    AgentNotificationService.notifyTurnCompleted(project, exitCode)
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

    fun stopRun() {
        // No extra UI bookkeeping needed here: destroying the process fires
        // OSProcessHandler's processTerminated callback, which already routes
        // through the listener's onCompleted -> finishRun() path below.
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
