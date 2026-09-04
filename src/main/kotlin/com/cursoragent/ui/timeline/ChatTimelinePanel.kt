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

    /** Tracks the still-in-progress row for a `started` tool call, keyed by call id, so the
     * matching `completed` event can replace it in place instead of leaving a stale duplicate. */
    private val activeToolCallRows = mutableMapOf<String, Component>()

    init {
        isOpaque = false
        add(emptyState, BorderLayout.CENTER)
    }

    fun clearTimeline() {
        messagesPanel.removeAll()
        currentAssistantBubble = null
        currentStatusRow = null
        activeToolCallRows.clear()
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
        removeActiveRow(payload.callId)
        val row = ToolCallBubble(payload.summary)
        addRow(row)
        activeToolCallRows[payload.callId] = row
        scrollToBottom()
    }

    fun addFileEditCard(
        callId: String,
        details: FileEditDetails,
        onViewDiff: () -> Unit,
        onRevert: () -> Unit,
    ) {
        hideEmptyState()
        removeActiveRow(callId)
        addRow(FileEditCard(details, onViewDiff, onRevert))
        scrollToBottom()
    }

    fun addShellResultCard(payload: ParsedToolCall) {
        val result = payload.shellResult ?: return
        hideEmptyState()
        removeActiveRow(payload.callId)
        addRow(ToolCallBubble.forShell(payload.summary, result))
        scrollToBottom()
    }

    fun addToolCallSummary(callId: String?, summary: String) {
        hideEmptyState()
        callId?.let { removeActiveRow(it) }
        addRow(ToolCallBubble(summary))
        scrollToBottom()
    }

    /** Removes the still-in-progress row for [callId] (if any) — a `completed` event replaces it. */
    private fun removeActiveRow(callId: String) {
        activeToolCallRows.remove(callId)?.let { removeRow(it) }
    }

    private fun addRow(component: Component) {
        messagesPanel.add(component)
        messagesPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        revalidate()
        repaint()
        scrollToBottom()
    }

    private fun removeRow(component: Component) {
        val index = messagesPanel.components.indexOf(component)
        if (index < 0) return
        messagesPanel.remove(index)
        if (index < messagesPanel.componentCount && messagesPanel.getComponent(index) is Box.Filler) {
            messagesPanel.remove(index)
        }
        revalidate()
        repaint()
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
