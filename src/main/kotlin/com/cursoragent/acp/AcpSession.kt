package com.cursoragent.acp

import com.cursoragent.service.AgentAnswer
import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentInputRequest
import com.cursoragent.service.AgentTurnOutcome
import com.cursoragent.service.PreparedAgentTurn
import com.cursoragent.service.TurnSettings
import com.cursoragent.service.containsCommand
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One lazy resident connection/session per tab. Only the caller's background turn waits for replies. */
internal class AcpSession(
    private val launch: (String, String) -> Process,
    private val onUncertain: () -> Unit,
    private val cancelTimeoutSeconds: Long = 10,
) : AutoCloseable {
    private val lock = Any()
    private val connectionLock = Any()
    @Volatile private var commands: com.cursoragent.service.CommandCatalog = com.cursoragent.service.CommandCatalog.Loading
    @Volatile private var commandListener: (com.cursoragent.service.CommandCatalog) -> Unit = {}

    fun observeCommands(listener: (com.cursoragent.service.CommandCatalog) -> Unit) {
        synchronized(lock) { commandListener = listener; listener(commands) }
    }

    private fun publishCommands(value: com.cursoragent.service.CommandCatalog) {
        synchronized(lock) {
            if (closing) return
            commands = value
            commandListener(value)
        }
    }

    /** Metadata connection only: no turn, prompt, inference, checkpoint, or transport lock. */
    fun prepare(root: String, executable: String) {
        try {
            connect(root, executable) { !closing && !disconnected }
        } catch (_: Exception) {
            publishCommands(com.cursoragent.service.CommandCatalog.Failed)
            disconnect()
            stopProcess()
        }
    }

    private val protocol = AcpProtocol()
    private val configuration = AcpConfiguration()
    private val requests = ConcurrentHashMap.newKeySet<AgentInputRequest>()
    @Volatile private var rpc: AcpJsonRpc? = null
    @Volatile private var process: Process? = null
    @Volatile private var processTree: AcpProcessTree? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var closing = false
    @Volatile private var disconnected = false
    @Volatile private var active: Active? = null
    private var connectionRoot: String? = null
    private var connectionExecutable: String? = null

    private class Active(val turn: PreparedAgentTurn) {
        @Volatile var promptSent = false
        @Volatile var terminal = false
        @Volatile var quiescent = false
        @Volatile var outcome: AgentTurnOutcome? = null
        @Volatile var uncertain = false
    }

    /** No retry or cross-transport fallback. Preparation's project reservation spans this entire call. */
    fun send(prompt: String, turn: PreparedAgentTurn, commandText: String? = null, commandName: String? = null) {
        val current = Active(turn)
        synchronized(lock) {
            if (closing || disconnected || active != null) {
                turn.run.reportError("このACP会話は再送できません。新しい会話を開始してください。")
                turn.run.complete(-1)
                return
            }
            active = current
        }
        turn.run.attachCancellation { cancel(current) }
        try {
            validateSettings(turn.settings, turn.workspace.mode)
            if (!turn.run.isActive) return
            val root = turn.workspace.commandTarget.rootPath ?: throw AcpException("プロジェクトルートが取得できません")
            val connection = connect(root, turn.settings.executable) { turn.run.isActive }
            if (!turn.run.isActive) return
            configure(connection, current)
            if (!turn.run.isActive) return
            val promptParams = sessionParams().apply {
                add("prompt", JsonArray().apply {
                    // ACP recognizes the command prefix. Context is a separate content block, not an argument rewrite.
                    if (commandText != null) add(jsonObject("type" to "text", "text" to commandText))
                    if (commandText == null || prompt.isNotEmpty()) add(jsonObject("type" to "text", "text" to prompt))
                })
            }
            val response = synchronized(lock) {
                if (!turn.run.isActive || closing || disconnected) return
                connection.request("session/prompt", promptParams, onDispatch = {
                    synchronized(lock) {
                        if (!turn.run.isActive || closing || disconnected) throw AcpException("送信前に停止しました")
                        if (commandText != null && (commandName == null || !commands.containsCommand(commandName))) {
                            throw AcpException("選択したコマンドを確認できません")
                        }
                        protocol.beginTurn()
                        processTree!!.sample()
                        current.promptSent = true
                        turn.promptDispatched = true
                        turn.run.emit { it.onSessionUpdated(sessionId, null) }
                    }
                }) { result ->
                    val reason = result.asJsonObject.requiredString("stopReason")
                    val outcome = when (reason) {
                        "end_turn" -> AgentTurnOutcome.COMPLETED
                        "max_tokens" -> AgentTurnOutcome.TOKEN_LIMIT
                        "max_turn_requests" -> AgentTurnOutcome.REQUEST_LIMIT
                        "refusal" -> AgentTurnOutcome.REFUSED
                        "cancelled" -> AgentTurnOutcome.CANCELLED
                        else -> throw AcpException("ACPの終了理由を確認できません")
                    }
                    synchronized(lock) {
                        if (reason != "cancelled" && protocol.hasUnfinishedTools) throw AcpException("ACPツールの停止を確認できません")
                        current.outcome = outcome
                        current.terminal = true
                    }
                }
            }
            while (true) {
                processTree!!.sample()
                try {
                    response.get(25, TimeUnit.MILLISECONDS)
                    break
                } catch (_: java.util.concurrent.TimeoutException) {
                    // Continue observing descendants while the reader receives frames independently.
                }
            }
            processTree!!.awaitQuiet(cancelTimeoutSeconds) { !connection.isClosed && !disconnected }
            current.quiescent = true
            cancelRequests()
        } catch (failure: Exception) {
            if (current.promptSent && !current.quiescent) uncertain(current)
            if (!turn.run.wasStopped && !current.uncertain) {
                val cause = failure.cause ?: failure
                turn.run.reportError(if (cause is AcpException) cause.message!! else "ACP接続または設定の確認に失敗しました")
            }
            disconnect()
        } finally {
            cancelRequests()
            if (current.uncertain || disconnected || closing || turn.run.wasStopped && !current.promptSent) {
                disconnect()
                stopProcess()
            }
            synchronized(lock) { if (active === current) active = null }
            if (current.uncertain) turn.run.completeUncertain(UNCERTAIN_MESSAGE)
            else turn.run.complete(0, outcome = current.outcome)
        }
    }

    private fun connect(root: String, executable: String, isActive: () -> Boolean): AcpJsonRpc = synchronized(connectionLock) {
        if (closing || disconnected || !isActive()) throw AcpException("ACP接続の準備を停止しました")
        rpc?.let {
            if (connectionRoot != root || connectionExecutable != executable || it.isClosed) {
                throw AcpException("ACP接続後に作業場所または実行ファイルが変わりました。新しい会話を開始してください。")
            }
            return it
        }
        connectionRoot = root
        connectionExecutable = executable
        val child = launch(root, executable)
        process = child
        processTree = AcpProcessTree(child)
        if (closing || !isActive()) throw AcpException("ACP接続の準備を停止しました")
        val connection = AcpJsonRpc(child.inputStream, child.outputStream, ::notification, ::request, onClosed = {
            disconnected = true
            publishCommands(com.cursoragent.service.CommandCatalog.Failed)
            active?.takeIf { it.promptSent && !it.quiescent }?.let(::uncertain)
            // Closing pipes/process happens off the reader and never on EDT.
            thread(name = "Cursor ACP cleanup", isDaemon = true) { stopProcess() }
        })
        rpc = connection
        thread(name = "Cursor ACP reader", isDaemon = true) { connection.read() }
        thread(name = "Cursor ACP diagnostics", isDaemon = true) {
            // Deliberately discard raw stderr: it may contain credentials, prompts or file contents.
            runCatching { child.errorStream.use { stream -> val bytes = ByteArray(4096); while (stream.read(bytes) >= 0) Unit } }
        }
        val initialize = JsonObject().apply {
            addProperty("protocolVersion", 1)
            add("clientInfo", jsonObject("name" to "cursor-in-android-studio", "version" to "0.1.0"))
            add("clientCapabilities", JsonObject().apply {
                add("fs", JsonObject().apply { addProperty("readTextFile", false); addProperty("writeTextFile", false) })
                addProperty("terminal", false)
            })
        }
        connection.request("initialize", initialize) {
            val version = it.asJsonObject["protocolVersion"]
            require(version?.isJsonPrimitive == true && version.asJsonPrimitive.isNumber &&
                version.asBigDecimal.compareTo(java.math.BigDecimal.ONE) == 0)
        }.get(20, TimeUnit.SECONDS)
        if (!isActive()) throw AcpException("ACP接続の準備を停止しました")
        // P0: existing CLI authentication works; never launch an interactive login flow here.
        connection.request("session/new", jsonObject("cwd" to root).apply { add("mcpServers", JsonArray()) }) { result ->
            val body = result.asJsonObject
            sessionId = body.requiredString("sessionId").also { require(it.isNotEmpty()) }
            configuration.replace(body)
            publishCommands(com.cursoragent.service.CommandCatalog.Awaiting)
        }.get(20, TimeUnit.SECONDS)
        return connection
    }

    private fun configure(connection: AcpJsonRpc, current: Active) {
        val settings = current.turn.settings
        val mode = settings.mode.name.lowercase()
        val model = settings.model.ifEmpty { configuration.selection("model") }
        for ((id, value) in listOf("mode" to mode, "model" to model)) {
            if (!current.turn.run.isActive) return
            if (!configuration.accepts(id, value)) throw AcpException("選択した$id はこのACP接続で利用できません。設定を確認してください。")
            if (configuration.selection(id) != value) {
                connection.request("session/set_config_option", sessionParams().apply {
                    addProperty("configId", id)
                    addProperty("value", value)
                }) { configuration.replace(it.asJsonObject) }.get(20, TimeUnit.SECONDS)
            }
            if (configuration.selection(id) != value) throw AcpException("ACPが選択した$id を確定しませんでした")
        }
        if (configuration.selection("mode") != mode || configuration.selection("model") != model) {
            throw AcpException("ACP設定の組み合わせが確定しませんでした")
        }
        current.turn.run.emit { it.onStructuredEvent(configuration.state()) }
    }

    private fun notification(method: String, params: JsonObject) {
        if (closing || disconnected || method != "session/update" || params.string("sessionId") != sessionId) return
        val update = params.getAsJsonObject("update")
        if (update.string("sessionUpdate") == "available_commands_update") {
            publishCommands(availableCommands(update))
            return
        }
        if (update.string("sessionUpdate") == "config_option_update") {
            configuration.replace(update)
            active?.turn?.run?.emit { it.onStructuredEvent(configuration.state()) }
            return
        }
        val current = active
        if (current == null || current.terminal || !current.promptSent) {
            if (!closing && update.string("sessionUpdate") in setOf("tool_call", "tool_call_update")) {
                onUncertain()
                disconnect()
            }
            return
        }
        val event = synchronized(lock) { protocol.update(update) }
        if (event != null) current.turn.run.emit { it.onStructuredEvent(event) }
    }

    private fun request(wire: AcpJsonRpc.Request, method: String, params: JsonObject) {
        val current = active
        if (current == null || !current.promptSent || params.has("sessionId") && params.string("sessionId") != sessionId) {
            wire.reject(-32602, "No active session prompt")
            return
        }
        val input = try { synchronized(lock) { protocol.interruptMessage(); protocol.input(method, params) } } catch (_: Exception) {
            wire.reject(-32602, "Invalid request parameters")
            return
        }
        if (input == null) {
            wire.reject()
            return
        }
        if (current.turn.run.wasStopped || current.terminal || closing) {
            wire.respond(answerJson(AgentAnswer.Cancel))
            return
        }
        lateinit var pending: AgentInputRequest
        pending = AgentInputRequest(input) { answer ->
            synchronized(lock) {
                val effective = if (active !== current || current.turn.run.wasStopped || current.terminal || closing) AgentAnswer.Cancel else answer
                val sent = wire.respond(answerJson(effective))
                requests.remove(pending)
                effective.takeIf { sent }
            }
        }
        requests.add(pending)
        // Stop may have arrived after validation and before insertion.
        if (!current.turn.run.isActive || closing) pending.answer(AgentAnswer.Cancel)
        else current.turn.run.emit { it.onStructuredEvent(AgentEvent.Input(pending)) }
    }

    private fun cancel(current: Active) {
        cancelRequests()
        if (active !== current || current.terminal) return
        if (!current.promptSent) {
            disconnect()
            return
        }
        rpc?.notify("session/cancel", sessionParams())
        CompletableFuture.delayedExecutor(cancelTimeoutSeconds, TimeUnit.SECONDS).execute {
            if (active === current && !current.terminal) {
                uncertain(current)
                disconnect()
            }
        }
    }

    private fun cancelRequests() = requests.toList().forEach { it.answer(AgentAnswer.Cancel) }

    private fun uncertain(current: Active) {
        current.uncertain = true
        onUncertain()
    }

    private fun sessionParams() = jsonObject("sessionId" to requireNotNull(sessionId))

    private fun disconnect() {
        disconnected = true
        rpc?.close()
    }

    /** Always background. Escalate only this connection's process tree after cooperative cancellation fails. */
    private fun stopProcess() {
        val child = process ?: return
        val tree = processTree
        runCatching { tree?.sample() }.onFailure { onUncertain() }
        runCatching { tree?.destroyObserved(false) }.onFailure { onUncertain() }
        runCatching { child.destroy() }
        runCatching {
            if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly().waitFor(2, TimeUnit.SECONDS)
        }
        runCatching { tree?.destroyObserved(true) }.onFailure { onUncertain() }
        if (child.isAlive || runCatching { tree?.isQuiet() == false }.getOrDefault(true)) onUncertain()
        runCatching { child.outputStream.close() }
        runCatching { child.inputStream.close() }
        runCatching { child.errorStream.close() }
    }

    override fun close() {
        synchronized(lock) { closing = true; commandListener = {} }
        val current = active
        if (current != null) current.turn.run.stop()
        else {
            disconnect()
            thread(name = "Cursor ACP close", isDaemon = true) { stopProcess() }
        }
    }

    companion object {
        const val UNCERTAIN_MESSAGE = "ACPの実行終了を確認できません。再送せず、残っている処理を確認してください。このプロジェクトの復元は無効です。"

        fun validateSettings(settings: TurnSettings, mode: WorktreeMode) {
            if (settings.permission != PermissionMode.ASK_EVERY_TIME || settings.sandbox != SandboxMode.DEFAULT || mode != WorktreeMode.DEFAULT) {
                throw AcpException("ACPで確認済みの設定は「操作の確認: 標準」「実行範囲: CLIの既定」「作業場所: このプロジェクト」です。設定を変更するか、新しい会話で互換CLIを選んでください。")
            }
        }
    }
}
