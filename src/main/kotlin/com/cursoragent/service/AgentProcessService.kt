package com.cursoragent.service

import com.cursoragent.parser.StreamEvent
import com.cursoragent.parser.StreamJsonParser
import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

interface AgentProcessListener {
    fun onUserMessage(prompt: String) {}
    fun onAssistantDelta(text: String) {}
    fun onResultFallback(text: String) {}
    fun onThinking(text: String) {}
    fun onToolCall(toolName: String) {}
    fun onSessionUpdated(chatId: String?, model: String?) {}
    fun onError(message: String) {}
    fun onCompleted(exitCode: Int) {}
}

@Service(Service.Level.PROJECT)
class AgentProcessService(private val project: Project) : Disposable {
    private val LOG = logger<AgentProcessService>()
    private val activeHandler = AtomicReference<OSProcessHandler?>(null)
    private var chatId: String? = null

    fun sendPrompt(prompt: String, listener: AgentProcessListener) {
        if (prompt.isBlank()) return

        killActiveProcess()

        val settings = AgentSettingsState.getInstance()
        val workspace = project.basePath ?: project.baseDir?.path
        if (workspace.isNullOrBlank()) {
            listener.onError("プロジェクトルートが取得できません")
            return
        }

        listener.onUserMessage(prompt)

        val commandLine = buildCommandLine(prompt, workspace, settings)
        LOG.info("Starting agent: ${commandLine.commandLineString}")

        val handler = OSProcessHandler(commandLine)
        activeHandler.set(handler)

        val parser = StreamJsonParser { event ->
            when (event) {
                is StreamEvent.SessionInit -> {
                    chatId = event.sessionId ?: chatId
                    listener.onSessionUpdated(chatId, event.model)
                }

                is StreamEvent.AssistantDelta -> {
                    if (event.text.isNotEmpty()) listener.onAssistantDelta(event.text)
                }

                is StreamEvent.ThinkingDelta -> {
                    if (event.text.isNotBlank()) listener.onThinking(event.text.trim())
                }

                is StreamEvent.ToolCall -> listener.onToolCall(event.toolName)

                is StreamEvent.Result -> {
                    chatId = event.sessionId ?: chatId
                    listener.onSessionUpdated(chatId, event.model)
                    if (event.isError) {
                        listener.onError(event.result ?: "Agent returned an error")
                    } else if (!event.result.isNullOrBlank()) {
                        listener.onResultFallback(event.result)
                    }
                }

                is StreamEvent.Unknown -> {
                    LOG.debug("Unknown stream event: ${event.type}")
                }
            }
        }

        handler.addProcessListener(object : ProcessAdapter() {
            private val stderr = StringBuilder()

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when (outputType) {
                    ProcessOutputTypes.STDOUT -> {
                        event.text.lineSequence().forEach(parser::parseLine)
                    }

                    ProcessOutputTypes.STDERR -> {
                        stderr.append(event.text)
                    }
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                activeHandler.compareAndSet(handler, null)
                val errorOutput = stderr.toString().trim()
                if (event.exitCode != 0 && errorOutput.isNotBlank()) {
                    listener.onError(errorOutput)
                }
                listener.onCompleted(event.exitCode)
            }
        })

        handler.startNotify()
    }

    fun currentChatId(): String? = chatId

    fun startNewChat() {
        killActiveProcess()
        chatId = null
    }

    fun killActiveProcess() {
        activeHandler.getAndSet(null)?.destroyProcess()
    }

    override fun dispose() {
        killActiveProcess()
    }

    private fun buildCommandLine(
        prompt: String,
        workspace: String,
        settings: AgentSettingsState,
    ): GeneralCommandLine {
        val executable = resolveAgentExecutable(settings.agentExecutablePath)
        // Without --trust the CLI blocks on a "Workspace Trust Required" prompt that
        // has no TTY to answer it, so every run in a project opened for the first
        // time fails outright. Opening the project in the IDE is the trust boundary.
        val args = mutableListOf(
            "-p",
            "--output-format", "stream-json",
            "--stream-partial-output",
            "--trust",
            "--workspace", workspace,
        )

        chatId?.let { args += listOf("--resume", it) }

        settings.selectedModel.takeIf { it.isNotBlank() }?.let {
            args += listOf("--model", it)
        }

        settings.mode.cliValue?.let { args += listOf("--mode", it) }

        if (settings.forceEnabled) {
            args += "--force"
        }

        args += prompt

        return GeneralCommandLine(executable)
            .withParameters(args)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(File(workspace))
            .withEnvironment(System.getenv())
    }

    private fun resolveAgentExecutable(configuredPath: String): String {
        if (configuredPath.isNotBlank() && File(configuredPath).canExecute()) {
            return configuredPath
        }

        val candidates = listOf(
            "/usr/local/bin/agent",
            "/opt/homebrew/bin/agent",
            "${System.getProperty("user.home")}/.local/bin/agent",
            "agent",
        )

        for (candidate in candidates) {
            if (candidate == "agent") return candidate
            if (File(candidate).canExecute()) return candidate
        }

        return "agent"
    }
}
