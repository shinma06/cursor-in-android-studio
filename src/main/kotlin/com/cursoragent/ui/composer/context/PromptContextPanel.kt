package com.cursoragent.ui.composer.context

import com.cursoragent.ui.composer.mention.Mention
import com.cursoragent.ui.composer.mention.contextDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.Timer

/** Visible per-draft attachments. Hidden/closed tabs have no polling timer. */
class PromptContextPanel(private val project: Project) : JPanel(BorderLayout()) {
    val draft = PromptContextDraft()
    var onAddMention: () -> Unit = {}
    private var automatic: EditorContext? = null
    private val automaticLabel = JButton("自動: ファイルなし").apply {
        isBorderPainted = false
        isContentAreaFilled = false
        addActionListener { automatic?.let { preview(it.selection?.block() ?: "Active file: ${it.path}") } }
    }
    private val rows = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val refreshTimer = Timer(350) { refreshAutomatic() }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(2, 6)
        val automaticToggle = JCheckBox("自動 context", true).apply {
            isOpaque = false
            toolTipText = "現在のファイルと選択範囲。送信開始時の内容を使います。"
            addActionListener { draft.automaticEnabled = isSelected; refreshAutomatic() }
        }
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                add(automaticToggle, BorderLayout.WEST)
                add(automaticLabel, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(JButton("追加…").apply { addActionListener { onAddMention() } })
                add(JButton("選択を追加").apply {
                    toolTipText = "現在のエディター選択をこの下書きへ固定して追加します。"
                    addActionListener { addCurrentSelection() }
                })
            }, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(rows, BorderLayout.CENTER)
        addHierarchyListener {
            if (isShowing && !project.isDisposed) { refreshAutomatic(); refreshTimer.start() } else refreshTimer.stop()
        }
    }

    override fun removeNotify() { refreshTimer.stop(); super.removeNotify() }

    fun addSelection(selection: SelectionContext) { draft.add(selection); render() }
    fun addMention(mention: Mention) { draft.add(mention); render() }
    fun clearExplicit() { draft.clearExplicit(); render() }

    /** Validate once when sending/enqueuing. The returned value then belongs to that request. */
    fun snapshot(): PromptContextSnapshot {
        refreshAutomatic()
        return draft.snapshot(EditorContextReader::isCurrent)
    }

    private fun addCurrentSelection(replace: String? = null) {
        val selection = EditorContextReader.current(project)?.selection
        if (selection == null) {
            Messages.showInfoMessage(project, "エディターで追加するコードを選択してください。", "Add to Chat")
            return
        }
        if (replace == null) draft.add(selection) else draft.replaceSelection(replace, selection)
        render()
    }

    private fun refreshAutomatic() {
        if (project.isDisposed) { refreshTimer.stop(); return }
        automatic = if (draft.automaticEnabled) EditorContextReader.current(project) else null
        automaticLabel.text = when {
            !draft.automaticEnabled -> "自動: 無効"
            automatic == null -> "自動: ファイルなし"
            else -> "自動: ${automatic!!.selection?.label ?: automatic!!.path}"
        }
        automaticLabel.toolTipText = automaticLabel.text
    }

    private fun render() {
        rows.removeAll()
        val state = draft.snapshot()
        for (selection in state.selections) {
            rows.add(row("明示: ${selection.label}", selection.block(), {
                draft.removeSelection(selection.key); render()
            }, { addCurrentSelection(selection.key) }))
        }
        for (mention in state.mentions) {
            rows.add(row("明示: ${mention.displayLabel}", mention.contextDescription(), {
                draft.removeMention(mention); render()
            }))
        }
        revalidate()
        repaint()
    }

    private fun row(label: String, content: String, remove: () -> Unit, replace: (() -> Unit)? = null) =
        JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(JButton(label).apply {
                horizontalAlignment = JButton.LEFT
                toolTipText = label
                addActionListener { preview(content) }
            }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                if (replace != null) add(JButton("変更").apply {
                    toolTipText = "現在のエディター選択へ置き換えます。"
                    addActionListener { replace() }
                })
                add(JButton("×").apply {
                    getAccessibleContext().accessibleName = "$label を削除"
                    addActionListener { remove() }
                })
            }, BorderLayout.EAST)
        }

    private fun preview(content: String) = Messages.showInfoMessage(
        project, content.take(2_000) + if (content.length > 2_000) "\n（previewは先頭2,000文字）" else "", "送信するcontext",
    )
}
