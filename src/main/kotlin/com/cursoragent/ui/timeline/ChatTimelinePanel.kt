package com.cursoragent.ui.timeline

import com.cursoragent.parser.FileEditDetails
import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.service.AgentInputRequest
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingUtilities

class ChatTimelinePanel : JPanel(BorderLayout()) {
    private val messagesPanel = object : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = JBUI.scale(16)
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) = (r.height - JBUI.scale(24)).coerceAtLeast(1)
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }.apply {
        layout = TranscriptLayout(JBUI.scale(14))
        isOpaque = false
        border = JBUI.Borders.empty(6, 12, 12, 12)
    }

    private val emptyState = EmptyStatePanel()
    private val scrollPane = JBScrollPane(messagesPanel).apply {
        border = JBUI.Borders.empty()
        verticalScrollBar.unitIncrement = JBUI.scale(16)
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        viewport.background = AgentUiColors.panelBackground
    }

    var isActiveTab: Boolean = true

    private var currentAssistantBubble: AssistantMessageBubble? = null
    private var currentStatusRow: StatusMessageRow? = null

    /** Tracks the still-in-progress row for a `started` tool call, keyed by call id, so the
     * matching `completed` event can replace it in place instead of leaving a stale duplicate. */
    private val activeToolCallRows = mutableMapOf<String, Component>()
    private val structuredTools = mutableMapOf<String, Component>()
    private var planRow: Component? = null

    init {
        isOpaque = false
        add(emptyState, BorderLayout.CENTER)
    }

    fun addUserMessage(text: String): UserMessageBubble {
        structuredTools.clear()
        planRow = null
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
            removeRow(row)
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

    fun upsertStructuredTool(tool: AgentTool, viewDiff: (AgentToolContent.Diff) -> Unit) {
        clearStatus()
        hideEmptyState()
        val card = StructuredToolCard(tool, viewDiff)
        replaceRow(structuredTools.put(tool.id, card), card)
    }

    fun addInputRequest(request: AgentInputRequest) {
        hideEmptyState()
        addRow(AgentRequestCard(request))
    }

    fun showPlan(entries: List<String>) {
        hideEmptyState()
        val row = ToolCallBubble("作業計画", entries.joinToString("\n"))
        replaceRow(planRow, row)
        planRow = row
    }

    private fun replaceRow(old: Component?, replacement: Component) {
        val index = messagesPanel.components.indexOf(old)
        if (index < 0) addRow(replacement) else {
            messagesPanel.remove(index)
            messagesPanel.add(replacement, index)
            revalidate()
            repaint()
        }
    }

    /** Removes the still-in-progress row for [callId] (if any) — a `completed` event replaces it. */
    private fun removeActiveRow(callId: String) {
        activeToolCallRows.remove(callId)?.let { removeRow(it) }
    }

    private fun addRow(component: Component) {
        messagesPanel.add(component)
        revalidate()
        repaint()
        scrollToBottom()
    }

    private fun removeRow(component: Component) {
        val index = messagesPanel.components.indexOf(component)
        if (index < 0) return
        messagesPanel.remove(index)
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

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            if (!isActiveTab) return@invokeLater
            val bar = scrollPane.verticalScrollBar
            bar.value = bar.maximum
        }
    }
}
