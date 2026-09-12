package com.cursoragent.ui.composer.mention

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer

class MentionListCellRenderer : ListCellRenderer<Mention> {
    override fun getListCellRendererComponent(
        list: JList<out Mention>,
        value: Mention,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        return JBLabel(value.displayLabel, iconFor(value.kind), JBLabel.LEFT).apply {
            putClientProperty("html.disable", true)
            toolTipText = value.contextDescription()
            border = JBUI.Borders.empty(2, 8)
            isOpaque = true
            background = if (isSelected) list.selectionBackground else list.background
            foreground = if (isSelected) list.selectionForeground else list.foreground
        }
    }

    private fun iconFor(kind: MentionKind): Icon = when (kind) {
        MentionKind.FILE -> AllIcons.FileTypes.Text
        MentionKind.FOLDER -> AllIcons.Nodes.Folder
        MentionKind.GIT_DIFF -> AllIcons.Actions.Diff
        MentionKind.BRANCH -> AllIcons.Vcs.Branch
        MentionKind.TERMINAL -> AllIcons.Debugger.Console
        MentionKind.DOCS -> AllIcons.Toolwindows.Documentation
        MentionKind.WEB -> AllIcons.General.Web
    }
}
