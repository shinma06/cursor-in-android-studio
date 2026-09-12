package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationHistory
import com.cursoragent.settings.ChatHistoryState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Loading is off EDT; neither selection nor replay executes provider operations. */
class PastChatsCoordinator(
    private val project: Project,
    private val chatHistoryState: ChatHistoryState,
    private val owner: JComponent,
    private val onChatResumed: (Conversation?, String?) -> Unit,
    private val isOpen: (String) -> Boolean,
) : Disposable {
    private data class Entry(val conversation: Conversation?, val legacyId: String?, val label: String)
    private val history = project.getService(ConversationHistory::class.java)
    private var popup: JBPopup? = null
    private var disposed = false
    private var generation = 0

    init { owner.addHierarchyListener { if (!owner.isShowing) { generation++; popup?.cancel() } } }

    fun showPopup(event: AnActionEvent) {
        if (disposed || project.isDisposed || !owner.isShowing) return
        popup?.takeUnless { it.isDisposed }?.let { it.cancel(); return }
        val ticket = ++generation
        history.load { result -> SwingUtilities.invokeLater {
            if (disposed || project.isDisposed || !owner.isShowing || ticket != generation) return@invokeLater
            val loaded = result.getOrNull()
            if (loaded == null) {
                Messages.showErrorDialog(project, "履歴を読み込めませんでした。保存先の権限を確認してください。", "履歴")
                return@invokeLater
            }
            if (loaded.unreadable > 0) Messages.showWarningDialog(project, "${loaded.unreadable} 件の保存形式・破損を確認できません。該当ファイルは保持し、読める会話を表示します。", "履歴")
            val entries = loaded.conversations.map { Entry(it, null, "${it.preview} (${formatTimestamp(it.updatedMs)}) [${it.transport}]") } +
                chatHistoryState.list().filter { legacy -> loaded.conversations.none { it.transport == com.cursoragent.service.AgentTransport.PRINT && it.providerId == legacy.chatId } }
                    .map { Entry(null, it.chatId, "${it.firstPromptPreview} (${formatTimestamp(it.lastUpdatedMs)}) [本文なし]") }
            if (entries.isEmpty()) { Messages.showInfoMessage(project, "過去のチャットはまだありません", "履歴"); return@invokeLater }
            val next = JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
                .setTitle("履歴 — 選択して開く・削除")
                .setRenderer { _, value: Entry, _, _, _ -> javax.swing.JLabel(" ${value.label}") }
                .setItemChosenCallback { entry ->
                    if (!disposed && !project.isDisposed) {
                        val choice = Messages.showDialog(project, "${entry.label}\n本文は明示削除まで保持（上限100会話・各8 MiB）。未送信の下書きは保存しません。", "履歴", arrayOf("開く", "削除", "キャンセル"), 0, Messages.getQuestionIcon())
                        if (choice == 0 && (entry.conversation == null || !history.isDeleted(entry.conversation.id))) onChatResumed(entry.conversation, entry.legacyId)
                        if (choice == 1) delete(entry)
                    }
                }.createPopup()
            popup = next
            Disposer.register(next, Disposable { if (popup === next) popup = null })
            next.showInBestPositionFor(event.dataContext)
        } }
    }

    private fun delete(entry: Entry) {
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

    override fun dispose() { disposed = true; generation++; popup?.cancel(); popup = null }
    private fun formatTimestamp(epochMs: Long): String = SimpleDateFormat("MM/dd HH:mm").format(Date(epochMs))
}
