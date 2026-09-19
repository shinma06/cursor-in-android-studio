package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationMatch
import com.cursoragent.history.findConversationText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Future
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

internal data class HistoryEntry(val conversation: Conversation?, val legacyId: String?, val preview: String, val updatedMs: Long) {
    val label: String get() = "${preview.ifBlank { "（本文なし）" }} (${SimpleDateFormat("MM/dd HH:mm").format(Date(updatedMs))}) [${conversation?.transport ?: "本文なし"}]"
}
internal data class HistoryHit(val entry: HistoryEntry, val match: ConversationMatch?)

internal fun searchHistory(entries: List<HistoryEntry>, query: String): List<HistoryHit> = entries.mapNotNull { entry ->
    if (Thread.currentThread().isInterrupted) return@mapNotNull null
    if (query.isEmpty()) return@mapNotNull HistoryHit(entry, null)
    val match = entry.conversation?.let { findConversationText(it, query) }
    if (match != null || entry.preview.contains(query, ignoreCase = true)) HistoryHit(entry, match) else null
}

/** The loaded snapshot belongs only to this dialog and is released when it closes. */
internal class HistorySearchDialog(
    private val project: Project,
    private val entries: List<HistoryEntry>,
    unreadable: Int,
    private val onOpen: (HistoryHit, String) -> Unit,
    private val onDelete: (HistoryEntry) -> Unit,
    private val onExport: (Conversation?) -> Unit,
) : DialogWrapper(project, false) {
    private val search = SearchTextField()
    private val model = DefaultListModel<HistoryHit>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SimpleListCellRenderer.create("") { hit: HistoryHit -> " ${hit.entry.label}" }
        emptyText.text = "保存された会話はありません"
    }
    private val preview = JTextArea(3, 48).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val status = JLabel(if (unreadable > 0) "$unreadable 件は破損・未対応形式のため検索できません（ファイルは保持）。" else "タイトルは冒頭文です。元入力・応答の本文を検索します。")
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
    private var worker: Future<*>? = null
    private var generation = 0
    private var composing = false
    private var closed = false
    private var appliedQuery = ""
    private var queryReady = true

    init {
        title = "保存した会話を検索"
        setOKButtonText("開く")
        setCancelButtonText("閉じる")
        search.textEditor.toolTipText = "タイトル（冒頭文）・元入力・応答を検索。旧履歴はタイトルのみです。"
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = scheduleSearch()
        })
        search.textEditor.addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(event: InputMethodEvent) {
                composing = event.committedCharacterCount < (event.text?.let { it.endIndex - it.beginIndex } ?: 0)
                scheduleSearch()
            }
            override fun caretPositionChanged(event: InputMethodEvent) = Unit
        })
        list.addListSelectionListener {
            val hit = list.selectedValue
            preview.text = hit?.match?.let { "一致箇所: ${it.excerpt}" }
                ?: if (hit?.entry?.conversation == null) "旧履歴には本文が保存されていません。" else "選択した会話を開くか、Markdownに書き出せます。"
            preview.caretPosition = 0
            isOKActionEnabled = queryReady && hit != null
        }
        init()
        applyHits(searchHistory(entries, ""), "")
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
        preferredSize = JBUI.size(640, 400)
        add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JLabel("タイトル（冒頭文）・本文を検索"), BorderLayout.NORTH)
            add(search, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(JBScrollPane(preview), BorderLayout.SOUTH)
    }

    override fun getPreferredFocusedComponent(): JComponent = search.textEditor

    private fun scheduleSearch() {
        val ticket = ++generation
        alarm.cancelAllRequests()
        worker?.cancel(true)
        queryReady = false
        isOKActionEnabled = false
        if (composing || closed) return
        val query = search.text.trim()
        alarm.addRequest({
            if (closed || composing || ticket != generation) return@addRequest
            worker = ApplicationManager.getApplication().executeOnPooledThread {
                val hits = searchHistory(entries, query)
                SwingUtilities.invokeLater {
                    if (!closed && !project.isDisposed && !composing && ticket == generation) applyHits(hits, query)
                }
            }
        }, 150)
    }

    private fun applyHits(hits: List<HistoryHit>, query: String) {
        queryReady = true
        appliedQuery = query
        model.clear()
        hits.forEach(model::addElement)
        list.emptyText.text = if (entries.isEmpty()) "保存された会話はありません" else "一致する会話はありません"
        if (!model.isEmpty) list.selectedIndex = 0 else { preview.text = ""; isOKActionEnabled = false }
    }

    override fun doOKAction() {
        if (!queryReady || composing || appliedQuery != search.text.trim()) return
        val hit = list.selectedValue ?: return
        close(OK_EXIT_CODE)
        onOpen(hit, appliedQuery)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, object : AbstractAction("Markdown出力…") {
        override fun actionPerformed(e: ActionEvent) {
            if (queryReady && !composing && appliedQuery == search.text.trim()) list.selectedValue?.entry?.let { onExport(it.conversation) }
        }
    }, object : AbstractAction("削除…") {
        override fun actionPerformed(e: ActionEvent) {
            if (!queryReady || composing || appliedQuery != search.text.trim()) return
            val entry = list.selectedValue?.entry ?: return
            close(CANCEL_EXIT_CODE)
            onDelete(entry)
        }
    }, cancelAction)

    override fun dispose() {
        closed = true
        generation++
        alarm.cancelAllRequests()
        worker?.cancel(true)
        super.dispose()
    }
}
