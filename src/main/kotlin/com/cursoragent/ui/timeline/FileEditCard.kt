package com.cursoragent.ui.timeline

import com.cursoragent.parser.FileEditDetails
import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class FileEditCard(
    details: FileEditDetails,
    onViewDiff: () -> Unit,
    onRevert: () -> Unit,
) : JPanel(BorderLayout()) {
    init {
        isOpaque = true
        background = AgentUiColors.assistantBubbleBackground
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(AgentUiColors.bubbleBorder, 1),
            JBUI.Borders.empty(8, 10),
        )

        val summary = "+${details.linesAdded} / -${details.linesRemoved}  ${details.path}"
        add(JBLabel("File edited: $summary"), BorderLayout.NORTH)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(JButton("View Diff").apply { addActionListener { onViewDiff() } })
            if (details.beforeContent != null) {
                add(JButton("Revert").apply { addActionListener { onRevert() } })
            }
        }
        add(actions, BorderLayout.SOUTH)
    }
}
