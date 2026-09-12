package com.cursoragent.ui.composer.mention

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import javax.swing.SwingUtilities

/** Keep explicit context outside editable prompt text. A stale popup cannot replace newer input. */
class MentionPopupController(
    private val project: Project,
    private val field: EditorTextField,
    private val onAttach: (Mention) -> Unit,
) {
    private var popup: JBPopup? = null

    fun install() {
        field.addHierarchyListener { if (!field.isShowing) popup?.cancel() }
        field.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                popup?.cancel()
                if (event.newLength != 1 || event.newFragment.toString() != "@") return
                val stamp = field.document.modificationStamp
                SwingUtilities.invokeLater {
                    if (!project.isDisposed && field.isShowing && field.document.modificationStamp == stamp) showPopup(event.offset)
                }
            }
        })
    }

    fun showPopup(triggerOffset: Int? = null) {
        if (project.isDisposed || !field.isShowing) return
        popup?.cancel()
        val document = field.document
        val stamp = document.modificationStamp
        lateinit var next: JBPopup
        lateinit var panel: MentionPickerPanel
        var pending: java.util.concurrent.Future<*>? = null
        var queryGeneration = 0L
        fun load(query: String) {
            pending?.cancel(true)
            val generation = ++queryGeneration
            pending = ApplicationManager.getApplication().executeOnPooledThread {
                val result = runCatching { ReadAction.compute<List<Mention>, RuntimeException> { MentionCandidateSource.buildCandidates(project, query) } }
                SwingUtilities.invokeLater {
                    if (!project.isDisposed && !next.isDisposed && field.isShowing && generation == queryGeneration) {
                        result.fold(panel::loaded) { panel.failed() }
                    }
                }
            }
        }
        panel = MentionPickerPanel(choose = { mention ->
            if (!project.isDisposed && field.isShowing && document.modificationStamp == stamp &&
                (triggerOffset == null || triggerOffset in 0 until document.textLength && document.charsSequence[triggerOffset] == '@')
            ) {
                if (triggerOffset != null) ApplicationManager.getApplication().runWriteAction {
                    document.deleteString(triggerOffset, triggerOffset + 1)
                }
                onAttach(mention)
                next.cancel()
                field.requestFocusInWindow()
            }
        }, cancel = { next.cancel(); field.requestFocusInWindow() }, onQuery = ::load)
        next = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.search)
            .setTitle("contextを追加（全体を検索・project候補を最大500件表示）")
            .setRequestFocus(true)
            .setResizable(true)
            .setCancelKeyEnabled(false)
            .createPopup()
        popup = next
        Disposer.register(next, Disposable { queryGeneration++; pending?.cancel(true); if (popup === next) popup = null })
        next.showUnderneathOf(field)
        load("")
    }
}
