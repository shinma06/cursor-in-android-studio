package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationHistory
import com.cursoragent.history.ConversationStore
import com.cursoragent.settings.ChatHistoryState
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import javax.swing.SwingUtilities

internal data class HistoryPopupContext(val ownerId: String?, val visible: Boolean, val sidebar: Boolean, val allowed: Boolean)

/** Loading is off EDT. A hidden history request belongs to its chat, never to a later selection. */
internal class PastChatsCoordinator(
    private val project: Project,
    private val chatHistoryState: ChatHistoryState,
    private val onChatResumed: (Conversation?, String?, com.cursoragent.history.ConversationMatch?, String) -> Unit,
    private val isOpen: (String) -> Boolean,
    private val context: () -> HistoryPopupContext,
    private val onShowing: () -> Unit,
    private val load: ((Result<ConversationStore.Loaded>) -> Unit) -> Unit = project.getService(ConversationHistory::class.java)::load,
    private val showDialog: (HistorySearchDialog) -> Unit = { it.show() },
    private val showMenu: ((ConversationStore.Loaded, () -> Boolean, () -> Unit) -> Disposable)? = null,
) : Disposable {
    private val history = project.getService(ConversationHistory::class.java)
    private var dialog: HistorySearchDialog? = null
    private var menu: Disposable? = null
    private val management = mutableSetOf<String>()
    private var disposed = false
    private var generation = 0
    private val requested = mutableSetOf<String>()
    private var loadingOwner: String? = null
    private var dialogOwner: String? = null

    fun request(toggle: Boolean = false, manage: Boolean = false) {
        if (disposed || project.isDisposed) return
        val state = context()
        val id = state.ownerId ?: return
        if (state.sidebar || !state.allowed) return
        if (toggle && id in requested) {
            requested.remove(id)
            management.remove(id)
        } else {
            requested.add(id)
            if (manage) management.add(id)
        }
        refresh()
    }

    fun forget(id: String) {
        requested.remove(id)
        management.remove(id)
        refresh()
    }

    fun refresh() {
        if (disposed || project.isDisposed) { dispose(); return }
        val state = context()
        if (state.sidebar) state.ownerId?.let(requested::remove)
        if (state.visible && !state.allowed) state.ownerId?.let(requested::remove)
        management.retainAll(requested)
        val id = state.ownerId?.takeIf { state.visible && !state.sidebar && state.allowed && it in requested }
        if (loadingOwner != null && loadingOwner != id || dialogOwner != null && dialogOwner != id) {
            generation++
            loadingOwner = null
            dialogOwner = null
            val previous = dialog
            dialog = null
            previous?.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE)
            val previousMenu = menu
            menu = null
            previousMenu?.let(Disposer::dispose)
        }
        if (id == null || loadingOwner != null || dialogOwner != null) return
        loadingOwner = id
        val ticket = ++generation
        load { result -> SwingUtilities.invokeLater {
            if (ticket != generation) return@invokeLater
            loadingOwner = null
            if (!canDisplay(id)) {
                // A failed focus/IME guard must not open a delayed dialog after the user moves on.
                requested.remove(id)
                return@invokeLater
            }
            val loaded = result.getOrNull()
            if (loaded == null) {
                requested.remove(id)
                Messages.showErrorDialog(project, "履歴を読み込めませんでした。保存先の権限を確認してください。", "履歴")
                return@invokeLater
            }
            if (showMenu != null && id !in management) {
                dialogOwner = id
                onShowing()
                var closed = false
                val next = showMenu(loaded, { ticket == generation && canDisplay(id) }) {
                    closed = true
                    if (ticket == generation) {
                        requested.remove(id)
                        dialogOwner = null
                        menu = null
                    }
                }
                if (!closed && ticket == generation && canDisplay(id)) menu = next else Disposer.dispose(next)
                return@invokeLater
            }
            val entries = loaded.conversations.map { HistoryEntry(it, null, it.preview, it.updatedMs) } +
                chatHistoryState.list().filter { legacy -> loaded.conversations.none { it.transport == com.cursoragent.service.AgentTransport.PRINT && it.providerId == legacy.chatId } }
                    .map { HistoryEntry(null, it.chatId, it.firstPromptPreview, it.lastUpdatedMs) }
            val next = HistorySearchDialog(project, entries, loaded.unreadable,
                onOpen = { hit, query ->
                    val entry = hit.entry
                    if (ticket == generation && canDisplay(id) && (entry.conversation == null || !history.isDeleted(entry.conversation.id))) {
                        onChatResumed(entry.conversation, entry.legacyId, hit.match, query)
                    }
                }, onDelete = { if (ticket == generation && canDisplay(id)) delete(it) },
                onExport = { if (ticket == generation && canDisplay(id)) TranscriptExport(project).export(it) },
            )
            dialog = next
            dialogOwner = id
            onShowing()
            try { showDialog(next) } finally {
                if (ticket == generation) {
                    requested.remove(id)
                    management.remove(id)
                    dialogOwner = null
                    dialog = null
                }
            }
        } }
    }

    private fun canDisplay(id: String): Boolean {
        if (disposed || project.isDisposed || id !in requested) return false
        val state = context()
        return state.ownerId == id && state.visible && !state.sidebar && state.allowed
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

    override fun dispose() {
        disposed = true
        generation++
        requested.clear()
        management.clear()
        loadingOwner = null
        dialogOwner = null
        dialog?.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE)
        dialog = null
        val previousMenu = menu
        menu = null
        previousMenu?.let(Disposer::dispose)
    }
}
