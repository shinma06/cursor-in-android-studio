package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationHistory
import com.cursoragent.settings.ChatHistoryState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Loading is off EDT; neither selection nor replay executes provider operations. */
class PastChatsCoordinator(
    private val project: Project,
    private val chatHistoryState: ChatHistoryState,
    private val owner: JComponent,
    private val onChatResumed: (Conversation?, String?, com.cursoragent.history.ConversationMatch?, String) -> Unit,
    private val isOpen: (String) -> Boolean,
) : Disposable {
    private val history = project.getService(ConversationHistory::class.java)
    private var dialog: HistorySearchDialog? = null
    private var disposed = false
    private var generation = 0

    init { owner.addHierarchyListener { if (!owner.isShowing) { generation++; dialog?.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE) } } }

    fun showPopup(event: AnActionEvent) {
        if (disposed || project.isDisposed || !owner.isShowing) return
        dialog?.let { it.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE); dialog = null; return }
        val ticket = ++generation
        history.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || !owner.isShowing || ticket != generation) return@invokeLater
            val loaded = result.getOrNull()
            if (loaded == null) {
                Messages.showErrorDialog(project, "履歴を読み込めませんでした。保存先の権限を確認してください。", "履歴")
                return@invokeLater
            }
            val entries = loaded.conversations.map { HistoryEntry(it, null, it.preview, it.updatedMs) } +
                chatHistoryState.list().filter { legacy -> loaded.conversations.none { it.transport == com.cursoragent.service.AgentTransport.PRINT && it.providerId == legacy.chatId } }
                    .map { HistoryEntry(null, it.chatId, it.firstPromptPreview, it.lastUpdatedMs) }
            val next = HistorySearchDialog(project, entries, loaded.unreadable,
                onOpen = { hit, query ->
                    val entry = hit.entry
                    if (!disposed && !project.isDisposed && (entry.conversation == null || !history.isDeleted(entry.conversation.id))) {
                        onChatResumed(entry.conversation, entry.legacyId, hit.match, query)
                    }
                }, onDelete = ::delete, onExport = TranscriptExport(project)::export,
            )
            dialog = next
            next.show()
            if (dialog === next) dialog = null
        } }
    }

    private fun delete(entry: HistoryEntry) {
        val conversation = entry.conversation
        if (conversation != null && isOpen(conversation.id)) {
            Messages.showInfoMessage(project, "この会話のタブを閉じてから削除してください。", "履歴")
            return
        }
        if (Messages.showYesNoDialog(project, "この会話の保存本文・履歴を削除します。取り消せません。", "履歴を削除", Messages.getWarningIcon()) != Messages.YES) return
        if (conversation == null) { entry.legacyId?.let(chatHistoryState::delete); return }
        if (isOpen(conversation.id)) {
            Messages.showInfoMessage(project, "会話が開かれたため削除を中止しました。タブを閉じてから再実行してください。", "履歴")
            return
        }
        history.delete(conversation.id) { deleted -> SwingUtilities.invokeLater {
            if (!disposed && !project.isDisposed) {
                if (deleted && conversation.transport == com.cursoragent.service.AgentTransport.PRINT) conversation.providerId?.let(chatHistoryState::delete)
                if (!deleted) Messages.showErrorDialog(project, "履歴を削除できませんでした。保存データは保持されています。", "履歴")
            }
        } }
    }

    override fun dispose() { disposed = true; generation++; dialog?.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE); dialog = null }
}
