package com.cursoragent.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

object DiffViewerHelper {
    fun showFileEditDiff(project: Project, path: String, before: String, after: String) {
        val factory = DiffContentFactory.getInstance()
        val request = SimpleDiffRequest(
            path,
            factory.create(before),
            factory.create(after),
            "Before",
            "After",
        )
        DiffManager.getInstance().showDiff(project, request)
    }

    /**
     * Restores [beforeContent], but only if the file's current content still matches
     * [expectedCurrentContent] (the content this specific edit produced). A mismatch means
     * the file was changed again since — by a later agent edit or the user — and reverting
     * would silently discard that newer content, so this refuses instead.
     */
    fun revertFileContent(project: Project, path: String, beforeContent: String, expectedCurrentContent: String): Boolean {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return false
        val currentContent = try {
            String(file.contentsToByteArray(), file.charset)
        } catch (_: Exception) {
            return false
        }
        if (currentContent != expectedCurrentContent) return false
        return try {
            WriteCommandAction.writeCommandAction(project).run<Throwable> {
                VfsUtil.saveText(file, beforeContent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
