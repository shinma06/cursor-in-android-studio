package com.cursoragent.ui

import com.cursoragent.history.ConversationHistory
import com.cursoragent.history.ConversationStore
import com.cursoragent.settings.ChatHistoryState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Future
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent

internal enum class ChatListMode { SIDEBAR, QUICK_ACCESS, HISTORY }

internal data class ChatArchiveRequest(
    val entry: RecentChatEntry,
    val fromSidebar: Boolean,
    val candidates: List<RecentChatEntry>,
    val isCurrent: () -> Boolean,
)

/** Metadata-only views; the owning window reloads a selected body. */
internal class AllChatsView(
    private val project: Project,
    private val mode: ChatListMode,
    private val openEntries: () -> List<RecentChatEntry>,
    private val selectedId: () -> RecentChatId?,
    private val valid: () -> Boolean,
    private val onChoose: (RecentChatId) -> Unit,
    private val onArchive: (ChatArchiveRequest) -> Unit,
    private val onManage: (() -> Unit)? = null,
) : JPanel(BorderLayout(0, JBUI.scale(4))), Disposable {
    private val pageSize = if (mode == ChatListMode.HISTORY) 20 else 6
    private val store = project.getService(ConversationHistory::class.java)
    private val search = SearchTextField()
    private val properties by lazy { PropertiesComponent.getInstance(project) }
    private val archive by lazy { ChatArchiveState(properties) }
    private fun readPins() = properties.getList("CursorAgent.pinnedChats").orEmpty().mapNotNull(::pinnedChatId).toSet()
    private var pinned = readPins()
    private val collapsed = if (mode != ChatListMode.SIDEBAR) mutableSetOf(ChatSection.ARCHIVED) else ChatSection.entries.filter {
        properties.getBoolean("CursorAgent.chatSection.${it.key}.collapsed", it == ChatSection.ARCHIVED)
    }.toMutableSet()
    private val sectionLimits = mutableMapOf<ChatSection, Int>()
    private val model = DefaultListModel<AllChatRow>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        accessibleContext.accessibleName = "すべてのチャット"
        emptyText.text = "履歴を読み込み中…"
        cellRenderer = object : ColoredListCellRenderer<AllChatRow>() {
            override fun customizeCellRenderer(list: JList<out AllChatRow>, value: AllChatRow?, index: Int, selected: Boolean, hasFocus: Boolean) {
                toolTipText = when (value) {
                    is AllChatRow.Section -> "${value.section.label}、${value.count}件。Enterで${if (value.collapsed) "展開" else "折り畳み"}。"
                    is AllChatRow.More -> "${value.section.label}を${pageSize}件追加表示します。"
                    is AllChatRow.Chat -> value.hit.entry.title
                    null -> null
                }
                getAccessibleContext().accessibleName = toolTipText
                val hit = when (value) {
                    is AllChatRow.Chat -> value.hit
                    is AllChatRow.Section -> {
                        append("${if (value.collapsed) "▸" else "▾"} ${value.section.label} (${value.count})", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                        return
                    }
                    is AllChatRow.More -> {
                        append("さらに表示（${value.section.label}）", SimpleTextAttributes.LINK_ATTRIBUTES)
                        return
                    }
                    null -> return
                }
                var offset = 0
                hit.highlights.forEach { range ->
                    append(hit.entry.title.substring(offset, range.startOffset), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(hit.entry.title.substring(range.startOffset, range.endOffset), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    offset = range.endOffset
                }
                append(hit.entry.title.substring(offset), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (hit.entry.description.isNotBlank() && hit.entry.description != hit.entry.title)
                    append("  ${hit.entry.description}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(if (hit.entry.running) "  実行中" else if (hit.entry.open) "  開いている" else "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                if (hit.entry.id is RecentChatId.LegacyPrint) append("  旧履歴・本文なし", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
    private val scroll = JBScrollPane(list)
    private var suspendedPosition: Point? = null
    private val status = JLabel(" ")
    private val pinButton = JButton("一覧に固定").apply { addActionListener { togglePin() } }
    private val archiveButton = JButton("アーカイブ").apply { addActionListener { toggleArchive() } }
    private var renderedDate = LocalDate.now()
    private val dateRefresh = Timer(60_000) {
        if (mode == ChatListMode.SIDEBAR && !disposed && valid() && ready && !isComposing && LocalDate.now() != renderedDate) rebuildRows()
    }
    private val debounce = Timer(150) { runSearch() }.apply { isRepeats = false }
    private var worker: Future<*>? = null
    private var stored = emptyList<RecentChatEntry>()
    private var entries = emptyList<RecentChatEntry>()
    private var hits = emptyList<AllChatHit>()
    private var rows = emptyList<AllChatRow>()
    private var historyComplete = false
    private var loadGeneration = 0
    private var searchGeneration = 0
    private var disposed = false
    private var ready = false
    private var appliedQuery = ""
    private var loadStatus = ""
    private var navigationTarget: SidebarChatTarget? = null
    var isComposing = false
        private set
    val focusComponent: JComponent get() = search.textEditor

    init {
        border = JBUI.Borders.empty(4)
        search.textEditor.accessibleContext.accessibleName = "チャットの名前・冒頭文を検索"
        search.textEditor.toolTipText = "名前・冒頭文で検索します。"
        add(search, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(status, BorderLayout.CENTER)
            add(JPanel().apply {
                if (mode != ChatListMode.QUICK_ACCESS) {
                    add(pinButton)
                    add(archiveButton)
                }
                onManage?.let { manage -> add(JButton("保存した会話を検索…").apply {
                    addActionListener { if (!disposed && valid() && !isComposing) manage() }
                }) }
            }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        list.addListSelectionListener { updatePinButton() }
        updatePinButton()
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                suspendedPosition = null
                navigationTarget = null
                if (mode == ChatListMode.HISTORY) sectionLimits.clear()
                scheduleSearch()
            }
        })
        search.textEditor.addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(event: InputMethodEvent) {
                isComposing = event.committedCharacterCount < (event.text?.let { it.endIndex - it.beginIndex } ?: 0)
                scheduleSearch()
            }
            override fun caretPositionChanged(event: InputMethodEvent) = Unit
        })
        for (component in listOf(search.textEditor, list)) {
            component.registerKeyboardAction({ choose(allowSection = component === list) }, KeyStroke.getKeyStroke("ENTER"), JComponent.WHEN_FOCUSED)
        }
        for ((key, offset) in listOf("UP" to -1, "DOWN" to 1)) {
            search.textEditor.registerKeyboardAction({
                if (!isComposing && ready && !model.isEmpty) {
                    val next = adjacentSidebarChat(rows.mapNotNull { it.target }, list.selectedValue?.target, null, offset < 0)
                    list.selectedIndex = rows.indexOfFirst { it.target != null && it.target == next }
                    if (list.selectedIndex >= 0) list.ensureIndexIsVisible(list.selectedIndex)
                }
            }, KeyStroke.getKeyStroke(key), JComponent.WHEN_FOCUSED)
        }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(event) && list.selectedIndex >= 0 &&
                    list.getCellBounds(list.selectedIndex, list.selectedIndex)?.contains(event.point) == true) choose()
            }
        })
    }

    fun reload() {
        if (disposed || !valid()) return
        if (mode == ChatListMode.SIDEBAR) dateRefresh.start()
        val ticket = ++loadGeneration
        historyComplete = false
        if (!ready) status.text = "履歴を読み込み中…"
        updatePinButton()
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || !valid() || ticket != loadGeneration) return@invokeLater
            useLoadedHistory(result.getOrNull())
        } }
    }

    fun useLoadedHistory(loaded: ConversationStore.Loaded?) {
        if (disposed || !valid()) return
        historyComplete = loaded != null && loaded.unreadable == 0
        stored = availableChatEntries(emptyList(), loaded?.conversations.orEmpty().filterNot { store.isDeleted(it.id) }, emptyList())
        loadStatus = when {
            loaded == null -> "保存履歴を読み込めませんでした。"
            loaded.unreadable > 0 -> "${loaded.unreadable}件は読み込めませんでした（ファイルは保持）。"
            else -> ""
        }
        refreshOpenEntries()
    }

    fun refreshOpenEntries() {
        if (disposed || !valid()) return
        val live = availableChatEntries(openEntries(), emptyList(), ChatHistoryState.getInstance(project).list())
        pinned = readPins()
        entries = archive.apply(mergeChatEntries(live, stored.filterNot { it.id is RecentChatId.Body && store.isDeleted(it.id.id) }))
        scheduleSearch()
    }

    private fun scheduleSearch() {
        searchGeneration++
        ready = false
        updatePinButton()
        worker?.cancel(true)
        debounce.stop()
        if (!disposed && valid() && !isComposing) debounce.restart()
    }

    private fun runSearch() {
        if (disposed || !valid() || isComposing) return
        val ticket = searchGeneration
        val query = search.text
        val snapshot = entries
        val selection = list.selectedValue?.target ?: selectedId()?.let(SidebarChatTarget::Chat)
        worker = ApplicationManager.getApplication().executeOnPooledThread {
            val found = if (mode == ChatListMode.QUICK_ACCESS) searchAllChats(snapshot.filterNot { it.archived }, query)
            else searchSidebarChats(snapshot, query)
            SwingUtilities.invokeLater {
                if (disposed || !valid() || isComposing || ticket != searchGeneration) return@invokeLater
                hits = found
                appliedQuery = query
                ready = true
                rebuildRows(selection)
            }
        }
    }

    private fun rebuildRows(preferred: SidebarChatTarget? = list.selectedValue?.target) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        renderedDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        rows = when (mode) {
            ChatListMode.QUICK_ACCESS -> hits.map(AllChatRow::Chat)
            ChatListMode.HISTORY -> historyChatRows(hits, pinned, ChatSection.ARCHIVED in collapsed, sectionLimits, appliedQuery.isBlank())
            ChatListMode.SIDEBAR -> sidebarChatRows(hits, pinned, collapsed, sectionLimits, now, zone)
        }
        if (navigationTarget != null && rows.none { it.target == navigationTarget }) navigationTarget = null
        model.clear()
        rows.forEach(model::addElement)
        list.emptyText.text = if (appliedQuery.isBlank()) "表示できるチャットはありません" else "一致するチャットはありません"
        val target = navigationTarget ?: preferred ?: selectedId()?.let(SidebarChatTarget::Chat)
        val index = rows.indexOfFirst { it.target != null && it.target == target }
        list.selectedIndex = if (index >= 0) index else rows.indexOfFirst { it.target != null }
        val previousPosition = suspendedPosition
        suspendedPosition = null
        if (previousPosition != null) scroll.viewport.viewPosition = previousPosition
        else if (list.selectedIndex >= 0) list.ensureIndexIsVisible(list.selectedIndex)
        status.text = loadStatus.ifBlank {
            if (mode == ChatListMode.QUICK_ACCESS && hits.size == 200) "200件まで表示・検索で絞り込めます" else "${hits.size}件"
        }
        updatePinButton()
    }

    fun navigate(reverse: Boolean): Boolean {
        if (disposed || !valid() || isComposing || !ready) return false
        val next = adjacentSidebarChat(rows.mapNotNull { it.target }, selectedId()?.let(SidebarChatTarget::Chat), navigationTarget, reverse) ?: return false
        navigationTarget = next
        list.selectedIndex = rows.indexOfFirst { it.target == next }
        list.ensureIndexIsVisible(list.selectedIndex)
        return true
    }

    fun confirmNavigation() {
        val target = navigationTarget
        navigationTarget = null
        if (!disposed && valid() && !isComposing && ready && appliedQuery == search.text && target != null)
            rows.firstOrNull { it.target == target }?.let(::activate)
    }

    fun cancelNavigation() {
        navigationTarget = null
        val current = selectedId()?.let(SidebarChatTarget::Chat)
        list.selectedIndex = rows.indexOfFirst { it.target != null && it.target == current }
    }

    private fun choose(allowSection: Boolean = true) {
        if (disposed || !valid() || isComposing || !ready || appliedQuery != search.text) return
        val row = list.selectedValue ?: return
        if (allowSection || row !is AllChatRow.Section) activate(row)
    }

    private fun activate(row: AllChatRow) {
        navigationTarget = null
        when (row) {
            is AllChatRow.Chat -> {
                if (entryAvailable(row.hit.entry)) onChoose(row.hit.entry.id) else refreshOpenEntries()
            }
            is AllChatRow.More -> {
                val firstAdded = rows.indexOf(row)
                sectionLimits[row.section] = (sectionLimits[row.section] ?: pageSize) + pageSize
                rebuildRows(selectedId()?.let(SidebarChatTarget::Chat))
                if (firstAdded in rows.indices) list.ensureIndexIsVisible(firstAdded)
            }
            is AllChatRow.Section -> {
                if (!collapsed.remove(row.section)) collapsed.add(row.section)
                if (mode == ChatListMode.SIDEBAR) properties.setValue("CursorAgent.chatSection.${row.section.key}.collapsed", row.section in collapsed, row.section == ChatSection.ARCHIVED)
                rebuildRows()
                list.selectedIndex = rows.indexOfFirst { it is AllChatRow.Section && it.section == row.section }
            }
        }
    }

    private fun updatePinButton() {
        val entry = (list.selectedValue as? AllChatRow.Chat)?.hit?.entry
        val actionable = !disposed && valid() && ready && !isComposing && appliedQuery == search.text && entry != null && entryAvailable(entry)
        archiveButton.text = if (entry?.archived == true) "復元" else "アーカイブ"
        archiveButton.toolTipText = if (entry?.archived == true) "通常の一覧へ戻します。会話の送信は再開しません。" else "この会話の実行を停止し、本文を保持してアーカイブへ移します。"
        archiveButton.isEnabled = actionable && mode != ChatListMode.QUICK_ACCESS
        if (mode == ChatListMode.QUICK_ACCESS) return
        val alreadyPinned = entry?.id in pinned
        val available = entries.filterNot { it.archived }.map { it.id }.toSet()
        val belowLimit = pinned.count { it in available } < 75
        pinButton.text = if (alreadyPinned) "固定を解除" else "一覧に固定"
        pinButton.isEnabled = actionable && !entry.archived &&
            (alreadyPinned || historyComplete && belowLimit)
        pinButton.toolTipText = when {
            alreadyPinned -> "このチャットの一覧への固定を解除します。"
            !historyComplete -> "履歴の読込みが完了してから固定できます。"
            !belowLimit -> "固定できるチャットは75件までです。"
            else -> "このチャットを一覧の先頭の区分に固定します。"
        }
    }

    private fun togglePin() {
        if (mode == ChatListMode.QUICK_ACCESS) return
        if (disposed || !valid() || !ready || isComposing || appliedQuery != search.text) return
        val entry = (list.selectedValue as? AllChatRow.Chat)?.hit?.entry ?: return
        if (!entryAvailable(entry) || entry.archived) { refreshOpenEntries(); return }
        pinned = readPins()
        if (entry.id !in pinned && !historyComplete) return
        val updated = togglePinnedChat(pinned, entry.id, entries.filterNot { it.archived }.map { it.id }.toSet()) ?: return
        pinned = updated
        properties.setList("CursorAgent.pinnedChats", pinned.map(::pinnedChatKey))
        navigationTarget = null
        val target = SidebarChatTarget.Chat(entry.id)
        rebuildRows(target)
        // A collapsed destination must not leave the pin button targeting a different chat.
        list.selectedIndex = rows.indexOfFirst { it.target == target }
    }

    private fun entryAvailable(entry: RecentChatEntry): Boolean = archive.matches(entry) &&
        !(entry.id is RecentChatId.Body && store.isDeleted(entry.id.id)) &&
        (entry.id !is RecentChatId.LegacyPrint || ChatHistoryState.getInstance(project).list().any { it.chatId == entry.id.id })

    private fun toggleArchive() {
        if (mode == ChatListMode.QUICK_ACCESS) return
        if (disposed || !valid() || !ready || isComposing || appliedQuery != search.text) return
        val entry = (list.selectedValue as? AllChatRow.Chat)?.hit?.entry ?: return
        if (entryAvailable(entry)) {
            val ticket = searchGeneration
            val candidates = if (mode == ChatListMode.SIDEBAR) sidebarChatRows(
                hits, pinned, emptySet(), ChatSection.entries.associateWith { Int.MAX_VALUE },
                System.currentTimeMillis(), ZoneId.systemDefault(),
            ).filterIsInstance<AllChatRow.Chat>().map { it.hit.entry } else emptyList()
            onArchive(ChatArchiveRequest(entry, mode == ChatListMode.SIDEBAR, candidates) {
                !disposed && valid() && ready && !isComposing && ticket == searchGeneration &&
                    appliedQuery == search.text && (list.selectedValue as? AllChatRow.Chat)?.hit?.entry == entry && entryAvailable(entry)
            })
        }
        navigationTarget = null
        refreshOpenEntries()
    }

    /** Hiding retains the query, rows and expansion state, but rejects every callback from the old display. */
    fun suspendUpdates() {
        if (mode == ChatListMode.SIDEBAR && ready) suspendedPosition = scroll.viewport.viewPosition
        loadGeneration++
        searchGeneration++
        debounce.stop()
        dateRefresh.stop()
        worker?.cancel(true)
        worker = null
        ready = false
        navigationTarget = null
        updatePinButton()
    }

    override fun dispose() {
        disposed = true
        suspendUpdates()
        suspendedPosition = null
        hits = emptyList()
        rows = emptyList()
        stored = emptyList()
        entries = emptyList()
        model.clear()
    }
}
