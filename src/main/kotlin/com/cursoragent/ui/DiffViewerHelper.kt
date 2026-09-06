package com.cursoragent.ui

import com.cursoragent.service.RestorePolicy
import com.cursoragent.service.RestoreResult
import com.cursoragent.service.RestoreTarget
import com.cursoragent.settings.AgentSettingsState
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
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
    fun revertFileContent(project: Project, path: String, beforeContent: String, expectedCurrentContent: String): Boolean =
        revertFileContentResult(project, path, beforeContent, expectedCurrentContent, RestoreTarget.UNKNOWN).restored

    fun revertFileContentResult(
        project: Project,
        path: String,
        beforeContent: String,
        expectedCurrentContent: String,
        target: RestoreTarget,
    ): RestoreResult {
        fun currentTarget() = RestoreTarget.capture(project.basePath, AgentSettingsState.getInstance().worktreeMode)
        RestorePolicy.rejectionReason(target, currentTarget())?.let { return RestoreResult(it) }
        val resolved = RestorePolicy.resolveFile(target, currentTarget(), path)
            ?: return RestoreResult(RestorePolicy.OUTSIDE_ROOT)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(resolved.toString())
            ?: return RestoreResult(RestorePolicy.RESTORE_FAILED)
        return try {
            var result = RestoreResult(RestorePolicy.RESTORE_FAILED)
            WriteCommandAction.writeCommandAction(project).run<Throwable> {
                // Recheck at the write boundary, including unsaved editor changes.
                val rejection = RestorePolicy.rejectionReason(target, currentTarget())
                if (rejection != null) {
                    result = RestoreResult(rejection)
                } else if (RestorePolicy.resolveFile(target, currentTarget(), path) != resolved) {
                    result = RestoreResult(RestorePolicy.OUTSIDE_ROOT)
                } else {
                    val documents = FileDocumentManager.getInstance()
                    val currentContent = String(file.contentsToByteArray(), file.charset)
                    if (documents.isFileModified(file) || currentContent != expectedCurrentContent) {
                        result = RestoreResult(RestorePolicy.STALE_EDIT)
                    } else {
                        VfsUtil.saveText(file, beforeContent)
                        result = RestoreResult()
                    }
                }
            }
            result
        } catch (_: Exception) {
            RestoreResult(RestorePolicy.RESTORE_FAILED)
        }
    }
}
