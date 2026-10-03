package com.cursoragent.ui.timeline

import com.cursoragent.parser.ShellResultDetails
import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.JTextArea

class ToolCallBubble(
    title: String,
    body: String? = null,
) : JPanel(BorderLayout()) {
    private var detailsPanel: ToolDetailsPanel? = null
    internal var expanded: Boolean
        get() = detailsPanel?.expanded ?: false
        set(value) { detailsPanel?.expanded = value }

    init {
        isOpaque = true
        background = AgentUiColors.assistantBubbleBackground
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(AgentUiColors.bubbleBorder, 1),
            JBUI.Borders.empty(8, 10),
        )

        val heading = title.lineSequence().first().take(120)
        add(JBLabel(if (heading == title) title else "$heading…").apply {
            putClientProperty("html.disable", true)
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
        }, BorderLayout.NORTH)

        val detailText = listOfNotNull(title.takeIf { it != heading }, body).joinToString("\n\n")
        if (detailText.isNotBlank()) {
            val details = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JTextArea(detailText).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
                    background = AgentUiColors.assistantBubbleBackground
                    border = JBUI.Borders.emptyTop(6)
                }, BorderLayout.CENTER)
            }
            detailsPanel = ToolDetailsPanel(details)
            add(detailsPanel, BorderLayout.CENTER)
        }
    }

    companion object {
        fun forShell(title: String, result: ShellResultDetails): ToolCallBubble {
            val output = result.interleavedOutput?.takeIf { it.isNotBlank() }
                ?: buildString {
                    if (result.stdout.isNotBlank()) append(result.stdout)
                    if (result.stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(result.stderr)
                    }
                }
            return ToolCallBubble("コマンド${if (result.exitCode == 0) "完了" else "失敗"}（終了コード ${result.exitCode}）", "$title\n\n${output.ifBlank { "出力はありません" }}")
        }
    }
}
