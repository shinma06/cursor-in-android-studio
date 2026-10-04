package com.cursoragent.ui.composer.context

import com.cursoragent.ui.composer.mention.Mention
import com.cursoragent.ui.composer.mention.contextDescription
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.datatransfer.StringSelection
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.Scrollable

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
    private val rows = object : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = JBUI.scale(16)
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) = (r.height - JBUI.scale(24)).coerceAtLeast(1)
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }.apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val attachments = object : JBScrollPane(rows) {
        override fun getPreferredSize(): Dimension = super.getPreferredSize().apply {
            height = height.coerceAtMost(JBUI.scale(120))
        }
    }.apply {
        isOpaque = false
        viewport.isOpaque = false
        border = JBUI.Borders.empty()
        isVisible = false
    }
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
        add(attachments, BorderLayout.CENTER)
        addHierarchyListener {
            if (isShowing && !project.isDisposed) { refreshAutomatic(); refreshTimer.start() } else refreshTimer.stop()
        }
    }

    override fun removeNotify() { refreshTimer.stop(); super.removeNotify() }

    fun addSelection(selection: SelectionContext) { draft.add(selection); render() }
    fun addMention(mention: Mention) { draft.add(mention); render() }
    fun addTerminal(terminal: TerminalContext) { draft.add(terminal); render() }
    internal fun addClipboard(context: ClipboardContextData) {
        context.selections.forEach(draft::add)
        context.terminal?.let(draft::add)
        context.mention?.let(draft::add)
        render()
    }
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
            }, { addCurrentSelection(selection.key) },
                accessibleLabel = "${selection.label}（選択位置 ${selection.startOffset}–${selection.endOffset}）",
                copy = { ClipboardContextData(project.locationHash, selection.label, listOf(selection), copiedAttachment = true) }))
        }
        for (mention in state.mentions) {
            rows.add(row("明示: ${mention.displayLabel}", mention.contextDescription(), {
                draft.removeMention(mention); render()
            }, copy = { ClipboardContextData(project.locationHash, mention.displayLabel, mention = mention, copiedAttachment = true) }))
        }
        for (terminal in state.terminals) {
            rows.add(row("明示: ${terminal.label}", terminal.block(), {
                draft.removeTerminal(terminal.id); render()
            }, copy = { ClipboardContextData(project.locationHash, terminal.label, terminal = terminal, copiedAttachment = true) }))
        }
        attachments.isVisible = rows.componentCount > 0
        revalidate()
        repaint()
    }

    private fun row(
        label: String, content: String, remove: () -> Unit, replace: (() -> Unit)? = null,
        accessibleLabel: String = label, copy: () -> ClipboardContextData,
    ): JPanel {
        fun copyAttachment() {
            if (project.isDisposed) return
            val context = copy()
            CopyPasteManager.getInstance().setContents(ContextTransferable(StringSelection(context.text), context))
        }
        val provider = object : CopyProvider {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isCopyEnabled(dataContext: DataContext) = !project.isDisposed
            override fun isCopyVisible(dataContext: DataContext) = true
            override fun performCopy(dataContext: DataContext) = copyAttachment()
        }
        return object : JPanel(BorderLayout(4, 0)), UiDataProvider {
            override fun uiDataSnapshot(sink: DataSink) { sink[PlatformDataKeys.COPY_PROVIDER] = provider }
        }.apply {
            isOpaque = false
            add(attachmentButton(label).apply {
                minimumSize = Dimension(0, preferredSize.height)
                getAccessibleContext().accessibleName = accessibleLabel
                horizontalAlignment = JButton.LEFT
                toolTipText = label
                addActionListener { preview(content) }
            }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(attachmentButton("コピー").apply {
                    getAccessibleContext().accessibleName = "$accessibleLabel をコピー"
                    toolTipText = "別の入力欄へ添付として貼り付けます。本文貼り付けでは表示名だけを入れます。"
                    addActionListener { copyAttachment() }
                })
                if (replace != null) add(attachmentButton("変更").apply {
                    getAccessibleContext().accessibleName = "$accessibleLabel を変更"
                    toolTipText = "現在のエディター選択へ置き換えます。"
                    addActionListener { replace() }
                })
                add(attachmentButton("×").apply {
                    getAccessibleContext().accessibleName = "$accessibleLabel を削除"
                    addActionListener { remove() }
                })
            }, BorderLayout.EAST)
        }
    }

    private fun attachmentButton(label: String) = JButton(label).apply {
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(event: FocusEvent) = scrollRectToVisible(Rectangle(size))
        })
    }

    private fun preview(content: String) = Messages.showInfoMessage(
        project, content.take(2_000) + if (content.length > 2_000) "\n（previewは先頭2,000文字）" else "", "送信するcontext",
    )
}
