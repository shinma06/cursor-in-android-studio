package com.cursoragent.ui.composer.command

import com.cursoragent.service.CommandCatalog
import com.cursoragent.service.containsCommand
import com.cursoragent.ui.composer.GrowingPromptField
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** A command is a removable, per-composer attachment; its raw arguments remain in the editor. */
class CommandInputPanel(private val project: Project, private val field: GrowingPromptField) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {
    var onRetry: () -> Unit = {}
    var selectedName: String? = null
        private set
    private var catalog: CommandCatalog = CommandCatalog.Unavailable
    private var canRetry = false
    private var popup: JBPopup? = null
    private var picker: CommandPickerPanel? = null
    private val open = JButton("/").apply {
        toolTipText = "Skills・コマンド候補"
        addActionListener { showPopup() }
    }
    private val selected = JLabel().apply { putClientProperty("html.disable", true) }
    private val remove = JButton("×").apply {
        toolTipText = "このメッセージのコマンド選択を解除（本文は保持）"
        accessibleContext.accessibleName = "コマンド選択を解除"
        addActionListener { clearSelection(); field.requestFocusInWindow() }
    }
    val popupOpen: Boolean get() = popup?.isDisposed == false
    fun canInvoke(name: String): Boolean = catalog.containsCommand(name)

    init {
        isOpaque = false
        add(open); add(selected); add(remove)
        refresh()
        field.addHierarchyListener { if (!field.isShowing) popup?.cancel() }
        field.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                popup?.cancel()
                if (field.isComposing || event.newLength != 1 || event.newFragment.toString() != "/") return
                val offset = event.offset
                if (offset > 0 && !field.document.charsSequence[offset - 1].isWhitespace()) return
                val stamp = field.document.modificationStamp
                SwingUtilities.invokeLater {
                    if (!project.isDisposed && field.isShowing && !field.isComposing && field.document.modificationStamp == stamp) showPopup(offset)
                }
            }
        })
    }

    fun update(value: CommandCatalog, retryAllowed: Boolean) {
        catalog = value
        canRetry = retryAllowed
        picker?.update(value, canRetry)
        refresh()
    }

    fun restoreSelection(name: String) { selectedName = name; refresh() }
    fun clearSelection() { selectedName = null; refresh() }
    fun close() { popup?.cancel(); picker = null }

    private fun refresh() {
        selected.text = selectedName?.let { "/$it" + if (canInvoke(it)) "（このメッセージ）" else "（未確認・再選択してください）" }.orEmpty()
        selected.toolTipText = selectedName?.let { "出所: この会話のAgent（server広告）。選択は1メッセージだけです。" }
        remove.isVisible = selectedName != null
        open.toolTipText = "Skills・コマンド候補: ${catalog.description()}"
        revalidate(); repaint()
    }

    private fun showPopup(triggerOffset: Int? = null) {
        if (project.isDisposed || !field.isShowing || field.isComposing) return
        popup?.cancel()
        val document = field.document
        val stamp = document.modificationStamp
        lateinit var next: JBPopup
        val panel = CommandPickerPanel(choose = { command ->
            if (!project.isDisposed && field.isShowing && document.modificationStamp == stamp && canInvoke(command.name)) {
                if (triggerOffset != null && triggerOffset < document.textLength && document.charsSequence[triggerOffset] == '/') {
                    ApplicationManager.getApplication().runWriteAction { document.deleteString(triggerOffset, triggerOffset + 1) }
                }
                selectedName = command.name
                refresh()
                next.cancel()
                field.requestFocusInWindow()
            }
        }, cancel = { next.cancel() }, retry = { onRetry() })
        next = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.search)
            .setRequestFocus(true).setCancelOnClickOutside(true).setCancelKeyEnabled(false).createPopup()
        popup = next
        picker = panel
        next.addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
            override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                if (popup === next) { popup = null; picker = null }
            }
        })
        panel.update(catalog, canRetry)
        next.showUnderneathOf(field)
    }
}
