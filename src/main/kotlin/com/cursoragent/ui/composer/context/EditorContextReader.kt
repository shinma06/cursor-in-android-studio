package com.cursoragent.ui.composer.context

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager

/** Editor/document reads happen on EDT; a selection always uses its own document's file. */
object EditorContextReader {
    fun current(project: Project): EditorContext? =
        FileEditorManager.getInstance(project).selectedTextEditor?.let { read(project, it) }

    fun read(project: Project, editor: Editor): EditorContext? {
        if (project.isDisposed || editor.isDisposed) return null
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        val path = project.guessProjectDir()?.let { VfsUtilCore.getRelativePath(file, it, '/') } ?: file.path
        val model = editor.selectionModel
        val start = model.selectionStart
        val end = model.selectionEnd
        val text = model.selectedText
        val selection = if (text.isNullOrEmpty() || start >= end) null else SelectionContext(
            file.url, path, start, end, document.getLineNumber(start) + 1,
            document.getLineNumber(end - 1) + 1, text, document.modificationStamp,
        )
        return EditorContext(file.url, path, selection)
    }

    fun isCurrent(selection: SelectionContext): Boolean {
        val file = VirtualFileManager.getInstance().findFileByUrl(selection.fileUrl)?.takeIf { it.isValid } ?: return false
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
        return selection.documentStamp == document.modificationStamp && selection.endOffset <= document.textLength &&
            document.charsSequence.subSequence(selection.startOffset, selection.endOffset).toString() == selection.text
    }
}
