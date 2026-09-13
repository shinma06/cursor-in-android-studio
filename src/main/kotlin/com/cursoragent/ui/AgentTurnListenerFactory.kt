package com.cursoragent.ui

import com.cursoragent.history.ConversationRecorder
import com.cursoragent.notification.AgentNotificationService
import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.parser.taskKey
import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.RestoreTarget
import com.cursoragent.service.displayText
import com.cursoragent.service.taskStatusText
import com.cursoragent.ui.composer.context.UsagePhase
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.cursoragent.ui.timeline.RunPhase
import com.intellij.openapi.project.Project
import javax.swing.SwingUtilities

/**
 * Builds the per-turn [AgentProcessListener] that maps stream-json callbacks onto
 * timeline/composer UI updates. Extracted from [AgentUiController] so the
 * controller stays focused on prompt assembly and high-level orchestration (#11).
 */
internal class AgentTurnListenerFactory(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val onUsage: (Long, com.cursoragent.parser.TokenUsage?) -> Unit,
    private val onConfiguration: (AgentEvent.Configuration) -> Unit,
    private val recorder: ConversationRecorder,
    private val onRunFinished: (successful: Boolean) -> Unit,
    private val changes: ConversationChanges,
    private val beforeRevert: () -> Unit,
    private val onUsageFinished: (Long, UsagePhase) -> Unit = { _, _ -> },
    private val onShowConversation: () -> Unit = {},
    private val onToolNotice: (String) -> Unit = { AgentNotificationService.notifyToolCall(project, it, onShowConversation) },
    private val onTerminalNotice: (String, RunPhase) -> Unit = { id, phase ->
        AgentNotificationService.clearToolCall(project, id)
        when (phase) {
            RunPhase.COMPLETED -> AgentNotificationService.notifyTurnCompleted(project, 0, onShowConversation)
            RunPhase.STOPPED -> AgentNotificationService.notifyStopped(project, onShowConversation)
            else -> AgentNotificationService.notifyTurnCompleted(project, -1, onShowConversation)
        }
    },
) {
    fun create(
        usageTicket: Long,
        turnId: String,
        isCurrent: () -> Boolean,
        isStopped: () -> Boolean,
        onSession: (String) -> Boolean,
        restoreTarget: () -> RestoreTarget,
        onPrintRequestId: (com.cursoragent.service.PrintRequestId) -> Unit = {},
    ): AgentProcessListener {
        var terminal = false
        var toolNoticeSent = false
        fun update(allowStopped: Boolean = false, block: () -> Unit) {
            updateCurrentTurnOnEdt({ project.isDisposed }, isCurrent, isStopped, allowStopped) {
                if (!terminal) block()
            }
        }
        fun activity(phase: RunPhase) { timeline.runStatus.update(phase) }
        fun toolStarted() {
            activity(RunPhase.TOOL)
            if (!toolNoticeSent) {
                toolNoticeSent = true
                if (!timeline.isActiveTab) onToolNotice(turnId)
            }
        }
        fun finish(phase: RunPhase) {
            terminal = true
            activity(phase)
            onTerminalNotice(turnId, phase)
        }
        val assistantText = TurnAssistantText(
            { text -> timeline.setAssistantText(text); recorder.assistant(text) },
            { timeline.finalizeAssistantMessage(); recorder.newAssistant() },
        )

        fun updateTask(payload: ParsedToolCall): Boolean {
            val task = payload.task ?: return false
            val displayed = timeline.upsertTask(task, payload.parentSessionId)
            recorder.tool(payload.taskKey(), taskSavedSummary(displayed))
            return true
        }
        fun finishTasks() {
            timeline.finishTasks().forEach { (id, tool) -> recorder.tool(id, taskSavedSummary(tool)) }
        }

        return object : AgentProcessListener {
            override fun onStarted() { update { activity(RunPhase.RUNNING) } }

            override fun onStructuredEvent(event: AgentEvent) {
                update {
                    when (event) {
                        is AgentEvent.Text -> { activity(RunPhase.RUNNING); assistantText.acpDelta(event) }
                        is AgentEvent.Content -> {
                            activity(RunPhase.RUNNING)
                            assistantText.interrupt()
                            val text = event.summary.displayText()
                            timeline.addAssistantContent(text)
                            recorder.assistantContent(text)
                        }
                        is AgentEvent.Thought -> activity(RunPhase.THINKING)
                        is AgentEvent.Tool -> {
                            if (event.state.status == "in_progress") toolStarted()
                            else activity(RunPhase.RUNNING)
                            changes.acp(turnId, event.state, restoreTarget())
                            val displayed = timeline.upsertStructuredTool(event.state) { diff ->
                                DiffViewerHelper.showFileEditDiff(project, diff.path, diff.before.orEmpty(), diff.after)
                            }
                            recorder.tool(displayed.id, if (displayed.task != null) taskSavedSummary(displayed)
                                else "ツール: ${safeToolKind(displayed.kind)} (${safeToolStatus(displayed.status)})" + safeContentSummary(displayed))
                        }
                        is AgentEvent.Input -> { activity(RunPhase.RUNNING); timeline.addInputRequest(event.request) }
                        is AgentEvent.Plan -> { activity(RunPhase.RUNNING); timeline.showPlan(event.entries) }
                        is AgentEvent.Configuration -> onConfiguration(event)
                    }
                }
            }

            override fun onTurnOutcome(outcome: com.cursoragent.service.AgentTurnOutcome) {
                if (outcome == com.cursoragent.service.AgentTurnOutcome.COMPLETED) {
                    onCompleted(0)
                    return
                }
                update {
                    onUsageFinished(usageTicket, if (outcome == com.cursoragent.service.AgentTurnOutcome.CANCELLED) UsagePhase.STOPPED else UsagePhase.FAILED)
                    timeline.finalizeAssistantMessage()
                    finishTasks()
                    recorder.finish(outcome.name.lowercase())
                    timeline.showStatus(outcome.message)
                    finish(if (outcome == com.cursoragent.service.AgentTurnOutcome.CANCELLED) RunPhase.STOPPED else RunPhase.FAILED)
                    onRunFinished(false)
                }
            }

            override fun onUncertain(message: String) {
                update(allowStopped = true) {
                    onUsageFinished(usageTicket, UsagePhase.FAILED)
                    timeline.finalizeAssistantMessage()
                    finishTasks()
                    recorder.error("接続の終了を確認できませんでした。")
                    recorder.finish("failed")
                    timeline.showError(message)
                    finish(RunPhase.FAILED)
                    onRunFinished(false)
                }
            }

            override fun onAssistantText(text: String) {
                update { activity(RunPhase.RUNNING); assistantText.printText(text) }
            }

            override fun onTokenUsage(usage: com.cursoragent.parser.TokenUsage?) {
                update { onUsage(usageTicket, usage) }
            }

            override fun onResultFallback(text: String) {
                update { activity(RunPhase.RUNNING); assistantText.printFallback(text) }
            }

            override fun onThinking(text: String) {
                update {
                    activity(RunPhase.THINKING)
                }
            }

            override fun onToolCall(toolName: String) {
                update {
                    toolStarted()
                }
            }

            override fun onToolCallStarted(payload: ParsedToolCall) {
                update {
                    if (payload.task == null && timeline.hasCompletedPrintTool(payload.callId)) return@update
                    toolStarted()
                    if (updateTask(payload)) return@update
                    recorder.tool(payload.callId, "ツール: ${safeToolKind(payload.kind)}（実行中）")
                    timeline.addToolCallStarted(payload)
                }
            }

            override fun onToolCallCompleted(payload: ParsedToolCall) {
                update {
                    activity(RunPhase.RUNNING)
                    if (updateTask(payload)) return@update
                    timeline.clearStatus()
                    recorder.tool(payload.callId, "ツール: ${safeToolKind(payload.kind)}（${if (payload.shellResult?.exitCode?.let { it != 0 } == true) "失敗" else "完了"}）")
                    val edit = payload.fileEdit
                    if (edit != null && payload.subtype == "completed") {
                        val target = restoreTarget()
                        changes.print(turnId, payload.callId, edit, target)
                        timeline.addFileEditCard(
                            callId = payload.callId,
                            details = edit,
                            onViewDiff = {
                                DiffViewerHelper.showFileEditDiff(
                                    project,
                                    edit.path,
                                    edit.beforeContent.orEmpty(),
                                    edit.afterContent.orEmpty(),
                                )
                            },
                            onRevert = {
                                beforeRevert()
                                DiffViewerHelper.revertObservedEdit(project, edit.path, edit.beforeContent, edit.afterContent, target) {
                                    timeline.showStatus("ファイルを編集前に戻しました")
                                }
                            },
                        )
                        return@update
                    }
                    if (payload.shellResult != null) {
                        timeline.addShellResultCard(payload)
                        return@update
                    }
                    timeline.addToolCallSummary(payload.callId, payload.summary)
                }
            }

            override fun onSessionUpdated(chatId: String?, model: String?) {
                update {
                    if (chatId != null && !onSession(chatId)) return@update
                    val parts = listOfNotNull(
                        chatId?.let { "session=${it.take(8)}…" },
                        model?.let { "model=$it" },
                    )
                    if (parts.isNotEmpty()) {
                        timeline.toolTipText = parts.joinToString(" | ")
                    }
                    if (chatId != null) {
                        recorder.provider(chatId)
                    }
                }
            }

            override fun onError(message: String) {
                update {
                    onUsageFinished(usageTicket, UsagePhase.FAILED)
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    finishTasks()
                    timeline.showError(message)
                    recorder.error("このターンでエラーが発生しました。")
                    recorder.finish("failed")
                    finish(RunPhase.FAILED)
                    onRunFinished(false)
                }
            }

            override fun onStopped() {
                update(allowStopped = true) {
                    onUsageFinished(usageTicket, UsagePhase.STOPPED)
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    finishTasks()
                    recorder.finish("stopped")
                    timeline.showStatus("停止しました。途中までの応答は残ります。適用済みの変更は自動で戻りません。")
                    finish(RunPhase.STOPPED)
                    onRunFinished(false)
                }
            }

            override fun onCompleted(exitCode: Int) = completed(exitCode, null)

            override fun onPrintCompleted(requestId: com.cursoragent.service.PrintRequestId) = completed(0, requestId)

            private fun completed(exitCode: Int, requestId: com.cursoragent.service.PrintRequestId?) {
                update {
                    onUsageFinished(usageTicket, if (exitCode == 0) UsagePhase.COMPLETED else UsagePhase.FAILED)
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    finishTasks()
                    if (exitCode != 0) {
                        timeline.showError("Agentが終了コード $exitCode で終了しました")
                    }
                    if (exitCode != 0) recorder.error("Agent終了コード: $exitCode")
                    recorder.finish(if (exitCode == 0) "completed" else "failed")
                    finish(if (exitCode == 0) RunPhase.COMPLETED else RunPhase.FAILED)
                    if (requestId != null) onPrintRequestId(requestId)
                    onRunFinished(exitCode == 0)
                }
            }
        }
    }
}

/** Recheck ownership when the queued callback runs, including callbacks queued before Stop/close. */
internal fun updateCurrentTurnOnEdt(
    isDisposed: () -> Boolean,
    isCurrent: () -> Boolean,
    isStopped: () -> Boolean,
    allowStopped: Boolean = false,
    block: () -> Unit,
) {
    val update = { if (!isDisposed() && isCurrent() && (allowStopped || !isStopped())) block() }
    if (SwingUtilities.isEventDispatchThread()) update() else SwingUtilities.invokeLater(update)
}

/** Per-turn text only. Both outputs replace the bubble; print is already normalized by the service. */
internal class TurnAssistantText(
    private val replaceText: (String) -> Unit,
    private val startMessage: () -> Unit,
) {
    private var printStarted = false
    private val acpText = StringBuilder()

    fun printText(full: String) {
        if (full.isEmpty()) return
        printStarted = true
        replaceText(full)
    }

    fun printFallback(text: String) {
        if (printStarted) return
        replaceText(text)
        printStarted = true
    }

    fun interrupt() {
        acpText.clear()
        startMessage()
    }

    fun acpDelta(event: AgentEvent.Text) {
        if (event.startsMessage) {
            acpText.clear()
            startMessage()
        }
        acpText.append(event.text)
        replaceText(acpText.toString())
    }
}

/** Persist allowlisted categories only; provider titles/commands can contain secrets. */
internal fun safeToolKind(kind: String?): String = when (kind) {
    "read", "readToolCall" -> "読取り"
    "edit", "editToolCall" -> "編集"
    "shell", "shellToolCall", "execute" -> "コマンド"
    "search", "searchToolCall" -> "検索"
    else -> "ツール"
}
internal fun safeToolStatus(status: String?): String = when (status) {
    "completed" -> "完了"
    "failed" -> "失敗"
    "pending" -> "待機"
    else -> "実行中"
}

internal fun taskSavedSummary(tool: com.cursoragent.service.AgentTool): String =
    "ツール: 子Task (${taskStatusText(tool.status, tool.task?.isBackground)})"

/** Retain categories/support states, never provider URI/name/text or arbitrary unknown type strings. */
internal fun safeContentSummary(tool: com.cursoragent.service.AgentTool): String {
    val summaries = tool.content.filterIsInstance<com.cursoragent.service.AgentToolContent.Summary>().map {
        val type = it.type.takeIf { value -> value in setOf("image", "audio", "resource_link", "resource", "resource (text)", "resource (blob)", "text", "diff") }
            ?: "内容"
        "$type: ${it.state.label}"
    }.distinct()
    return if (summaries.isEmpty()) "" else "\n内容情報（詳細は保存しません）: " + summaries.joinToString(" / ")
}
