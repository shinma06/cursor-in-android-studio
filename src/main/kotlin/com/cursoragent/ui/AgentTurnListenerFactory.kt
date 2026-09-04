package com.cursoragent.ui

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
    fun create(userText: String): AgentProcessListener {
        var assistantStarted = false
        val assistantDeduper = AssistantChunkDeduper()

        return object : AgentProcessListener {
            override fun onAssistantDelta(text: String) {
                runOnEdt {
                    val full = assistantDeduper.dedupe(text) ?: return@runOnEdt
                    if (!assistantStarted) {
                        timeline.ensureAssistantBubble()
                        assistantStarted = true
                    }
                    timeline.setAssistantText(full)
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

            override fun onToolCallStarted(payload: ParsedToolCall) {
                runOnEdt {
                    timeline.showStatus(payload.summary)
                    timeline.addToolCallStarted(payload)
                    AgentNotificationService.notifyToolCall(project, payload.summary)
                }
            }

            override fun onToolCallCompleted(payload: ParsedToolCall) {
                runOnEdt {
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
                                        "Cursor Agent",
                                    )
                                }
                            },
                        )
                        return@runOnEdt
                    }
                    if (payload.shellResult != null) {
                        timeline.addShellResultCard(payload)
                        return@runOnEdt
                    }
                    timeline.addToolCallSummary(payload.callId, payload.summary)
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
                    onRunFinished()
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
                    onRunFinished()
                }
            }
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
