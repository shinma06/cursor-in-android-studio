package com.cursoragent.ui

import com.cursoragent.actions.AgentQueueActions
import com.cursoragent.actions.AgentQueueCommand
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel

/** Queue edits use stable IDs and the same live ownership check as the management buttons. */
internal class PromptQueueList(
    private val queue: PromptQueue,
    private val isCurrent: () -> Boolean,
    private val shortcutAvailable: () -> Boolean,
    private val onEdit: (QueuedPrompt) -> Unit,
    private val onChanged: () -> Unit,
    private val onReturnToInput: () -> Unit,
) : JBList<QueuedPrompt>(DefaultListModel()), UiDataProvider {
    private val rows get() = model as DefaultListModel<QueuedPrompt>
    internal val actions = AgentQueueActions(
        available = { command ->
            isCurrent() && shortcutAvailable() &&
                (command == AgentQueueCommand.RETURN_TO_INPUT || currentEntry() != null)
        },
        perform = ::perform,
    )

    init {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        getAccessibleContext().accessibleName = "予約した入力"
        cellRenderer = SimpleListCellRenderer.create("") { value: QueuedPrompt ->
            (if (value.image != null) "[画像あり] " else "") + "${value.mode.name.lowercase().replaceFirstChar { it.titlecase() }} / ${value.model.ifBlank { "既定モデル" }} — ${com.cursoragent.service.commandPrompt(value.command, value.text).replace('\n', ' ').take(100)}"
        }
        emptyText.text = "予約した入力はありません"
    }

    override fun uiDataSnapshot(sink: DataSink) {
        if (isFocusOwner && isCurrent()) sink[AgentQueueActions.KEY] = actions
    }

    fun installShortcuts(parent: Disposable) {
        AgentQueueCommand.entries.forEach { command ->
            ActionManager.getInstance().getAction(command.actionId)?.let { action ->
                action.registerCustomShortcutSet(action.shortcutSet, this, parent)
            }
        }
    }

    /** Up enters at the last queued item; Down enters at the second, or the only item. */
    fun selectFromPrompt(reverse: Boolean): Boolean {
        if (!isCurrent()) return false
        refresh()
        if (rows.isEmpty) return false
        selectedIndex = if (reverse) rows.size() - 1 else minOf(1, rows.size() - 1)
        ensureIndexIsVisible(selectedIndex)
        return true
    }

    fun refresh() {
        val selected = selectedValue?.id
        val oldIndex = selectedIndex
        val snapshot = queue.snapshot()
        rows.clear()
        snapshot.forEach(rows::addElement)
        if (snapshot.isNotEmpty()) {
            val retained = snapshot.indexOfFirst { it.id == selected }
            selectedIndex = if (retained >= 0) retained else oldIndex.coerceIn(0, snapshot.lastIndex)
            ensureIndexIsVisible(selectedIndex)
        }
    }

    fun withSelected(action: (QueuedPrompt) -> Unit) {
        if (!isCurrent()) return
        currentEntry()?.let(action)
        if (isCurrent()) { refresh(); onChanged() }
    }

    private fun currentEntry(): QueuedPrompt? = selectedValue?.id?.let { id -> queue.snapshot().find { it.id == id } }

    private fun perform(command: AgentQueueCommand) {
        // Recheck even when a previous Action update enabled this command.
        if (!actions.available(command)) return
        when (command) {
            AgentQueueCommand.RETURN_TO_INPUT -> onReturnToInput()
            AgentQueueCommand.EDIT -> withSelected(onEdit)
            AgentQueueCommand.REMOVE -> withSelected { queue.remove(it.id) }
            AgentQueueCommand.PREVIOUS -> {
                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                ensureIndexIsVisible(selectedIndex)
            }
            AgentQueueCommand.NEXT -> {
                if (selectedIndex == rows.size() - 1) onReturnToInput()
                else { selectedIndex++; ensureIndexIsVisible(selectedIndex) }
            }
        }
    }
}
