package com.cursoragent.ui

import com.cursoragent.settings.ChatHistoryRecord
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.service.AgentProcessService
import com.cursoragent.ui.header.AgentHeaderBar
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Past-chats popup and resume flow. Split from [AgentUiController] (#11).
 */
class PastChatsCoordinator(
    private val project: Project,
    private val timeline: ChatTimelinePanel,
    private val header: AgentHeaderBar,
    private val agentService: AgentProcessService,
    private val chatHistoryState: ChatHistoryState,
    private val onChatResumed: () -> Unit = {},
) {
    fun showPopup() {
        val records = chatHistoryState.list()
        if (records.isEmpty()) {
            Messages.showInfoMessage(project, "過去のチャットはまだありません", "Past Chats")
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(records)
            .setTitle("Past Chats")
            .setRenderer { _, value: ChatHistoryRecord, _, _, _ ->
                javax.swing.JLabel(" ${value.firstPromptPreview}  (${formatTimestamp(value.lastUpdatedMs)})")
            }
            .setItemChosenCallback { record -> resumeChat(record) }
            .createPopup()
            .showUnderneathOf(header.pastChatsButton)
    }

    private fun resumeChat(record: ChatHistoryRecord) {
        onChatResumed()
        agentService.resumeChat(record.chatId)
        timeline.clearTimeline()
        header.setSessionStatus("session=${record.chatId.take(8)}… (resumed)")
    }

    private fun formatTimestamp(epochMs: Long): String =
        SimpleDateFormat("MM/dd HH:mm").format(Date(epochMs))
}
