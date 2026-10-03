package com.cursoragent.ui.timeline

import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

/** A keyboard-accessible toggle; callers keep the short status visible outside the details. */
internal class ToolDetailsPanel(content: JPanel, expanded: Boolean = false) : JPanel(BorderLayout()) {
    private val details = content
    private val toggle = JButton()
    var expanded: Boolean
        get() = details.isVisible
        set(value) {
            details.isVisible = value
            toggle.text = if (value) "詳細を閉じる" else "詳細を表示"
            revalidate()
            repaint()
        }

    init {
        isOpaque = false
        add(toggle, BorderLayout.NORTH)
        add(details, BorderLayout.CENTER)
        toggle.addActionListener { this.expanded = !this.expanded }
        this.expanded = expanded
    }
}
