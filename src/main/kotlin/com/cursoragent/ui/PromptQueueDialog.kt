package com.cursoragent.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/** Opening management pauses automatic sends. Only the explicit OK action resumes them. */
internal class PromptQueueDialog(
    private val project: Project,
    private val queue: PromptQueue,
    private val isCurrent: () -> Boolean,
    private val onChanged: () -> Unit,
    private val onResume: () -> Unit,
) : DialogWrapper(project, false) {
    private val model = DefaultListModel<QueuedPrompt>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SimpleListCellRenderer.create("") { value: QueuedPrompt ->
            "${value.mode.name.lowercase().replaceFirstChar { it.titlecase() }} / ${value.model.ifBlank { "既定モデル" }} — ${com.cursoragent.service.commandPrompt(value.command, value.text).replace('\n', ' ').take(100)}"
        }
        emptyText.text = "予約した入力はありません"
    }
    private fun button(text: String, action: (QueuedPrompt) -> Unit) = JButton(text).apply {
        addActionListener {
            if (isCurrent()) list.selectedValue?.let { action(it); refresh() }
        }
    }

    init {
        title = "予約した入力（一時停止中）"
        setOKButtonText("予約送信を再開")
        setCancelButtonText("一時停止のまま閉じる")
        init()
        refresh()
    }

    private fun refresh() {
        val selected = list.selectedValue?.id
        model.clear()
        queue.snapshot().forEach(model::addElement)
        val index = queue.snapshot().indexOfFirst { it.id == selected }
        if (!model.isEmpty) list.selectedIndex = index.coerceAtLeast(0)
        isOKActionEnabled = queue.size > 0
        onChanged()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
        preferredSize = JBUI.size(640, 440)
        add(JLabel("明示選択・添付は登録時に固定。自動context・参照内容・実行設定は送信開始時です。"), BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(button("編集…", ::edit))
            add(button("context…") { entry ->
                val snapshot = entry.context
                val details = buildString {
                    append("登録時のコマンド: ").append(entry.command?.let { "/$it" } ?: "なし").append("\n登録時の明示context\n")
                    snapshot?.selections?.forEach { append(it.block()).append("\n\n") }
                    snapshot?.mentions?.forEach { append(it.displayLabel).append(" — ").append(it.insertToken).append("\n") }
                    append("\n参照内容と自動contextは次turn開始時。自動context: ")
                    append(if (snapshot?.automaticEnabled != false) "有効" else "無効")
                }
                com.intellij.openapi.ui.Messages.showInfoMessage(project, details.take(4_000), "予約項目のcontext")
            })
            add(button("削除") { queue.remove(it.id) })
            add(button("上へ") { queue.move(it.id, -1) })
            add(button("下へ") { queue.move(it.id, 1) })
        }, BorderLayout.SOUTH)
    }

    private fun edit(entry: QueuedPrompt) {
        object : DialogWrapper(project, false) {
            private val text = JBTextArea(entry.text, 8, 50).apply { lineWrap = true; wrapStyleWord = true }
            init {
                title = "予約した入力を編集" + (entry.command?.let { "（/$it の引数）" } ?: "")
                setOKButtonText("保存")
                setCancelButtonText("キャンセル")
                init()
            }
            override fun createCenterPanel(): JComponent = JBScrollPane(text)
            override fun doOKAction() {
                if (!isCurrent()) return
                if (text.text.isBlank() && entry.command == null) {
                    com.intellij.openapi.ui.Messages.showInfoMessage(project, "空の入力は予約できません。削除する場合は一覧の「削除」を使ってください。", "予約した入力")
                    return
                }
                if (queue.edit(entry.id, text.text)) super.doOKAction()
            }
        }.show()
    }

    override fun doOKAction() {
        if (!isCurrent()) return
        close(OK_EXIT_CODE)
        onResume()
    }
}
