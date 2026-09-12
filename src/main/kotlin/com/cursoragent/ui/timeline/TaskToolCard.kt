package com.cursoragent.ui.timeline

import com.cursoragent.service.AgentToolContent
import com.cursoragent.service.AgentTool
import com.cursoragent.service.taskStatus
import com.cursoragent.service.taskStatusText
import com.cursoragent.ui.AgentUiColors
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

/** One stable card per parent turn/tool. Updates keep expansion and never interpret provider text as HTML. */
internal class TaskToolCard(initial: AgentTool, private val viewDiff: (AgentToolContent.Diff) -> Unit) : JPanel(BorderLayout()) {
    var tool: AgentTool = initial
        private set
    private val summary = textArea()
    private val bodyText = textArea()
    private val details = JPanel(BorderLayout()).apply {
        isOpaque = false
        isVisible = false
        add(bodyText, BorderLayout.NORTH)
    }
    private var standardContent: JPanel? = null
    private val toggle = JButton("詳細を表示").apply {
        addActionListener {
            details.isVisible = !details.isVisible
            text = if (details.isVisible) "詳細を閉じる" else "詳細を表示"
            revalidate()
            repaint()
        }
    }

    init {
        isOpaque = true
        background = AgentUiColors.assistantBubbleBackground
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(AgentUiColors.bubbleBorder, 1), JBUI.Borders.empty(8, 10),
        )
        add(summary, BorderLayout.NORTH)
        add(details, BorderLayout.CENTER)
        add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 0, 0)).apply {
            isOpaque = false
            add(toggle)
        }, BorderLayout.SOUTH)
        update(initial)
    }

    fun update(incoming: AgentTool) {
        // Retain the observed result, but do not hide contradictory activity behind "completed".
        if (tool.status == "failed" && incoming.status != "failed") return
        val status = taskStatus(tool.status, incoming.status)
        tool = if (status == "unconfirmed") tool.copy(status = status) else incoming.copy(status = status)
        val task = requireNotNull(tool.task)
        val title = "子Task: ${task.name ?: task.description ?: "名前は未取得"}\n${taskStatusText(tool.status, task.isBackground)}"
        if (summary.text != title) summary.text = title
        val body = buildString {
            task.description?.let { append("要約: $it\n") }
            append("モデル: ${task.model ?: "未取得"}\n")
            append("実行方法: ${when (task.isBackground) { true -> "背景"; false -> "前景"; null -> "未取得" }}\n")
            append("提供された所要時間: ${task.durationMs?.let { "$it ms" } ?: "未取得"}\n")
            append("子のusage: 未取得\n")
            task.requestedAgentId?.let { append("要求のagent ID: $it\n") }
            task.resumeId?.let { append("再開要求のID: $it\n") }
            task.agentId?.let { append("結果のagent ID: $it\n") }
            task.reportedAgentId?.let { append("cursor/taskのagent ID（用途未確認）: $it\n") }
            if (tool.status in setOf("pending", "in_progress")) append("子の詳細な進捗は未取得\n")
            task.errorText?.let { append("\n子Taskのエラー:\n$it\n") }
            append("\n${task.resultText?.let { "提供された子の結果（取得できた範囲）:\n$it" } ?: "子の結果本文は取得できません"}")
        }
        if (bodyText.text != body) bodyText.text = body
        standardContent?.let(details::remove)
        standardContent = if (tool.content.isNotEmpty() || tool.locations.isNotEmpty() || tool.locationsNotice != null) StructuredToolCard(tool, viewDiff) else null
        standardContent?.let { details.add(it, BorderLayout.CENTER) }
        revalidate()
        repaint()
    }

    private fun textArea() = JTextArea().apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
    }
}
