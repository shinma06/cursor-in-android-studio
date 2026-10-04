package com.cursoragent.ui.composer.context

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/** Only the transferable made by this copy owns the provenance; matching text alone is insufficient. */
class ClipboardContextData internal constructor(
    val projectLocation: String,
    val text: String,
    selections: List<SelectionContext> = emptyList(),
    val terminal: TerminalContext? = null,
) : TextBlockTransferableData {
    val selections = selections.toList()

    override fun getFlavor(): DataFlavor = FLAVOR

    companion object {
        val FLAVOR = DataFlavor(
            "${DataFlavor.javaJVMLocalObjectMimeType};class=${ClipboardContextData::class.java.name}",
            "Cursor Agent copied context",
            ClipboardContextData::class.java.classLoader,
        )
    }
}

/** PSI is only used to identify the copied document; resolving/committing other files is unnecessary. */
class ClipboardContextCopyProcessor : CopyPastePostProcessor<ClipboardContextData>() {
    override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project) = false

    override fun collectTransferableData(file: PsiFile, editor: Editor, startOffsets: IntArray, endOffsets: IntArray): List<ClipboardContextData> {
        val project = editor.project ?: return emptyList()
        if (project.isDisposed || editor.isDisposed || startOffsets.isEmpty() || startOffsets.size != endOffsets.size) return emptyList()
        val document = editor.document
        val source = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
        if (!source.isValid || !source.isInLocalFileSystem || source != file.virtualFile || file.viewProvider.document !== document) return emptyList()
        val path = project.guessProjectDir()?.let { VfsUtilCore.getRelativePath(source, it, '/') } ?: source.path
        val selections = startOffsets.indices.map { index ->
            val start = startOffsets[index]
            val end = endOffsets[index]
            if (start < 0 || end <= start || end > document.textLength) return emptyList()
            SelectionContext(source.url, path, start, end, document.getLineNumber(start) + 1,
                document.getLineNumber(end - 1) + 1, document.charsSequence.subSequence(start, end).toString(), document.modificationStamp)
        }
        return listOf(ClipboardContextData(project.locationHash, selections.joinToString("\n") { it.text }, selections))
    }
}

internal fun normalizedClipboardText(text: String) = text.replace("\r\n", "\n").trimEnd('\n')

/** Unsupported/external data falls through to native paste, including the original text. */
internal fun clipboardContext(value: Transferable, project: Project): ClipboardContextData? = try {
    if (project.isDisposed || !value.isDataFlavorSupported(ClipboardContextData.FLAVOR) || !value.isDataFlavorSupported(DataFlavor.stringFlavor)) null
    else {
        val data = value.getTransferData(ClipboardContextData.FLAVOR) as? ClipboardContextData
        val text = value.getTransferData(DataFlavor.stringFlavor) as? String
        data?.takeIf {
            text != null && it.projectLocation == project.locationHash &&
                normalizedClipboardText(text).contains('\n') && normalizedClipboardText(text) == normalizedClipboardText(it.text) &&
                (it.selections.isNotEmpty() != (it.terminal != null)) &&
                normalizedClipboardText(it.terminal?.text ?: it.selections.joinToString("\n") { selection -> selection.text }) == normalizedClipboardText(it.text) &&
                it.selections.all(EditorContextReader::isCurrent)
        }
    }
} catch (_: Exception) { null }

/** Preserve every native flavor (plain text, HTML, etc.) when decorating a Terminal copy. */
internal class ContextTransferable(private val original: Transferable, private val context: ClipboardContextData) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = (original.transferDataFlavors.toList() + ClipboardContextData.FLAVOR).distinct().toTypedArray()
    override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == ClipboardContextData.FLAVOR || original.isDataFlavorSupported(flavor)
    override fun getTransferData(flavor: DataFlavor): Any = if (flavor == ClipboardContextData.FLAVOR) context else original.getTransferData(flavor)
}
