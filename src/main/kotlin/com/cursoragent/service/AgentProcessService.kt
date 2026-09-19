package com.cursoragent.service

import com.cursoragent.parser.belongsToPrintSession

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
    /** Print process constructed, or ACP prompt dispatch observed; preparation has ended. */
    fun onStarted() {}
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
    /** Successful print Result plus actual exit 0, after AgentRun has rejected Stop/errors. */
    fun onPrintCompleted(requestId: PrintRequestId) { onCompleted(0) }
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
    internal val imageWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "Cursor image snapshots").apply { isDaemon = true }
    }
    private var images: com.cursoragent.ui.composer.image.ImageAttachmentStore? = null
    init {
        imageWorker.execute {
            runCatching { com.cursoragent.ui.composer.image.ImageAttachmentStore.recoverStopped() }
                .onSuccess { reasons -> reasons.forEach { LOG.warn(it) } }
                .onFailure { LOG.warn("前回の添付一時データの確認に失敗したため保持しました。") }
        }
    }
    /** Called only on imageWorker; recovery never runs on the UI thread. */
    internal fun imageStore(): com.cursoragent.ui.composer.image.ImageAttachmentStore = images ?: run {
        com.cursoragent.ui.composer.image.ImageAttachmentStore(onRetained = { LOG.warn(it) }).also { images = it }
    }
    internal fun releaseImage(image: com.cursoragent.ui.composer.image.ImageAttachmentStore.ImageAttachment) {
        runCatching { imageWorker.execute { runCatching { image.close() }.onFailure { LOG.warn("添付一時データの解放に失敗しました。") } } }
    }
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
    fun prepareAcpCommands(tabId: String, root: String, executable: String, onImageSupport: (Boolean?) -> Unit = {}, onCommands: (CommandCatalog) -> Unit) {
        if (disposed) return
        val session = acpSession(tabId)
        session.observeCommands(onCommands)
        session.observeImageSupport(onImageSupport)
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

    internal fun sendPrompt(prompt: String, turn: PreparedAgentTurn, tabId: String? = null, transport: AgentTransport = AgentTransport.PRINT, commandText: String? = null, commandName: String? = null,
        image: com.cursoragent.ui.composer.image.ValidatedImage? = null) {
        if (image != null && transport != AgentTransport.ACP) {
            turn.run.reportError("画像の送信には画像対応を確認できるACP接続が必要です。")
            turn.run.complete(-1)
            return
        }
        if (transport == AgentTransport.ACP) {
            val session = synchronized(this) {
                if (disposed || !turn.run.isActive) return
                acpSession(requireNotNull(tabId))
            }
            session.send(prompt, turn, commandText, commandName, image)
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

        val taskState = PrintTaskState(turn.workspace.resumeId)
        try {
            val requestId = PrintRequestIdCandidate(turn.workspace.resumeId)
            val parser = StreamJsonParser { event ->
                taskState.observe(event)
                val chatId = taskState.sessionId
                run.emit { listener ->
                    when (event) {
                        StreamEvent.OutputLimitExceeded -> {
                            run.reportError("CLIの出力が1行の受信上限を超えたため停止しました。")
                            handler.destroyProcess()
                        }

                        is StreamEvent.SessionInit -> {
                            requestId.session(event.sessionId)
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

                        is StreamEvent.ToolCallStarted -> if (event.payload.belongsToPrintSession(chatId)) {
                            listener.onToolCallStarted(event.payload)
                        }

                        is StreamEvent.ToolCallCompleted -> if (event.payload.belongsToPrintSession(chatId)) {
                            listener.onToolCallCompleted(event.payload)
                        }

                        is StreamEvent.Result -> {
                            requestId.accept(event)
                            listener.onTokenUsage(event.usage)
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
                        finishPrintTaskRun(run, operations, taskState.backgroundObserved, event.exitCode, stderr.toString().trim(), requestId.completed(event.exitCode))
                    } finally {
                        processReservation.close()
                    }
                }
            })

            run.attachProcess(handler::destroyProcess) { handler.isProcessTerminated }
            run.emit { it.onStarted() }
            handler.startNotify()
        } catch (error: Exception) {
            // A constructed process may already be writing even if listener setup/startNotify fails.
            // Observe the OS process directly: a ProcessHandler callback may never be installed.
            run.reportError("CLIの出力監視を開始できませんでした")
            LOG.warn("Failed to observe agent process", error)
            handler.process.onExit().thenRun {
                runs.remove(run)
                try {
                    finishPrintTaskRun(run, operations, taskState.backgroundObserved, -1)
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
    fun listModels(): ModelCatalogState =
        modelCatalogResult(runAgentCommandSync("--list-models", timeoutMs = 15_000))

    /** Current CLI metadata path. The dialog parses observed `id: status` rows with
     *  McpListParser and falls back to raw output when no rows can be parsed. */
    fun listMcpServersRaw(): String = runAgentCommandSync("mcp", "list") ?: "(agent mcp list failed)"

    fun setMcpServerEnabled(identifier: String, enabled: Boolean): Boolean {
        val subcommand = if (enabled) "enable" else "disable"
        return runAgentCommandSync("mcp", subcommand, identifier) != null
    }

    private fun runAgentCommandSync(vararg args: String, timeoutMs: Int = 0): String? {
        val settings = AgentSettingsState.getInstance()
        val executable = resolveAgentExecutable(settings.agentExecutablePath)
        val workspace = project.basePath ?: return null
        return try {
            val commandLine = GeneralCommandLine(executable, *args)
                .withWorkDirectory(File(workspace))
                .withCharset(StandardCharsets.UTF_8)
                .withEnvironment(System.getenv())
            val output = ExecUtil.execAndGetOutput(commandLine, timeoutMs)
            output.stdout.takeIf { output.exitCode == 0 && !output.isTimeout && !output.isCancelled }
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
        imageWorker.execute { runCatching { images?.close() }; images = null }
        imageWorker.shutdown()
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

/** Physical parent exit cannot confirm a provider-managed background child's termination. */
internal fun finishPrintTaskRun(run: AgentRun, operations: WorkspaceOperationGate, backgroundObserved: Boolean, exitCode: Int, errorOutput: String? = null, printRequestId: PrintRequestId? = null) {
    if (backgroundObserved) {
        operations.markUncertain()
        run.completeUncertain("背景Taskの終了を確認できません。復元を停止しました。")
    } else run.complete(exitCode, errorOutput, printRequestId = printRequestId)
}

/** Wire safety state outlives UI delivery, including buffered initialization after Stop or tab close. */
internal class PrintTaskState(resumeId: String?) {
    @Volatile var sessionId: String? = resumeId
        private set
    @Volatile var backgroundObserved = false
        private set

    @Synchronized
    fun observe(event: StreamEvent) {
        if (sessionId == null) sessionId = when (event) {
            is StreamEvent.SessionInit -> event.sessionId
            is StreamEvent.Result -> event.sessionId
            else -> null
        }?.takeIf { it.isNotBlank() }
        val payload = when (event) {
            is StreamEvent.ToolCallStarted -> event.payload
            is StreamEvent.ToolCallCompleted -> event.payload
            else -> null
        }
        if (payload?.belongsToPrintSession(sessionId) == true && payload.task?.task?.isBackground == true) {
            backgroundObserved = true
        }
    }
}
