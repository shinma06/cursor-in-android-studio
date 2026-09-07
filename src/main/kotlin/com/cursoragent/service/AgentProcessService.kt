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
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

data class ModelOption(val id: String, val label: String)

interface AgentProcessListener {
    fun onUserMessage(prompt: String) {}
    fun onAssistantDelta(text: String) {}
    fun onResultFallback(text: String) {}
    fun onTokenUsage(usage: com.cursoragent.parser.TokenUsage?) {}
    fun onThinking(text: String) {}
    fun onToolCall(toolName: String) {}
    fun onToolCallStarted(payload: com.cursoragent.parser.ParsedToolCall) {}
    fun onToolCallCompleted(payload: com.cursoragent.parser.ParsedToolCall) {}
    fun onSessionUpdated(chatId: String?, model: String?) {}
    fun onError(message: String) {}
    fun onCompleted(exitCode: Int) {}
    fun onStopped() {}
}

@Service(Service.Level.PROJECT)
class AgentProcessService(private val project: Project) : Disposable {
    private val LOG = logger<AgentProcessService>()
    private val activeRun = AtomicReference<AgentRun?>(null)
    @Volatile
    private var chatId: String? = null
    private val sessionTargets = java.util.concurrent.ConcurrentHashMap<String, RestoreTarget>()
    private val operations = WorkspaceOperationGate()

    fun captureWorkspace(): TurnWorkspace {
        val resumeId = chatId
        return TurnWorkspace(
            project.basePath,
            AgentSettingsState.getInstance().worktreeMode,
            resumeId,
            resumeId?.let(sessionTargets::get),
        )
    }

    fun tryRestore(): AutoCloseable? = operations.tryRestore()

    fun prepareTurn(workspace: TurnWorkspace, listener: () -> AgentProcessListener): PreparedAgentTurn? {
        val preparation = operations.tryPrepare() ?: return null
        return try {
            val run = AgentRun(listener())
            activeRun.set(run)
            PreparedAgentTurn(run, workspace, preparation)
        } catch (error: Exception) {
            preparation.close()
            throw error
        }
    }

    fun sendPrompt(prompt: String, turn: PreparedAgentTurn) {
        val run = turn.run
        if (!run.isActive) return
        if (prompt.isBlank()) {
            run.complete(0)
            return
        }

        val settings = AgentSettingsState.getInstance()
        val workspace = turn.workspace.commandTarget.rootPath
        if (workspace.isNullOrBlank()) {
            run.reportError("プロジェクトルートが取得できません")
            run.complete(-1)
            return
        }

        run.emit { it.onUserMessage(prompt) }

        val commandLine = buildCommandLine(prompt, turn.workspace, settings)
        LOG.info("Starting agent: ${commandLine.commandLineString}")

        val processReservation = turn.preparation.launchingProcess()
        val handler = try {
            OSProcessHandler(commandLine)
        } catch (e: Exception) {
            // Most common real-world cause: the `agent` executable isn't installed or
            // isn't where resolveAgentExecutable guessed. Left uncaught, this throws
            // out of an IDE action handler as a raw platform exception instead of
            // going through the plugin's own error UI.
            processReservation.close()
            LOG.warn("Failed to start agent process", e)
            run.reportError("cursor-agent CLIの起動に失敗しました: ${e.message}")
            run.complete(-1)
            return
        }

        try {
            val parser = StreamJsonParser { event ->
                run.emit { listener ->
                    when (event) {
                        is StreamEvent.SessionInit -> {
                            chatId = event.sessionId ?: chatId
                            chatId?.let { sessionTargets[it] = turn.workspace.restoreTarget }
                            listener.onSessionUpdated(chatId, event.model)
                        }

                        is StreamEvent.AssistantDelta -> {
                            if (event.text.isNotEmpty()) listener.onAssistantDelta(event.text)
                        }

                        is StreamEvent.ThinkingDelta -> {
                            if (event.text.isNotBlank()) listener.onThinking(event.text.trim())
                        }

                        is StreamEvent.ToolCall -> listener.onToolCall(event.toolName)

                        is StreamEvent.ToolCallStarted -> listener.onToolCallStarted(event.payload)

                        is StreamEvent.ToolCallCompleted -> listener.onToolCallCompleted(event.payload)

                        is StreamEvent.Result -> {
                            listener.onTokenUsage(event.usage)
                            chatId = event.sessionId ?: chatId
                            chatId?.let { sessionTargets[it] = turn.workspace.restoreTarget }
                            listener.onSessionUpdated(chatId, event.model)
                            if (event.isError) {
                                run.reportError(event.result ?: "Agent returned an error")
                            } else if (!event.result.isNullOrBlank()) {
                                listener.onResultFallback(event.result)
                            }
                        }

                        is StreamEvent.Unknown -> {
                            LOG.debug("Unknown stream event: ${event.type}")
                        }
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
                    activeRun.compareAndSet(run, null)
                    try {
                        run.complete(event.exitCode, stderr.toString().trim())
                    } finally {
                        processReservation.close()
                    }
                }
            })

            run.attachProcess(handler::destroyProcess) { handler.isProcessTerminated }
            handler.startNotify()
        } catch (error: Exception) {
            // A constructed process may already be writing even if listener setup/startNotify fails.
            // Observe the OS process directly: a ProcessHandler callback may never be installed.
            run.reportError("CLIの出力監視を開始できませんでした")
            LOG.warn("Failed to observe agent process", error)
            handler.process.onExit().thenRun {
                activeRun.compareAndSet(run, null)
                try {
                    run.complete(-1)
                } finally {
                    processReservation.close()
                }
            }
            runCatching { handler.destroyProcess() }
            runCatching { handler.process.destroy() }
        }

    }

    fun currentChatId(): String? = chatId

    fun resumeChat(id: String) {
        chatId = id
    }

    /**
     * `--list-models` and `agent mcp list`/`enable`/`disable` are local metadata
     * operations, not chat turns — verified against a live install that they don't
     * consume the same per-conversation quota `sendPrompt` does (see requirements
     * doc §13), so these run synchronously (blocking) rather than through the
     * streaming OSProcessHandler machinery above. Call off the EDT.
     */
    fun listModels(): List<ModelOption> {
        val output = runAgentCommandSync("--list-models") ?: return emptyList()
        return ModelListParser.parse(output)
    }

    /** Raw `agent mcp list` output; format unverified against a populated config
     *  (no MCP servers were configured on the machine this was written on), so the
     *  UI shows this verbatim rather than attempting a specific parse. */
    fun listMcpServersRaw(): String = runAgentCommandSync("mcp", "list") ?: "(agent mcp list failed)"

    fun setMcpServerEnabled(identifier: String, enabled: Boolean): Boolean {
        val subcommand = if (enabled) "enable" else "disable"
        return runAgentCommandSync("mcp", subcommand, identifier) != null
    }

    private fun runAgentCommandSync(vararg args: String): String? {
        val settings = AgentSettingsState.getInstance()
        val executable = resolveAgentExecutable(settings.agentExecutablePath)
        val workspace = project.basePath ?: return null
        return try {
            val commandLine = GeneralCommandLine(executable, *args)
                .withWorkDirectory(File(workspace))
                .withCharset(StandardCharsets.UTF_8)
                .withEnvironment(System.getenv())
            val output = ExecUtil.execAndGetOutput(commandLine)
            output.stdout.takeIf { output.exitCode == 0 }
        } catch (e: Exception) {
            LOG.warn("agent ${args.joinToString(" ")} failed", e)
            null
        }
    }

    fun startNewChat() {
        killActiveProcess()
        chatId = null
    }

    fun killActiveProcess() {
        activeRun.getAndSet(null)?.stop()
    }

    override fun dispose() {
        killActiveProcess()
    }

    private fun buildCommandLine(
        prompt: String,
        workspace: TurnWorkspace,
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
        )
        args += workspace.arguments()

        settings.selectedModel.takeIf { it.isNotBlank() }?.let {
            args += listOf("--model", it)
        }

        settings.mode.cliValue?.let { args += listOf("--mode", it) }

        settings.permissionMode.cliArg?.let { args += it }

        settings.sandboxMode.cliValue?.let { args += listOf("--sandbox", it) }

        args += prompt

        return GeneralCommandLine(executable)
            .withParameters(args)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(File(requireNotNull(workspace.commandTarget.rootPath)))
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
