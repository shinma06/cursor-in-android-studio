package com.cursoragent.ui

import com.cursoragent.settings.ChatHistoryRecord
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

/** Project-local history; its popup cannot outlive the owning ToolWindow content. */
class PastChatsCoordinator(
    private val project: Project,
    private val chatHistoryState: ChatHistoryState,
    private val owner: JComponent,
    private val onChatResumed: (String) -> Unit,
) : Disposable {
    private var popup: JBPopup? = null
    private var disposed = false

    init {
        owner.addHierarchyListener { if (!owner.isShowing) popup?.cancel() }
    }

    fun showPopup(event: AnActionEvent) {
        if (disposed || project.isDisposed || !owner.isShowing) return
        popup?.takeUnless { it.isDisposed }?.let { it.cancel(); return }
        val records = chatHistoryState.list()
        if (records.isEmpty()) {
            Messages.showInfoMessage(project, "過去のチャットはまだありません", "履歴")
            return
        }

        val next = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(records)
            .setTitle("履歴")
            .setRenderer { _, value: ChatHistoryRecord, _, _, _ ->
                javax.swing.JLabel(" ${value.firstPromptPreview}  (${formatTimestamp(value.lastUpdatedMs)})")
            }
            .setItemChosenCallback { record -> if (!disposed && !project.isDisposed) onChatResumed(record.chatId) }
            .createPopup()
        popup = next
        Disposer.register(next, Disposable { if (popup === next) popup = null })
        val anchor = event.inputEvent?.component as? JComponent
        if (anchor?.isShowing == true) next.showUnderneathOf(anchor)
        else next.showInBestPositionFor(event.dataContext)
    }

    override fun dispose() {
        disposed = true
        popup?.cancel()
        popup = null
    }

    private fun formatTimestamp(epochMs: Long): String =
        SimpleDateFormat("MM/dd HH:mm").format(Date(epochMs))
}
