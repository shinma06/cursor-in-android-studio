package com.cursoragent.ui

import com.cursoragent.history.ConversationHistory
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

/** Shared metadata view for the persistent list and quick access; selected bodies are reloaded by the owner. */
internal class AllChatsView(
    private val project: Project,
    private val quickAccess: Boolean,
    private val openEntries: () -> List<RecentChatEntry>,
    private val selectedId: () -> RecentChatId?,
    private val valid: () -> Boolean,
    private val onChoose: (RecentChatId) -> Unit,
) : JPanel(BorderLayout(0, JBUI.scale(4))), Disposable {
    private val limit = if (quickAccess) 200 else Int.MAX_VALUE
    private val store = project.getService(ConversationHistory::class.java)
    private val search = SearchTextField()
    private val properties by lazy { PropertiesComponent.getInstance(project) }
    private var pinned = if (quickAccess) emptySet() else properties.getList("CursorAgent.pinnedChats").orEmpty().mapNotNull(::pinnedChatId).toSet()
    private val collapsed = if (quickAccess) mutableSetOf() else ChatSection.entries.filter {
        properties.getBoolean("CursorAgent.chatSection.${it.key}.collapsed", false)
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
                    is AllChatRow.More -> "${value.section.label}を6件追加表示します。"
                    is AllChatRow.Chat -> value.hit.entry.title
                    null -> null
                }
                accessibleContext.accessibleName = toolTipText
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
    private var renderedDate = LocalDate.now()
    private val dateRefresh = Timer(60_000) {
        if (!quickAccess && !disposed && valid() && ready && !isComposing && LocalDate.now() != renderedDate) rebuildRows()
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
            if (!quickAccess) add(pinButton, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        list.addListSelectionListener { updatePinButton() }
        updatePinButton()
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { suspendedPosition = null; navigationTarget = null; scheduleSearch() }
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
        if (!quickAccess) dateRefresh.start()
        val ticket = ++loadGeneration
        historyComplete = false
        if (!ready) status.text = "履歴を読み込み中…"
        updatePinButton()
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || !valid() || ticket != loadGeneration) return@invokeLater
            val loaded = result.getOrNull()
            historyComplete = loaded != null && loaded.unreadable == 0
            stored = availableChatEntries(emptyList(), loaded?.conversations.orEmpty().filterNot { store.isDeleted(it.id) }, emptyList())
            loadStatus = when {
                loaded == null -> "保存履歴を読み込めませんでした。"
                loaded.unreadable > 0 -> "${loaded.unreadable}件は読み込めませんでした（ファイルは保持）。"
                else -> ""
            }
            refreshOpenEntries()
        } }
    }

    fun refreshOpenEntries() {
        if (disposed || !valid()) return
        val live = availableChatEntries(openEntries(), emptyList(), ChatHistoryState.getInstance(project).list())
        entries = mergeChatEntries(live, stored.filterNot { it.id is RecentChatId.Body && store.isDeleted(it.id.id) })
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
            val found = if (quickAccess) searchAllChats(snapshot, query) else searchSidebarChats(snapshot, query)
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
        rows = if (quickAccess) hits.map(AllChatRow::Chat) else
            sidebarChatRows(hits, pinned, collapsed, sectionLimits, now, zone)
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
            if (quickAccess && hits.size == limit) "先頭${limit}件を表示・検索で絞り込めます" else "${hits.size}件"
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
            is AllChatRow.Chat -> onChoose(row.hit.entry.id)
            is AllChatRow.More -> {
                val firstAdded = rows.indexOf(row)
                sectionLimits[row.section] = (sectionLimits[row.section] ?: 6) + 6
                rebuildRows(selectedId()?.let(SidebarChatTarget::Chat))
                if (firstAdded in rows.indices) list.ensureIndexIsVisible(firstAdded)
            }
            is AllChatRow.Section -> {
                if (!collapsed.remove(row.section)) collapsed.add(row.section)
                properties.setValue("CursorAgent.chatSection.${row.section.key}.collapsed", row.section in collapsed, false)
                rebuildRows()
                list.selectedIndex = rows.indexOfFirst { it is AllChatRow.Section && it.section == row.section }
            }
        }
    }

    private fun updatePinButton() {
        if (quickAccess) return
        val entry = (list.selectedValue as? AllChatRow.Chat)?.hit?.entry
        val alreadyPinned = entry?.id in pinned
        val available = entries.map { it.id }.toSet()
        val belowLimit = pinned.count { it in available } < 75
        pinButton.text = if (alreadyPinned) "固定を解除" else "一覧に固定"
        pinButton.isEnabled = !disposed && valid() && ready && !isComposing && entry != null &&
            (alreadyPinned || historyComplete && belowLimit)
        pinButton.toolTipText = when {
            alreadyPinned -> "このチャットの一覧への固定を解除します。"
            !historyComplete -> "履歴の読込みが完了してから固定できます。"
            !belowLimit -> "固定できるチャットは75件までです。"
            else -> "このチャットを一覧の先頭の区分に固定します。"
        }
    }

    private fun togglePin() {
        if (disposed || !valid() || !ready || isComposing || appliedQuery != search.text) return
        val entry = (list.selectedValue as? AllChatRow.Chat)?.hit?.entry ?: return
        if (entry.id !in pinned && !historyComplete) return
        val updated = togglePinnedChat(pinned, entry.id, entries.map { it.id }.toSet()) ?: return
        pinned = updated
        properties.setList("CursorAgent.pinnedChats", pinned.map(::pinnedChatKey))
        navigationTarget = null
        val target = SidebarChatTarget.Chat(entry.id)
        rebuildRows(target)
        // A collapsed destination must not leave the pin button targeting a different chat.
        list.selectedIndex = rows.indexOfFirst { it.target == target }
    }

    /** Hiding retains the query, rows and expansion state, but rejects every callback from the old display. */
    fun suspendUpdates() {
        if (!quickAccess && ready) suspendedPosition = scroll.viewport.viewPosition
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
