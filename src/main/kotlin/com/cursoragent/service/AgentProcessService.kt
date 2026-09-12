package com.cursoragent.service

import com.cursoragent.acp.AcpException
import com.cursoragent.acp.AcpSession
import com.cursoragent.parser.PrintAssistantText
import com.cursoragent.parser.StreamEvent
import com.cursoragent.parser.StreamJsonParser
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.WorktreeMode
import com.cursoragent.settings.detectAgentExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
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

data class ModelOption(val id: String, val label: String)

interface AgentProcessListener {
    fun onStructuredEvent(event: AgentEvent) {}
    fun onTurnOutcome(outcome: AgentTurnOutcome) {
        if (outcome == AgentTurnOutcome.COMPLETED) onCompleted(0) else onError(outcome.message)
    }
    fun onUncertain(message: String) { onError(message) }
    fun onUserMessage(prompt: String) {}
    fun onAssistantText(text: String) {}
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

/** Owns print runs and per-tab ACP connections; UI/session lifetimes remain separate. */
@Service(Service.Level.PROJECT)
class AgentProcessService(private val project: Project) : Disposable {
    private val LOG = logger<AgentProcessService>()
    private val runs = java.util.concurrent.ConcurrentHashMap.newKeySet<AgentRun>()
    @Volatile private var disposed = false
    private val sessionTargets = SessionWorkspaceHistory()
    private val operations = WorkspaceOperationGate()
    private val acpSessions = mutableMapOf<String, AcpSession>()

    fun restoreUnavailableReason(): String = if (operations.isUncertain) AcpSession.UNCERTAIN_MESSAGE else RestorePolicy.BUSY

    @Synchronized
    fun closeSession(tabId: String) { acpSessions.remove(tabId)?.close() }


    private fun acpSession(tabId: String): AcpSession = acpSessions.getOrPut(tabId) {
        AcpSession(
            launch = { root, executable ->
                GeneralCommandLine(executable.ifBlank { resolveAgentExecutable("") }, "acp")
                    .withWorkDirectory(File(root)).withCharset(StandardCharsets.UTF_8).createProcess()
            },
            onUncertain = operations::markUncertain,
        )
    }

    /** Capture session ownership before scheduling, so a late task cannot recreate a closed tab. */
    @Synchronized
    fun prepareAcpCommands(tabId: String, root: String, executable: String, onCommands: (CommandCatalog) -> Unit) {
        if (disposed) return
        val session = acpSession(tabId)
        session.observeCommands(onCommands)
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val canonicalRoot = runCatching { RestoreTarget.capture(root, WorktreeMode.DEFAULT).rootPath }.getOrNull()
            if (canonicalRoot == null) onCommands(CommandCatalog.Failed)
            else session.prepare(canonicalRoot, executable)
        }
    }

    /** Early UI guidance; AcpSession still validates the same values at its execution boundary. */
    fun settingsUnavailableReason(transport: AgentTransport, settings: TurnSettings, mode: WorktreeMode): String? {
        if (transport == AgentTransport.PRINT) return null
        return try {
            AcpSession.validateSettings(settings, mode)
            null
        } catch (error: AcpException) {
            error.message
        }
    }

    fun captureWorkspace(resumeId: String?, mode: WorktreeMode): TurnWorkspace {
        return TurnWorkspace(
            project.basePath,
            mode,
            resumeId,
            resumeId?.let(sessionTargets::find),
        )
    }

    fun tryRestore(): AutoCloseable? = operations.tryRestore()

    @Synchronized
    fun prepareTurn(workspace: TurnWorkspace, settings: TurnSettings, listener: () -> AgentProcessListener): PreparedAgentTurn? {
        if (disposed) return null
        val preparation = operations.tryPrepare() ?: return null
        return try {
            val run = AgentRun(listener()) { runs.remove(it) }
            runs.add(run)
            PreparedAgentTurn(run, workspace, preparation, settings)
        } catch (error: Exception) {
            preparation.close()
            throw error
        }
    }

    fun sendPrompt(prompt: String, turn: PreparedAgentTurn, tabId: String? = null, transport: AgentTransport = AgentTransport.PRINT, commandText: String? = null, commandName: String? = null) {
        if (transport == AgentTransport.ACP) {
            val session = synchronized(this) {
                if (disposed || !turn.run.isActive) return
                acpSession(requireNotNull(tabId))
            }
            session.send(prompt, turn, commandText, commandName)
            return
        }
        val run = turn.run
        if (!run.isActive) return
        if (prompt.isBlank()) {
            run.complete(0)
            return
        }

        val settings = turn.settings
        val workspace = turn.workspace.commandTarget.rootPath
        if (workspace.isNullOrBlank()) {
            run.reportError("プロジェクトルートが取得できません")
            run.complete(-1)
            return
        }

        run.emit { it.onUserMessage(prompt) }

        val commandLine = buildCommandLine(prompt, turn.workspace, settings)
        val printText = PrintAssistantText(
            probePrintVersion(commandLine, run),
            commandLine.parametersList.hasParameter("--stream-partial-output"),
        )
        if (!run.isActive) {
            run.complete(0)
            return
        }
        LOG.info("Starting print agent")

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
            var chatId = turn.workspace.resumeId
            val parser = StreamJsonParser { event ->
                run.emit { listener ->
                    when (event) {
                        is StreamEvent.SessionInit -> {
                            if (chatId == null) chatId = event.sessionId?.takeIf { it.isNotBlank() }
                            chatId?.let { sessionTargets.record(it, turn.workspace.restoreTarget) }
                            listener.onSessionUpdated(chatId, event.model)
                        }

                        is StreamEvent.AssistantDelta -> {
                            printText.accept(event)?.let(listener::onAssistantText)
                        }

                        is StreamEvent.ThinkingDelta -> {
                            if (event.text.isNotBlank()) listener.onThinking(event.text.trim())
                        }

                        is StreamEvent.ToolCall -> listener.onToolCall(event.toolName)

                        is StreamEvent.ToolCallStarted -> listener.onToolCallStarted(event.payload)

                        is StreamEvent.ToolCallCompleted -> listener.onToolCallCompleted(event.payload)

                        is StreamEvent.Result -> {
                            listener.onTokenUsage(event.usage)
                            if (chatId == null) chatId = event.sessionId?.takeIf { it.isNotBlank() }
                            chatId?.let { sessionTargets.record(it, turn.workspace.restoreTarget) }
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
                            parser.parseChunk(event.text)
                        }

                        ProcessOutputTypes.STDERR -> {
                            stderr.append(event.text)
                        }
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    runs.remove(run)
                    try {
                        parser.finish()
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
                runs.remove(run)
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

    /** Current CLI metadata path. The dialog parses observed `id: status` rows with
     *  McpListParser and falls back to raw output when no rows can be parsed. */
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

    fun killActiveProcess() {
        runs.toList().forEach { it.detachListener(); it.stop() }
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        killActiveProcess()
        acpSessions.values.forEach { it.close() }
        acpSessions.clear()
    }

    private fun buildCommandLine(
        prompt: String,
        workspace: TurnWorkspace,
        settings: TurnSettings,
    ): GeneralCommandLine {
        val executable = resolveAgentExecutable(settings.executable)
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

        args += settings.arguments()

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

        return detectAgentExecutable() ?: "agent"
    }
}

/** Probe the frozen invocation, never current global settings; cancellation also owns this subprocess. */
internal fun probePrintVersion(command: GeneralCommandLine, run: AgentRun): String? = runCatching {
    if (!run.isActive) return null
    val probe = GeneralCommandLine(command.exePath, "--version")
        .withWorkDirectory(command.workDirectory)
        .withCharset(StandardCharsets.UTF_8)
        .withEnvironment(command.environment)
    val handler = CapturingProcessHandler(probe)
    run.attachCancellation { handler.destroyProcess() }
    val output = handler.runProcess(3000)
    output.stdout.trim().takeIf { output.exitCode == 0 && !output.isTimeout && !output.isCancelled }
}.getOrNull()
