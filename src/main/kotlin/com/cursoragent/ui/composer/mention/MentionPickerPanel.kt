package com.cursoragent.ui.composer.mention

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Search and cancel stay responsive while project candidates load off EDT. */
internal class MentionPickerPanel(private val choose: (Mention) -> Unit, private val cancel: () -> Unit) : JPanel(BorderLayout(4, 4)) {
    val search = JTextField()
    val model = DefaultListModel<Mention>()
    val list = JBList(model)
    private val preview = JLabel("候補を読み込み中…")
    private var candidates = emptyList<Mention>()
    private var status = "候補を読み込み中…"
    private var composing = false

    init {
        preferredSize = JBUI.size(420, 280)
        border = JBUI.Borders.empty(6)
        search.getAccessibleContext().accessibleName = "context候補を検索"
        list.cellRenderer = MentionListCellRenderer()
        list.emptyText.text = status
        preview.putClientProperty("html.disable", true)
        add(search, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(preview, BorderLayout.SOUTH)
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filter()
            override fun removeUpdate(e: DocumentEvent) = filter()
            override fun changedUpdate(e: DocumentEvent) = filter()
        })
        search.addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(e: InputMethodEvent) {
                composing = e.text?.let { it.endIndex - it.beginIndex > e.committedCharacterCount } == true
            }
            override fun caretPositionChanged(e: InputMethodEvent) = Unit
        })
        list.addListSelectionListener {
            preview.text = list.selectedValue?.insertToken ?: status
            preview.toolTipText = list.selectedValue?.contextDescription() ?: status
        }
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    val index = list.locationToIndex(e.point)
                    if (index >= 0 && list.getCellBounds(index, index).contains(e.point)) choose(model.get(index))
                }
            }
        })
        for (target in listOf(search, list)) {
            bind(target, "ENTER") { if (!composing) list.selectedValue?.let(choose) }
            bind(target, "ESCAPE") { if (!composing) cancel() }
        }
        bind(search, "DOWN") { if (!model.isEmpty) list.selectedIndex = (list.selectedIndex + 1).coerceAtMost(model.size - 1) }
        bind(search, "UP") { if (!model.isEmpty) list.selectedIndex = (list.selectedIndex - 1).coerceAtLeast(0) }
    }

    fun loaded(values: List<Mention>) { candidates = values; status = "一致する候補はありません"; filter() }
    fun failed() { candidates = emptyList(); status = "候補を取得できませんでした。閉じて再度追加してください。"; filter() }

    private fun filter() {
        val query = search.text.trim()
        model.clear()
        candidates.filter { it.displayLabel.contains(query, ignoreCase = true) || it.insertToken.contains(query, ignoreCase = true) }.forEach(model::addElement)
        list.emptyText.text = status
        if (!model.isEmpty) list.selectedIndex = 0
        preview.text = list.selectedValue?.insertToken ?: status
    }

    private fun bind(target: JComponent, key: String, action: () -> Unit) {
        target.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key)
        target.actionMap.put(key, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = action()
        })
    }
}
