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

    fun revertFileContent(project: Project, path: String, beforeContent: String): Boolean {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return false
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
