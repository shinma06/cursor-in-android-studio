package com.cursoragent.ui

import com.cursoragent.history.ConversationHistory
import com.cursoragent.settings.ChatHistoryState
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
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.Future
import javax.swing.DefaultListModel
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
    private val model = DefaultListModel<AllChatHit>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        accessibleContext.accessibleName = "すべてのチャット"
        emptyText.text = "履歴を読み込み中…"
        cellRenderer = object : ColoredListCellRenderer<AllChatHit>() {
            override fun customizeCellRenderer(list: JList<out AllChatHit>, value: AllChatHit?, index: Int, selected: Boolean, hasFocus: Boolean) {
                val hit = value ?: return
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
    private val status = JLabel(" ")
    private val debounce = Timer(150) { runSearch() }.apply { isRepeats = false }
    private var worker: Future<*>? = null
    private var stored = emptyList<RecentChatEntry>()
    private var entries = emptyList<RecentChatEntry>()
    private var loadGeneration = 0
    private var searchGeneration = 0
    private var disposed = false
    private var ready = false
    private var appliedQuery = ""
    private var loadStatus = ""
    private var navigationId: RecentChatId? = null
    var isComposing = false
        private set
    val focusComponent: JComponent get() = search.textEditor

    init {
        border = JBUI.Borders.empty(4)
        search.textEditor.accessibleContext.accessibleName = "チャットの名前・冒頭文を検索"
        search.textEditor.toolTipText = "名前・冒頭文で検索します。"
        add(search, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = scheduleSearch()
        })
        search.textEditor.addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(event: InputMethodEvent) {
                isComposing = event.committedCharacterCount < (event.text?.let { it.endIndex - it.beginIndex } ?: 0)
                scheduleSearch()
            }
            override fun caretPositionChanged(event: InputMethodEvent) = Unit
        })
        for (component in listOf(search.textEditor, list)) {
            component.registerKeyboardAction({ choose() }, KeyStroke.getKeyStroke("ENTER"), JComponent.WHEN_FOCUSED)
        }
        for ((key, offset) in listOf("UP" to -1, "DOWN" to 1)) {
            search.textEditor.registerKeyboardAction({
                if (!isComposing && ready && !model.isEmpty) {
                    list.selectedIndex = (list.selectedIndex + offset).coerceIn(0, model.size() - 1)
                    list.ensureIndexIsVisible(list.selectedIndex)
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
        val ticket = ++loadGeneration
        store.load { result -> SwingUtilities.invokeLater {
            if (disposed || !valid() || ticket != loadGeneration) return@invokeLater
            val loaded = result.getOrNull()
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
        worker?.cancel(true)
        debounce.stop()
        if (!disposed && valid() && !isComposing) debounce.restart()
    }

    private fun runSearch() {
        if (disposed || !valid() || isComposing) return
        val ticket = searchGeneration
        val query = search.text
        val snapshot = entries
        val selection = list.selectedValue?.entry?.id ?: selectedId()
        worker = ApplicationManager.getApplication().executeOnPooledThread {
            val hits = searchAllChats(snapshot, query, limit, rankMatches = quickAccess)
            SwingUtilities.invokeLater {
                if (disposed || !valid() || isComposing || ticket != searchGeneration) return@invokeLater
                if (navigationId != null && hits.none { it.entry.id == navigationId }) navigationId = null
                appliedQuery = query
                ready = true
                model.clear()
                hits.forEach(model::addElement)
                list.emptyText.text = if (query.isBlank()) "表示できるチャットはありません" else "一致するチャットはありません"
                if (!model.isEmpty) list.selectedIndex = hits.indexOfFirst { it.entry.id == (navigationId ?: selection) }.coerceAtLeast(0)
                if (list.selectedIndex >= 0) list.ensureIndexIsVisible(list.selectedIndex)
                status.text = loadStatus.ifBlank { if (hits.size == limit) "先頭${limit}件を表示・検索で絞り込めます" else "${hits.size}件" }
            }
        }
    }

    fun navigate(reverse: Boolean): Boolean {
        if (disposed || !valid() || isComposing || !ready) return false
        val ids = (0 until model.size()).map { model[it].entry.id }
        val next = adjacentSidebarChat(ids, selectedId(), navigationId, reverse) ?: return false
        navigationId = next
        list.selectedIndex = ids.indexOf(next)
        list.ensureIndexIsVisible(list.selectedIndex)
        return true
    }

    fun confirmNavigation() {
        val id = navigationId
        navigationId = null
        if (!disposed && valid() && !isComposing && ready && appliedQuery == search.text &&
            id != null && (0 until model.size()).any { model[it].entry.id == id }) onChoose(id)
    }

    fun cancelNavigation() {
        navigationId = null
        val current = selectedId()
        list.selectedIndex = (0 until model.size()).firstOrNull { model[it].entry.id == current } ?: -1
    }

    private fun choose() {
        if (disposed || !valid() || isComposing || !ready || appliedQuery != search.text) return
        list.selectedValue?.entry?.id?.let(onChoose)
    }

    override fun dispose() {
        disposed = true
        loadGeneration++
        searchGeneration++
        debounce.stop()
        worker?.cancel(true)
        stored = emptyList()
        entries = emptyList()
        model.clear()
    }
}
