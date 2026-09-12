package com.cursoragent.ui

import com.cursoragent.PluginBrand
import com.cursoragent.notification.AgentNotificationService
import com.cursoragent.parser.AssistantChunkDeduper
import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentProcessService
import com.cursoragent.service.RestorePolicy
import com.cursoragent.service.RestoreResult
import com.cursoragent.service.RestoreTarget
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.composer.context.UsagePhase
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

/**
 * Builds the per-turn [AgentProcessListener] that maps stream-json callbacks onto
 * timeline/composer UI updates. Extracted from [AgentUiController] so the
 * controller stays focused on prompt assembly and high-level orchestration (#11).
 */
class AgentTurnListenerFactory(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val recorder: ConversationRecorder,
    private val onRunFinished: (successful: Boolean) -> Unit,
) {
    fun create(
        usageTicket: Long,
        isCurrent: () -> Boolean,
        isStopped: () -> Boolean,
        onSession: (String) -> Boolean,
        restoreTarget: () -> RestoreTarget,
    ): AgentProcessListener {
        fun update(allowStopped: Boolean = false, block: () -> Unit) {
            updateCurrentTurnOnEdt({ project.isDisposed }, isCurrent, isStopped, allowStopped, block)
        }
        val assistantText = TurnAssistantText(
            { text -> timeline.setAssistantText(text); recorder.assistant(text) },
            { timeline.finalizeAssistantMessage(); recorder.newAssistant() },
        )

        return object : AgentProcessListener {
            override fun onStructuredEvent(event: AgentEvent) {
                update {
                    when (event) {
                        is AgentEvent.Text -> assistantText.acpDelta(event)
                        is AgentEvent.Thought -> timeline.showStatus("考え中: ${event.text.take(80)}")
                        is AgentEvent.Tool -> {
                            recorder.tool(event.state.id, "ツール: ${safeToolKind(event.state.kind)} (${safeToolStatus(event.state.status)})")
                            timeline.upsertStructuredTool(event.state) { diff ->
                                DiffViewerHelper.showFileEditDiff(project, diff.path, diff.before.orEmpty(), diff.after)
                            }
                        }
                        is AgentEvent.Input -> timeline.addInputRequest(event.request)
                        is AgentEvent.Plan -> timeline.showPlan(event.entries)
                        is AgentEvent.Configuration -> composer.showAcpConfiguration(event)
                    }
                }
            }

            override fun onTurnOutcome(outcome: com.cursoragent.service.AgentTurnOutcome) {
                if (outcome == com.cursoragent.service.AgentTurnOutcome.COMPLETED) {
                    onCompleted(0)
                    return
                }
                update {
                    composer.contextUsage.finish(usageTicket, if (outcome == com.cursoragent.service.AgentTurnOutcome.CANCELLED) UsagePhase.STOPPED else UsagePhase.FAILED)
                    timeline.finalizeAssistantMessage()
                    recorder.finish(outcome.name.lowercase())
                    timeline.showStatus(outcome.message)
                    onRunFinished(false)
                }
            }

            override fun onUncertain(message: String) {
                update(allowStopped = true) {
                    composer.contextUsage.finish(usageTicket, UsagePhase.FAILED)
                    timeline.finalizeAssistantMessage()
                    recorder.error("接続の終了を確認できませんでした。")
                    recorder.finish("failed")
                    timeline.showError(message)
                    onRunFinished(false)
                }
            }

            override fun onAssistantDelta(text: String) {
                update { assistantText.printDelta(text) }
            }

            override fun onTokenUsage(usage: com.cursoragent.parser.TokenUsage?) {
                update { composer.contextUsage.update(usageTicket, usage) }
            }

            override fun onResultFallback(text: String) {
                update { assistantText.printFallback(text) }
            }

            override fun onThinking(text: String) {
                update {
                    timeline.showStatus("Thinking: ${text.take(80)}")
                }
            }

            override fun onToolCall(toolName: String) {
                update {
                    timeline.showStatus("Running: $toolName")
                    AgentNotificationService.notifyToolCall(project, toolName)
                }
            }

            override fun onToolCallStarted(payload: ParsedToolCall) {
                update {
                    timeline.showStatus(payload.summary)
                    recorder.tool(payload.callId, "ツール: ${safeToolKind(payload.kind)}（実行中）")
                    timeline.addToolCallStarted(payload)
                    AgentNotificationService.notifyToolCall(project, payload.summary)
                }
            }

            override fun onToolCallCompleted(payload: ParsedToolCall) {
                update {
                    timeline.clearStatus()
                    recorder.tool(payload.callId, "ツール: ${safeToolKind(payload.kind)}（完了）")
                    val edit = payload.fileEdit
                    if (edit != null && payload.subtype == "completed") {
                        val target = restoreTarget()
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
                                val before = edit.beforeContent
                                val after = edit.afterContent
                                val reservation = project.getService(AgentProcessService::class.java).tryRestore()
                                val result = if (reservation == null) {
                                    RestoreResult(RestorePolicy.BUSY)
                                } else {
                                    reservation.use {
                                        if (before == null || after == null) RestoreResult(RestorePolicy.RESTORE_FAILED)
                                        else DiffViewerHelper.revertFileContentResult(project, edit.path, before, after, target)
                                    }
                                }
                                if (result.restored) timeline.showStatus("ファイルを編集前に戻しました")
                                else Messages.showErrorDialog(project, result.rejectionReason!!, PluginBrand.NAME)
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
                    composer.contextUsage.finish(usageTicket, UsagePhase.FAILED)
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    timeline.showError(message)
                    recorder.error("このターンでエラーが発生しました。")
                    recorder.finish("failed")
                    AgentNotificationService.notifyError(project, message)
                    Messages.showErrorDialog(project, message, PluginBrand.NAME)
                    if (!project.isDisposed && isCurrent() && !isStopped()) onRunFinished(false)
                }
            }

            override fun onStopped() {
                update(allowStopped = true) {
                    composer.contextUsage.finish(usageTicket, UsagePhase.STOPPED)
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    recorder.finish("stopped")
                    timeline.showStatus("停止しました")
                    onRunFinished(false)
                }
            }

            override fun onCompleted(exitCode: Int) {
                update {
                    composer.contextUsage.finish(usageTicket, if (exitCode == 0) UsagePhase.COMPLETED else UsagePhase.FAILED)
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    if (exitCode != 0) {
                        timeline.showError("Agent exited with code $exitCode")
                    }
                    if (exitCode != 0) recorder.error("Agent終了コード: $exitCode")
                    recorder.finish(if (exitCode == 0) "completed" else "failed")
                    AgentNotificationService.notifyTurnCompleted(project, exitCode)
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

/** Per-turn text only. Both outputs replace the bubble; ACP deltas never use print heuristics. */
internal class TurnAssistantText(
    private val replaceText: (String) -> Unit,
    private val startMessage: () -> Unit,
) {
    private val printDeduper = AssistantChunkDeduper()
    private var printStarted = false
    private val acpText = StringBuilder()

    fun printDelta(text: String) {
        val full = printDeduper.dedupe(text) ?: return
        printStarted = true
        replaceText(full)
    }

    fun printFallback(text: String) {
        if (printStarted) return
        replaceText(text)
        printStarted = true
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
