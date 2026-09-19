package com.cursoragent.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

enum class AgentTransport { PRINT, ACP }

enum class AgentTurnOutcome(val message: String) {
    COMPLETED("応答が完了しました"),
    REFUSED("依頼が拒否されました"),
    TOKEN_LIMIT("応答の長さの上限に達しました"),
    REQUEST_LIMIT("このターンの要求回数の上限に達しました"),
    CANCELLED("Agent側で応答が取り消されました"),
}

/** UI-facing values contain no wire JSON, CLI schema or process handles. */
sealed interface AgentEvent {
    /** Exact text delta with an explicit message boundary (no print deduplication). */
    data class Text(val text: String, val messageId: String?, val startsMessage: Boolean) : AgentEvent
    data class Content(val summary: AgentToolContent.Summary) : AgentEvent
    data class Thought(val text: String) : AgentEvent
    data class Tool(val state: AgentTool) : AgentEvent
    data class Input(val request: AgentInputRequest) : AgentEvent
    data class Plan(val entries: List<String>) : AgentEvent
    data class Configuration(val mode: String, val model: String, val models: List<ModelOption>) : AgentEvent
}

data class AgentTool(
    val id: String,
    val title: String = "ツール",
    val kind: String? = null,
    val status: String? = null,
    val content: List<AgentToolContent> = emptyList(),
    val locations: List<String> = emptyList(),
    val command: String? = null,
    val path: String? = null,
    val task: AgentTask? = null,
    val locationsNotice: String? = null,
) {
    val hasPermissionTarget: Boolean get() = !command.isNullOrBlank() || !path.isNullOrBlank() ||
        locations.isNotEmpty() || content.any { it is AgentToolContent.Diff }
}

sealed interface AgentToolContent {
    data class Text(val text: String) : AgentToolContent
    data class Diff(val path: String, val before: String?, val after: String) : AgentToolContent
    data class Unsupported(val type: String) : AgentToolContent
    /** Finite plain display data; never contains binary data or an executable URI. */
    data class Summary(val type: String, val state: ContentDisplayState, val details: String = "") : AgentToolContent
}

sealed interface AgentInput {
    data class Permission(val tool: AgentTool, val options: List<PermissionOption>) : AgentInput
    data class Questions(val title: String?, val questions: List<Question>) : AgentInput
    data class Plan(val title: String?, val overview: String?, val markdown: String) : AgentInput
}

data class PermissionOption(val id: String, val name: String, val kind: String)
data class Question(val id: String, val prompt: String, val options: List<QuestionOption>, val multiple: Boolean)
data class QuestionOption(val id: String, val label: String)

sealed interface AgentAnswer {
    data object Cancel : AgentAnswer
    data object Skip : AgentAnswer
    data object Accept : AgentAnswer
    data object Reject : AgentAnswer
    data class Permission(val optionId: String) : AgentAnswer
    data class Questions(val answers: Map<String, List<String>>) : AgentAnswer
}

/** One response per request, including races between Stop, close and late UI clicks. */
class AgentInputRequest(val input: AgentInput, private val reply: (AgentAnswer) -> AgentAnswer?) {
    private val answered = AtomicBoolean()
    val resolved = CompletableFuture<AgentAnswer?>()
    val isPending: Boolean get() = !answered.get()

    fun answer(answer: AgentAnswer): Boolean {
        if (!valid(answer) || !answered.compareAndSet(false, true)) return false
        resolved.complete(runCatching { reply(answer) }.getOrNull())
        return true
    }

    private fun valid(answer: AgentAnswer): Boolean {
        if (answer == AgentAnswer.Cancel) return true
        return when (val body = input) {
            is AgentInput.Permission -> answer is AgentAnswer.Permission && body.options.any {
                it.id == answer.optionId && (it.kind in setOf("reject_once", "reject_always") ||
                    it.kind in setOf("allow_once", "allow_always") && body.tool.hasPermissionTarget)
            }
            is AgentInput.Plan -> answer == AgentAnswer.Accept || answer == AgentAnswer.Reject
            is AgentInput.Questions -> answer == AgentAnswer.Skip || answer is AgentAnswer.Questions &&
                answer.answers.keys == body.questions.map { it.id }.toSet() && body.questions.all { question ->
                    val values = answer.answers.getValue(question.id)
                    values.isNotEmpty() && values.size == values.toSet().size &&
                        (question.multiple || values.size == 1) && values.all { id -> question.options.any { it.id == id } }
                }
        }
    }
}

/** Display support is independent of the provider's tool execution status. */
enum class ContentDisplayState(val label: String) {
    METADATA("情報のみ・内容未検証"), INVALID("内容の形式が不正"),
    UNSUPPORTED("表示未対応"), LIMITED("表示上限により省略"),
}

fun AgentToolContent.Summary.displayText(): String =
    "$type（${state.label}）" + if (details.isEmpty()) "" else "\n$details"
