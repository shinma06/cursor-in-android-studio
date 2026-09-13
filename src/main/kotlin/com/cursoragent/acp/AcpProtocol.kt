package com.cursoragent.acp

import com.cursoragent.service.AgentAnswer
import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentInput
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import com.cursoragent.service.ModelOption
import com.cursoragent.service.PermissionOption
import com.cursoragent.service.Question
import com.cursoragent.service.QuestionOption
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Cursor v1 boundary: opaque IDs and partial tool updates never pass through the print parser. */
internal class AcpProtocol {
    private val tools = linkedMapOf<String, AgentTool>()
    private var payloadSize = 0
    private var messageId: String? = null
    private var interrupted = true

    fun interruptMessage() { interrupted = true }

    fun beginTurn() {
        tools.clear()
        payloadSize = 0
        interrupted = true
    }

    val hasUnfinishedTools: Boolean
        get() = tools.values.any { it.status != "completed" && it.status != "failed" }

    fun update(update: JsonObject): AgentEvent? {
        acceptPayload(update)
        return when (update.string("sessionUpdate")) {
            "agent_message_chunk", "agent_thought_chunk" -> {
                val content = update.getAsJsonObject("content")
                if (content.string("type") == "text") {
                    val text = content.requiredString("text")
                    if (update.string("sessionUpdate") == "agent_message_chunk") {
                        val id = update.string("messageId")
                        val boundary = interrupted || messageId != id
                        messageId = id
                        interrupted = false
                        AgentEvent.Text(text, id, boundary)
                    } else { interrupted = true; AgentEvent.Thought(text) }
                } else null // Image/audio input and richer output are separate acceptance scopes.
            }
            "tool_call", "tool_call_update" -> {
                interrupted = true
                val id = update.requiredString("toolCallId")
                require(tools.size < 512 || id in tools)
                val tool = tool(update, tools[id] ?: AgentTool(id))
                tools[id] = tool
                AgentEvent.Tool(tool)
            }
            "plan" -> {
                interrupted = true
                AgentEvent.Plan(update.array("entries").map {
                    val entry = it.asJsonObject
                    "${entry.requiredString("status")}: ${entry.requiredString("content")}"
                })
            }
            else -> null
        }
    }

    fun input(method: String, params: JsonObject): AgentInput? {
        acceptPayload(params)
        return when (method) {
            "session/request_permission" -> {
                val options = params.array("options").map {
                    val option = it.asJsonObject
                    PermissionOption(option.requiredString("optionId"), option.requiredString("name"), option.requiredString("kind"))
                }
                require(options.isNotEmpty() && options.map { it.id }.toSet().size == options.size)
                val payload = params.getAsJsonObject("toolCall")
                val id = payload.requiredString("toolCallId")
                AgentInput.Permission(tool(payload, tools[id] ?: AgentTool(id)), options)
            }
            "cursor/ask_question" -> {
                params.requiredString("toolCallId")
                val questions = params.array("questions").map {
                    val question = it.asJsonObject
                    val options = question.array("options").map { value ->
                        val option = value.asJsonObject
                        QuestionOption(option.requiredString("id"), option.requiredString("label"))
                    }
                    require(options.isNotEmpty() && options.map { it.id }.toSet().size == options.size)
                    val multiple = question["allowMultiple"]?.let { value ->
                        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
                        value.asBoolean
                    } ?: false
                    Question(question.requiredString("id"), question.requiredString("prompt"), options, multiple)
                }
                require(questions.isNotEmpty() && questions.size <= 32 && questions.map { it.id }.toSet().size == questions.size)
                AgentInput.Questions(params.string("title"), questions)
            }
            "cursor/create_plan" -> {
                params.requiredString("toolCallId")
                AgentInput.Plan(params.string("name"), params.string("overview"), params.requiredString("plan"))
            }
            else -> null
        }
    }

    private fun acceptPayload(value: JsonObject) {
        payloadSize += value.toString().length
        require(payloadSize <= 4 * 1024 * 1024) { "ACP turn payload limit" }
    }

    private fun tool(update: JsonObject, old: AgentTool): AgentTool = old.copy(
        command = update["rawInput"]?.takeIf { it.isJsonObject }?.asJsonObject?.string("command") ?: old.command,
        path = update["rawInput"]?.takeIf { it.isJsonObject }?.asJsonObject?.string("path") ?: old.path,
        title = update.string("title") ?: old.title,
        kind = update.string("kind") ?: old.kind,
        status = update.string("status") ?: old.status,
        content = if (update["content"]?.isJsonArray == true) update.array("content").map { item ->
            val data = item.asJsonObject
            when (data.string("type")) {
                "content" -> {
                    val content = data.getAsJsonObject("content")
                    if (content.string("type") == "text") AgentToolContent.Text(content.requiredString("text"))
                    else AgentToolContent.Unsupported(content.string("type") ?: "unknown")
                }
                "diff" -> AgentToolContent.Diff(data.requiredString("path"), data.string("oldText"), data.requiredString("newText"))
                else -> AgentToolContent.Unsupported(data.string("type") ?: "unknown")
            }
        } else old.content,
        locations = if (update["locations"]?.isJsonArray == true) update.array("locations").map { it.asJsonObject.requiredString("path") } else old.locations,
    )
}

internal fun answerJson(answer: AgentAnswer): JsonObject {
    val outcome = when (answer) {
        AgentAnswer.Cancel -> jsonObject("outcome" to "cancelled")
        AgentAnswer.Skip -> jsonObject("outcome" to "skipped")
        AgentAnswer.Accept -> jsonObject("outcome" to "accepted")
        AgentAnswer.Reject -> jsonObject("outcome" to "rejected")
        is AgentAnswer.Permission -> jsonObject("outcome" to "selected", "optionId" to answer.optionId)
        is AgentAnswer.Questions -> jsonObject("outcome" to "answered").apply {
            add("answers", JsonArray().apply {
                answer.answers.forEach { (question, options) ->
                    add(jsonObject("questionId" to question).apply {
                        add("selectedOptionIds", JsonArray().apply { options.forEach(::add) })
                    })
                }
            })
        }
    }
    return JsonObject().apply { add("outcome", outcome) }
}

/** Config lists replace, never merge. Only P0-observed Cursor IDs are interpreted as mode/model. */
internal class AcpConfiguration {
    private var options: List<JsonObject> = emptyList()

    @Synchronized
    fun replace(response: JsonObject) {
        options = response.array("configOptions").map { it.asJsonObject.deepCopy() }
        require(options.map { it.requiredString("id") }.toSet().size == options.size)
    }

    @Synchronized
    fun selection(id: String): String = option(id).requiredString("currentValue")

    @Synchronized
    fun accepts(id: String, value: String): Boolean = values(option(id)).any { it.id == value }

    @Synchronized
    fun state(): AgentEvent.Configuration {
        val mode = selection("mode")
        val model = selection("model")
        val models = values(option("model"))
        require(mode in setOf("agent", "ask", "plan") && accepts("mode", mode) && models.any { it.id == model })
        return AgentEvent.Configuration(mode, model, models)
    }

    private fun option(id: String): JsonObject = options.firstOrNull { it.string("id") == id && it.string("type") == "select" }
        ?: throw AcpException("ACPから必要な設定を取得できません（$id）")

    private fun values(option: JsonObject): List<ModelOption> = option.array("options").flatMap {
        val value = it.asJsonObject
        if (value.has("options")) value.array("options").map { entry ->
            ModelOption(entry.asJsonObject.requiredString("value"), entry.asJsonObject.requiredString("name"))
        } else listOf(ModelOption(value.requiredString("value"), value.requiredString("name")))
    }.also { values -> require(values.map { it.id }.toSet().size == values.size) }
}

internal fun JsonObject.requiredString(name: String): String = requireNotNull(string(name))
internal fun JsonObject.array(name: String): JsonArray = requireNotNull(get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray)

/** An invalid replacement clears confidence in the entire list; never retain stale partial entries. */
internal fun availableCommands(update: JsonObject): com.cursoragent.service.CommandCatalog = try {
    val entries = update.array("availableCommands")
    require(entries.size() <= 2_000)
    val commands = entries.map { entry ->
        val value = entry.asJsonObject
        val name = value.requiredString("name")
        // A command must be one slash token. Do not lowercase, trim, or invent a different ID.
        require(name.isNotEmpty() && name.length <= 256 && !name.startsWith('/') && name.none { it.isWhitespace() || it.isISOControl() })
        val description = value.requiredString("description")
        require(description.length <= 16_384)
        val input = value.get("input")
        val hint = if (input == null || input.isJsonNull) null else input.asJsonObject.requiredString("hint")
        require(hint == null || hint.length <= 4_096)
        com.cursoragent.service.AgentCommand(name, description, hint)
    }
    require(commands.map { it.name }.toSet().size == commands.size)
    com.cursoragent.service.CommandCatalog.Ready(commands)
} catch (_: Exception) {
    com.cursoragent.service.CommandCatalog.Invalid
}
