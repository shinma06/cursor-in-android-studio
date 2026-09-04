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
    init {
        isOpaque = true
        background = AgentUiColors.assistantBubbleBackground
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(AgentUiColors.bubbleBorder, 1),
            JBUI.Borders.empty(8, 10),
        )

        add(JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
        }, BorderLayout.NORTH)

        if (!body.isNullOrBlank()) {
            add(
                JTextArea(body.trim()).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
                    background = AgentUiColors.assistantBubbleBackground
                    border = JBUI.Borders.emptyTop(6)
                },
                BorderLayout.CENTER,
            )
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
            return ToolCallBubble(title, output.ifBlank { "(no output)" })
        }
    }
}
