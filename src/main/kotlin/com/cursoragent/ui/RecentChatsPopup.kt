package com.cursoragent.ui

import com.cursoragent.actions.AgentPanelActions
import com.cursoragent.actions.AgentPanelCommand
import com.cursoragent.history.ConversationHistory
import com.cursoragent.settings.ChatHistoryState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/** Native popup owns key delivery and disposal; no application-wide keyboard listener is installed. */
internal class RecentChatsPopup(
    private val project: Project,
    private val reverse: Boolean,
    private val visits: List<RecentChatId>,
    private val openEntries: () -> List<RecentChatEntry>,
    private val valid: () -> Boolean,
    private val onChoose: (RecentChatId) -> Unit,
) : Disposable {
    private val model = DefaultListModel<RecentChatEntry>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component =
                super.getListCellRendererComponent(list, (value as? RecentChatEntry)?.label.orEmpty(), index, selected, focus)
        }
        emptyText.text = "履歴を読み込み中…"
        accessibleContext.accessibleName = "最近使ったチャット"
        focusTraversalKeysEnabled = false
    }
    private val status = JLabel("Enterで開く・Escで取消")
    private val content = JPanel(BorderLayout()).apply {
        preferredSize = JBUI.size(520, 280)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }
    private val actions = AgentPanelActions(
        available = { !disposed && valid() && it in commands },
        perform = { command, _ ->
            quickShortcuts = shortcuts(command)
            if (!model.isEmpty) {
                val offset = if (command == AgentPanelCommand.RECENT_CHAT) 1 else -1
                list.selectedIndex = Math.floorMod(list.selectedIndex + offset, model.size())
                list.ensureIndexIsVisible(list.selectedIndex)
            }
        },
    )
    private var quickShortcuts = shortcuts(if (reverse) AgentPanelCommand.LEAST_RECENT_CHAT else AgentPanelCommand.RECENT_CHAT)
    private var disposed = false
    private val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, list)
        .setTitle("最近使ったチャット")
        .setProject(project)
        .setRequestFocus(true)
        .setResizable(true)
        .setCancelOnClickOutside(true)
        .setCancelOnOtherWindowOpen(true)
        .setCancelOnWindowDeactivation(true)
        .setKeyEventHandler { event ->
            if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ENTER) {
                choose(); true
            } else if (acceptsRecentChatRelease(event, quickShortcuts)) {
                // Release before loading completes leaves the picker open; it must not select later by itself.
                quickShortcuts = emptyList()
                choose(); true
            } else false
        }
        .createPopup()

    init {
        popup.setUiDataProvider { sink -> if (!disposed) sink[AgentPanelActions.KEY] = actions }
        commands.forEach { command ->
            ActionManager.getInstance().getAction(command.actionId)?.let { action ->
                action.registerCustomShortcutSet(action.shortcutSet, content, popup)
            }
        }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && SwingUtilities.isLeftMouseButton(event) &&
                    list.selectedIndex >= 0 && list.getCellBounds(list.selectedIndex, list.selectedIndex)?.contains(event.point) == true) choose()
            }
        })
        Disposer.register(popup, Disposable { disposed = true })
    }

    fun show(event: AnActionEvent) {
        if (!valid() || disposed) { dispose(); return }
        popup.showInBestPositionFor(event.dataContext)
        project.getService(ConversationHistory::class.java).load { result -> SwingUtilities.invokeLater {
            if (disposed || !valid()) { dispose(); return@invokeLater }
            val loaded = result.getOrNull()
            val entries = recentChatEntries(visits, openEntries(), loaded?.conversations.orEmpty(), ChatHistoryState.getInstance(project).list())
            entries.forEach(model::addElement)
            if (!model.isEmpty) {
                list.selectedIndex = if (reverse) model.size() - 1 else minOf(1, model.size() - 1)
                list.ensureIndexIsVisible(list.selectedIndex)
            }
            list.emptyText.text = "表示できる会話がありません"
            status.text = when {
                loaded == null -> "保存履歴を読み込めませんでした。開いている会話と旧履歴のみ表示しています。"
                loaded.unreadable > 0 -> "${loaded.unreadable}件は破損・未対応形式のため表示できません（ファイルは保持）。"
                else -> "Enterで開く・Escで取消"
            }
        } }
    }

    private fun choose() {
        val entry = list.selectedValue ?: return
        if (disposed || !valid()) { dispose(); return }
        popup.cancel()
        onChoose(entry.id)
    }

    override fun dispose() { if (!disposed) popup.cancel() }

    private fun shortcuts(command: AgentPanelCommand): List<KeyboardShortcut> =
        ActionManager.getInstance().getAction(command.actionId)?.shortcutSet?.shortcuts?.filterIsInstance<KeyboardShortcut>().orEmpty()

    companion object {
        private val commands = setOf(AgentPanelCommand.RECENT_CHAT, AgentPanelCommand.LEAST_RECENT_CHAT)
    }
}

/** Multi-stroke shortcuts require explicit Enter. Shift alone confirms only after other modifiers are up. */
internal fun acceptsRecentChatRelease(event: KeyEvent, shortcuts: List<KeyboardShortcut>): Boolean {
    if (event.id != KeyEvent.KEY_RELEASED) return false
    return shortcuts.any { shortcut ->
        if (shortcut.secondKeyStroke != null) return@any false
        val modifiers = shortcut.firstKeyStroke.modifiers
        when (event.keyCode) {
            KeyEvent.VK_CONTROL -> modifiers and InputEvent.CTRL_DOWN_MASK != 0
            KeyEvent.VK_META -> modifiers and InputEvent.META_DOWN_MASK != 0
            KeyEvent.VK_ALT -> modifiers and InputEvent.ALT_DOWN_MASK != 0
            KeyEvent.VK_SHIFT -> modifiers and InputEvent.SHIFT_DOWN_MASK != 0 &&
                event.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK) == 0
            else -> false
        }
    }
}
