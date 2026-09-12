package com.cursoragent.ui.timeline

import com.cursoragent.service.AgentAnswer
import com.cursoragent.service.AgentInput
import com.cursoragent.service.AgentInputRequest
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.AbstractButton
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/** Request IDs and answers stay typed; agent text is displayed as plain text, never Swing HTML. */
class AgentRequestCard(private val request: AgentInputRequest) : JPanel() {
    private val controls = mutableListOf<AbstractButton>()
    private val state = plainText("返答を選択してください")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        when (val input = request.input) {
            is AgentInput.Permission -> {
                add(plainText("操作の確認\n${toolDescription(input.tool)}"))
                if (!input.tool.hasPermissionTarget) add(plainText("操作対象を確認できないため、許可できません。"))
                if (input.options.any { it.kind == "allow_always" || it.kind == "reject_always" }) {
                    add(plainText("「今後も」の適用範囲・有効期間は接続先が定めます。このプラグインの共有設定は変更しません。"))
                }
                input.options.forEach { option ->
                    val label = when (option.kind) {
                        "allow_once" -> "今回だけ許可"
                        "allow_always" -> "今後も許可"
                        "reject_once" -> "今回は拒否"
                        "reject_always" -> "今後も拒否"
                        else -> "未対応の選択肢"
                    }
                    button("$label: ${option.name}", AgentAnswer.Permission(option.id)).isEnabled = request.isPending &&
                        (option.kind in setOf("reject_once", "reject_always") ||
                            option.kind in setOf("allow_once", "allow_always") && input.tool.hasPermissionTarget)
                }
            }
            is AgentInput.Plan -> {
                add(plainText(listOfNotNull(input.title, input.overview, input.markdown).joinToString("\n\n")))
                button("計画を承認", AgentAnswer.Accept)
                button("計画を却下", AgentAnswer.Reject)
            }
            is AgentInput.Questions -> {
                input.title?.let { add(plainText(it)) }
                val selections = input.questions.associate { question ->
                    add(plainText(question.prompt))
                    val group = ButtonGroup()
                    question.id to question.options.associate { option ->
                        val choice: AbstractButton = if (question.multiple) JCheckBox() else JRadioButton().also(group::add)
                        choice.text = option.label
                        choice.putClientProperty("html.disable", true)
                        choice.isOpaque = false
                        choice.isEnabled = request.isPending
                        controls.add(choice)
                        add(choice)
                        option.id to choice
                    }
                }
                val send = JButton("回答を送信").apply {
                    isEnabled = false
                    addActionListener {
                        request.answer(AgentAnswer.Questions(selections.mapValues { (_, options) ->
                            options.filterValues { it.isSelected }.keys.toList()
                        }))
                    }
                }
                selections.values.flatMap { it.values }.forEach { choice ->
                    choice.addActionListener { send.isEnabled = request.isPending && selections.values.all { values -> values.values.any { it.isSelected } } }
                }
                controls.add(send)
                add(send)
                button("回答をスキップ", AgentAnswer.Skip)
            }
        }
        button("取り消す", AgentAnswer.Cancel)
        add(state)
        request.resolved.thenAccept { answer ->
            SwingUtilities.invokeLater {
                controls.forEach { it.isEnabled = false }
                state.text = when (answer) {
                    null -> "接続を確認できず、返答を送信できませんでした"
                    AgentAnswer.Cancel -> "取り消す回答を送信しました"
                    AgentAnswer.Skip -> "回答をスキップしました"
                    AgentAnswer.Accept -> "計画を承認する回答を送信しました"
                    AgentAnswer.Reject -> "計画を却下する回答を送信しました"
                    is AgentAnswer.Questions -> "質問への回答を送信しました"
                    is AgentAnswer.Permission -> {
                        val option = (request.input as AgentInput.Permission).options.first { it.id == answer.optionId }
                        when (option.kind) {
                            "allow_once" -> "今回の操作を許可する回答を送信しました"
                            "allow_always" -> "今後も操作を許可する回答を送信しました（適用範囲は接続先の仕様によります）"
                            "reject_once" -> "今回の操作を拒否する回答を送信しました"
                            "reject_always" -> "今後も操作を拒否する回答を送信しました（適用範囲は接続先の仕様によります）"
                            else -> "未対応の返答です"
                        }
                    }
                }
            }
        }
    }

    private fun button(label: String, answer: AgentAnswer): JButton = JButton(label).apply {
        putClientProperty("html.disable", true)
        isEnabled = request.isPending
        addActionListener { request.answer(answer) }
        controls.add(this)
        this@AgentRequestCard.add(this)
    }
}

class StructuredToolCard(tool: AgentTool, viewDiff: (AgentToolContent.Diff) -> Unit) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        val status = when (tool.status) {
            "pending" -> "待機中"
            "in_progress" -> "実行中"
            "completed" -> "報告完了"
            "failed" -> "失敗"
            else -> "状態を確認中"
        }
        add(plainText("$status: ${toolDescription(tool)}"), BorderLayout.NORTH)
        add(plainText(tool.content.mapNotNull {
            when (it) {
                is AgentToolContent.Text -> it.text
                is AgentToolContent.Unsupported -> "表示未対応の内容: ${it.type}"
                is AgentToolContent.Diff -> null
            }
        }.joinToString("\n")), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            isOpaque = false
            tool.content.filterIsInstance<AgentToolContent.Diff>().forEach { diff ->
                add(JButton("差分を表示").apply {
                    toolTipText = diff.path
                    addActionListener { viewDiff(diff) }
                })
            }
        }, BorderLayout.SOUTH)
        // No Revert: these reports do not establish a verified root/before/current-file restore contract.
    }
}

private fun toolDescription(tool: AgentTool): String = listOfNotNull(
    tool.title, tool.command?.let { "コマンド: $it" }, tool.path?.let { "ファイル: $it" },
    tool.locations.takeIf { it.isNotEmpty() }?.joinToString("\n"),
    tool.content.filterIsInstance<AgentToolContent.Diff>().takeIf { it.isNotEmpty() }?.joinToString("\n") { it.path },
).joinToString("\n")

private fun plainText(value: String) = JTextArea(value).apply {
    isEditable = false
    isOpaque = false
    lineWrap = true
    wrapStyleWord = true
}
