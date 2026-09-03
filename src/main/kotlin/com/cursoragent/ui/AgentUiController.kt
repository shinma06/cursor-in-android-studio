package com.cursoragent.ui

import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentProcessService
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import javax.swing.SwingUtilities

class AgentUiController(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val composer: ComposerPanel,
    private val header: AgentHeaderBar,
) {
    private val agentService = project.getService(AgentProcessService::class.java)

    fun startNewChat() {
        agentService.startNewChat()
        timeline.clearTimeline()
        header.setSessionStatus("Ready")
    }

    fun sendPrompt(userText: String) {
        if (userText.isBlank()) return

        composer.clearInput()
        composer.setInputEnabled(false)
        timeline.addUserMessage(userText)

        val contextPrefix = buildActiveFileContext()
        val fullPrompt = if (contextPrefix.isNullOrBlank()) userText else "$contextPrefix\n\n$userText"

        header.setSessionStatus("Running...")

        agentService.sendPrompt(fullPrompt, createListener())
    }

    private fun createListener(): AgentProcessListener {
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
