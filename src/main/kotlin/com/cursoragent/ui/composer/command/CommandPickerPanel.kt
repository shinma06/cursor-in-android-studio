package com.cursoragent.ui.composer.command

import com.cursoragent.service.AgentCommand
import com.cursoragent.service.CommandCatalog
import com.cursoragent.ui.composer.PromptImeGuard
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal fun CommandCatalog.description(): String = when (this) {
    CommandCatalog.Unavailable -> "候補はACP接続で取得できます。互換CLIではコマンドを直接入力できます。"
    CommandCatalog.Loading -> "候補の取得に必要な接続を準備中…"
    CommandCatalog.Awaiting -> "接続済み。この会話の候補はまだ通知されていません。"
    CommandCatalog.Invalid -> "候補の形式を確認できません。次の通知を待つか再接続してください。"
    CommandCatalog.Failed -> "候補を取得できませんでした。初回送信前は再接続できます。"
    is CommandCatalog.Ready -> if (commands.isEmpty()) "この会話に広告された候補はありません。" else "一致する候補はありません。"
}

/** Local filtering of the complete session advertisement. Selection never submits a prompt. */
internal class CommandPickerPanel(
    private val choose: (AgentCommand) -> Unit,
    cancel: () -> Unit,
    retry: () -> Unit,
) : JPanel(BorderLayout(4, 4)) {
    val search = JTextField()
    val model = DefaultListModel<AgentCommand>()
    val list = JBList(model)
    private val preview = JTextArea(3, 40).apply { isEditable = false; lineWrap = true; wrapStyleWord = true; isOpaque = false }
    private val retryButton = JButton("再接続").apply { addActionListener { retry() } }
    private var catalog: CommandCatalog = CommandCatalog.Loading
    private val ime = PromptImeGuard()

    init {
        preferredSize = JBUI.size(460, 320)
        border = JBUI.Borders.empty(6)
        search.accessibleContext.accessibleName = "Skills・コマンド候補を検索"
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, selected, focus)
                putClientProperty("html.disable", true)
                text = (value as? AgentCommand)?.let { "/${it.name} — ${it.description}" }.orEmpty()
                return this
            }
        }
        add(search, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply { add(preview); add(retryButton, BorderLayout.SOUTH) }, BorderLayout.SOUTH)
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filter()
            override fun removeUpdate(e: DocumentEvent) = filter()
            override fun changedUpdate(e: DocumentEvent) = filter()
        })
        search.addInputMethodListener(ime)
        list.addListSelectionListener { updatePreview() }
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val index = list.locationToIndex(e.point)
                if (!ime.isComposing && e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e) && index >= 0 && list.getCellBounds(index, index).contains(e.point)) choose(model.get(index))
            }
        })
        for (target in listOf(search, list)) {
            bind(target, "ENTER") { if (!ime.isComposing) list.selectedValue?.let(choose) }
            bind(target, "ESCAPE") { if (!ime.isComposing) cancel() }
        }
        for ((key, offset) in listOf("DOWN" to 1, "UP" to -1)) bind(search, key) {
            if (!ime.isComposing && !model.isEmpty) {
                list.selectedIndex = (list.selectedIndex + offset).coerceIn(0, model.size - 1)
                list.ensureIndexIsVisible(list.selectedIndex)
            }
        }
    }

    fun update(value: CommandCatalog, canRetry: Boolean) {
        catalog = value
        retryButton.isEnabled = canRetry && value != CommandCatalog.Loading && value != CommandCatalog.Unavailable
        filter()
    }

    private fun filter() {
        val name = list.selectedValue?.name
        model.clear()
        val query = search.text.trim()
        (catalog as? CommandCatalog.Ready)?.commands?.filter {
            it.name.contains(query, true) || it.description.contains(query, true)
        }?.forEach(model::addElement)
        list.emptyText.text = catalog.description()
        if (!model.isEmpty) list.selectedIndex = (0 until model.size).firstOrNull { model.get(it).name == name } ?: 0
        updatePreview()
    }

    private fun updatePreview() {
        preview.text = list.selectedValue?.let {
            "出所: この会話のAgent（server広告）\n${it.description}" + (it.hint?.let { hint -> "\n引数: $hint" } ?: "")
        } ?: catalog.description()
    }

    private fun bind(target: JComponent, key: String, action: () -> Unit) {
        target.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key)
        target.actionMap.put(key, object : AbstractAction() { override fun actionPerformed(e: ActionEvent?) = action() })
    }
}
