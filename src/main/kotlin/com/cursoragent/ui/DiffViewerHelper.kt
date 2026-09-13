package com.cursoragent.ui

import com.cursoragent.service.FileRevertOperation
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
    /** Both the individual print card and the aggregate list use this same restore gate. */
    fun revertObservedEdit(
        project: Project,
        path: String,
        before: String?,
        after: String?,
        target: RestoreTarget,
        isCurrent: () -> Boolean = { true },
        onRestored: () -> Unit,
    ) {
        if (project.isDisposed) return
        val reservation = project.getService(com.cursoragent.service.AgentProcessService::class.java).tryRestore()
        val result = if (reservation == null) RestoreResult(RestorePolicy.BUSY) else reservation.use {
            if (before == null || after == null) RestoreResult(RestorePolicy.RESTORE_FAILED)
            else revertFileContentResult(project, path, before, after, target, isCurrent)
        }
        if (result.restored) onRestored()
        else com.intellij.openapi.ui.Messages.showErrorDialog(project, result.rejectionReason!!, com.cursoragent.PluginBrand.NAME)
    }

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
    fun revertFileContentResult(
        project: Project,
        path: String,
        beforeContent: String,
        expectedCurrentContent: String,
        target: RestoreTarget,
        isCurrent: () -> Boolean = { true },
    ): RestoreResult {
        if (!isCurrent()) return RestoreResult("会話の状態が変わったためRevertできません。変更一覧を開き直してください。")
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
                if (!isCurrent()) {
                    result = RestoreResult("会話の状態が変わったためRevertできません。変更一覧を開き直してください。")
                    return@run
                }
                result = FileRevertOperation.restore(
                    target, currentTarget(), path, beforeContent, expectedCurrentContent,
                    object : FileRevertOperation.FileAccess {
                        override val path = resolved
                        override fun hasUnsavedChanges() = FileDocumentManager.getInstance().isFileModified(file)
                        override fun read() = String(file.contentsToByteArray(), file.charset)
                        override fun write(content: String) = VfsUtil.saveText(file, content)
                    },
                )
            }
            result
        } catch (_: Exception) {
            RestoreResult(RestorePolicy.RESTORE_FAILED)
        }
    }
}
