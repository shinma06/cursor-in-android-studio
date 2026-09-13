package com.cursoragent.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/** A fixed snapshot of reported edits, not a claim about every current filesystem change. */
internal class ConversationChangesDialog(
    project: Project,
    private val snapshot: ChangesSnapshot,
    private val onDiff: (FileChangeGroup) -> Unit,
    private val onRevert: (FileChangeGroup) -> Unit,
    private val onConversation: () -> Unit,
) : DialogWrapper(project, false) {
    private val scope = JComboBox((listOf("会話全体") + snapshot.turns.mapIndexed { index, _ -> "ターン ${index + 1}" }).toTypedArray())
    private val model = DefaultListModel<FileChangeGroup>()
    private val list = JBList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SimpleListCellRenderer.create("") { file: FileChangeGroup ->
            " ${file.last.path}（${file.edits.size} 件・${safeToolStatus(file.last.status)}）"
        }
        emptyText.text = "この範囲に受信したファイル差分はありません"
    }
    private val detail = JLabel(" ")
    private val diff = object : AbstractAction("Diff") {
        override fun actionPerformed(e: ActionEvent) { list.selectedValue?.takeIf { it.canShowDiff }?.let(onDiff) }
    }
    private val revert = object : AbstractAction("Revert") {
        override fun actionPerformed(e: ActionEvent) { list.selectedValue?.takeIf { it.revertRejection == null }?.let(onRevert) }
    }

    init {
        title = "この会話のファイル変更"
        setOKButtonText("会話へ戻る")
        setCancelButtonText("閉じる")
        scope.addActionListener { populate() }
        list.addListSelectionListener {
            val value = list.selectedValue
            diff.isEnabled = value?.canShowDiff == true
            revert.isEnabled = value != null && value.revertRejection == null
            detail.text = value?.revertRejection ?: "Revert時に現在の内容・未保存変更・作業場所・実行状態を確認します。"
        }
        init()
        populate()
    }

    private fun populate() {
        val turnId = snapshot.turns.getOrNull(scope.selectedIndex - 1)
        model.clear()
        snapshot.files(turnId).forEach(model::addElement)
        if (!model.isEmpty) list.selectedIndex = 0 else {
            diff.isEnabled = false
            revert.isEnabled = false
            detail.text = "保存履歴から復元用の差分は再取得しません。"
        }
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
        preferredSize = JBUI.size(760, 360)
        add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JLabel("開いた時点までに受信した編集の記録です。編集はすでに行われる場合があり、事前承認ではありません。"), BorderLayout.NORTH)
            add(scope, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(detail, BorderLayout.SOUTH)
    }

    override fun createActions(): Array<Action> = arrayOf(diff, revert, okAction, cancelAction)
    override fun doOKAction() { close(OK_EXIT_CODE); onConversation() }
}
