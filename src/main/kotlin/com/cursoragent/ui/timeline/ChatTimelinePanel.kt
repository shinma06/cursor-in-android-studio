package com.cursoragent.ui.timeline

import com.cursoragent.parser.FileEditDetails
import com.cursoragent.parser.ParsedToolCall
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ChatTimelinePanel : JPanel(BorderLayout()) {
    private val messagesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(8, 0)
    }

    private val emptyState = EmptyStatePanel()
    private val scrollPane = JBScrollPane(messagesPanel).apply {
        border = JBUI.Borders.empty()
        verticalScrollBar.unitIncrement = 16
    }

    private var currentAssistantBubble: AssistantMessageBubble? = null
    private var currentStatusRow: StatusMessageRow? = null

    init {
        isOpaque = false
        add(emptyState, BorderLayout.CENTER)
    }

    fun clearTimeline() {
        messagesPanel.removeAll()
        currentAssistantBubble = null
        currentStatusRow = null
        showEmptyState()
        revalidate()
        repaint()
    }

    fun addUserMessage(text: String): UserMessageBubble {
        hideEmptyState()
        val bubble = UserMessageBubble(text)
        addRow(bubble)
        return bubble
    }

    fun ensureAssistantBubble(): AssistantMessageBubble {
        currentAssistantBubble?.let { return it }
        hideEmptyState()
        val bubble = AssistantMessageBubble()
        currentAssistantBubble = bubble
        addRow(bubble)
        return bubble
    }

    fun appendAssistantText(text: String) {
        ensureAssistantBubble().appendContent(text)
        scrollToBottom()
    }

    fun setAssistantText(text: String) {
        ensureAssistantBubble().setContent(text)
        scrollToBottom()
    }

    fun finalizeAssistantMessage() {
        currentAssistantBubble = null
    }

    fun showStatus(text: String) {
        hideEmptyState()
        val row = currentStatusRow
        if (row != null) {
            row.updateText(text)
        } else {
            currentStatusRow = StatusMessageRow(text).also { addRow(it) }
        }
        scrollToBottom()
    }

    fun clearStatus() {
        currentStatusRow?.let { row ->
            messagesPanel.remove(row)
            currentStatusRow = null
            revalidate()
            repaint()
        }
    }

    fun showError(text: String) {
        clearStatus()
        hideEmptyState()
        addRow(StatusMessageRow("Error: $text"))
        scrollToBottom()
    }

    fun addToolCallStarted(payload: ParsedToolCall) {
        clearStatus()
        hideEmptyState()
        addRow(ToolCallBubble(payload.summary))
        scrollToBottom()
    }

    fun addFileEditCard(
        details: FileEditDetails,
        onViewDiff: () -> Unit,
        onRevert: () -> Unit,
    ) {
        hideEmptyState()
        addRow(FileEditCard(details, onViewDiff, onRevert))
        scrollToBottom()
    }

    fun addShellResultCard(payload: ParsedToolCall) {
        val result = payload.shellResult ?: return
        hideEmptyState()
        addRow(ToolCallBubble.forShell(payload.summary, result))
        scrollToBottom()
    }

    fun addToolCallSummary(summary: String) {
        hideEmptyState()
        addRow(ToolCallBubble(summary))
        scrollToBottom()
    }

    private fun addRow(component: Component) {
        messagesPanel.add(component)
        messagesPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        revalidate()
        repaint()
        scrollToBottom()
    }

    private fun hideEmptyState() {
        if (emptyState.parent == this) {
            remove(emptyState)
            add(scrollPane, BorderLayout.CENTER)
            revalidate()
            repaint()
        }
    }

    private fun showEmptyState() {
        if (scrollPane.parent == this) {
            remove(scrollPane)
            add(emptyState, BorderLayout.CENTER)
        }
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val bar = scrollPane.verticalScrollBar
            bar.value = bar.maximum
        }
    }
}
