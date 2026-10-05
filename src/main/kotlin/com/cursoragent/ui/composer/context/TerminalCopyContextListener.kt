package com.cursoragent.ui.composer.context

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.terminal.frontend.view.TerminalView
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.lang.ref.WeakReference
import java.util.UUID

/** Optional Terminal registration. Snapshot only the native copy action's explicit source. */
class TerminalCopyContextListener : AnActionListener {
    private data class Pending(val event: WeakReference<AnActionEvent>, val previous: WeakReference<Transferable>, val data: ClipboardContextData)
    private var pending: Pending? = null

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        pending = null
        if (ActionManager.getInstance().getId(action) != "Terminal.CopySelectedText") return
        try {
            val project = event.project?.takeUnless { it.isDisposed } ?: return
            val view = event.getData(TerminalView.DATA_KEY) ?: return
            val selection = view.textSelectionModel.selection ?: return
            val model = view.outputModels.active.value
            val start = selection.startOffset
            val end = selection.endOffset
            if (start < model.startOffset || end > model.endOffset || end <= start) return
            val text = model.getText(start, end).toString()
            if (!normalizedClipboardText(text).contains('\n')) return
            val terminal = TerminalContext(UUID.randomUUID().toString(), view.title.buildTitle(),
                model.getLineByOffset(start).toAbsolute() + 1, model.getLineByOffset(end.minus(1)).toAbsolute() + 1, text)
            pending = Pending(WeakReference(event), WeakReference(CopyPasteManager.getInstance().contents),
                ClipboardContextData(project.locationHash, text, terminal = terminal))
        } catch (_: Exception) { /* Native copy remains available if its source cannot be captured. */ }
        catch (_: LinkageError) { /* Terminal remains an optional dependency. */ }
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        val copy = pending
        pending = null
        if (copy == null || copy.event.get() !== event || !result.isPerformed || event.project?.isDisposed != false) return
        try {
            val clipboard = CopyPasteManager.getInstance()
            val value = clipboard.contents ?: return
            if (value === copy.previous.get() || !value.isDataFlavorSupported(DataFlavor.stringFlavor)) return
            val text = value.getTransferData(DataFlavor.stringFlavor) as? String ?: return
            if (normalizedClipboardText(text) != normalizedClipboardText(copy.data.text)) return
            clipboard.setContents(ContextTransferable(value, copy.data))
        } catch (_: Exception) { /* Do not replace a failed/changed copy or expose clipboard contents. */ }
    }
}
