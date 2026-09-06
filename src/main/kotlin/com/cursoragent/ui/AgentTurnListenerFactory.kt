package com.cursoragent.ui

import com.cursoragent.PluginBrand
import com.cursoragent.notification.AgentNotificationService
import com.cursoragent.parser.AssistantChunkDeduper
import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

/**
 * Builds the per-turn [AgentProcessListener] that maps stream-json callbacks onto
 * timeline/header/composer UI updates. Extracted from [AgentUiController] so the
 * controller stays focused on prompt assembly and high-level orchestration (#11).
 */
class AgentTurnListenerFactory(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val header: AgentHeaderBar,
    private val chatHistoryState: ChatHistoryState,
    private val onRunFinished: () -> Unit,
) {
    fun create(
        userText: String,
        usageTicket: Long,
        isCurrent: () -> Boolean,
        isStopped: () -> Boolean,
    ): AgentProcessListener {
        fun update(allowStopped: Boolean = false, block: () -> Unit) {
            runOnEdt {
                if (!project.isDisposed && isCurrent() && (allowStopped || !isStopped())) block()
            }
        }
        var assistantStarted = false
        val assistantDeduper = AssistantChunkDeduper()

        return object : AgentProcessListener {
            override fun onAssistantDelta(text: String) {
                update {
                    val full = assistantDeduper.dedupe(text) ?: return@update
                    if (!assistantStarted) {
                        timeline.ensureAssistantBubble()
                        assistantStarted = true
                    }
                    timeline.setAssistantText(full)
                }
            }

            override fun onTokenUsage(usage: com.cursoragent.parser.TokenUsage?) {
                update { composer.contextUsage.update(usageTicket, usage) }
            }

            override fun onResultFallback(text: String) {
                update {
                    if (assistantStarted) return@update
                    timeline.setAssistantText(text)
                    assistantStarted = true
                }
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
                    timeline.addToolCallStarted(payload)
                    AgentNotificationService.notifyToolCall(project, payload.summary)
                }
            }

            override fun onToolCallCompleted(payload: ParsedToolCall) {
                update {
                    timeline.clearStatus()
                    val edit = payload.fileEdit
                    if (edit != null && payload.subtype == "completed") {
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
                                if (before != null && after != null &&
                                    DiffViewerHelper.revertFileContent(project, edit.path, before, after)
                                ) {
                                    timeline.showStatus("Reverted ${edit.path}")
                                } else {
                                    Messages.showErrorDialog(
                                        project,
                                        "Could not revert ${edit.path} — it may have been changed again since this edit.",
                                        PluginBrand.NAME,
                                    )
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
                update {
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    timeline.showError(message)
                    AgentNotificationService.notifyError(project, message)
                    Messages.showErrorDialog(project, message, PluginBrand.NAME)
                    onRunFinished()
                }
            }

            override fun onStopped() {
                update(allowStopped = true) {
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    timeline.showStatus("停止しました")
                    onRunFinished()
                    header.setSessionStatus("停止しました")
                }
            }

            override fun onCompleted(exitCode: Int) {
                update {
                    timeline.clearStatus()
                    timeline.finalizeAssistantMessage()
                    if (exitCode != 0) {
                        timeline.showError("Agent exited with code $exitCode")
                    }
                    AgentNotificationService.notifyTurnCompleted(project, exitCode)
                    onRunFinished()
                }
            }
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
