package com.cursoragent.service

import java.nio.file.Path

/** The caller supplies file access inside its write lock (the IDE uses a write command). */
internal object FileRevertOperation {
    fun restore(
        created: RestoreTarget,
        current: RestoreTarget,
        path: String,
        before: String,
        expectedAfter: String,
        file: FileAccess,
    ): RestoreResult {
        RestorePolicy.rejectionReason(created, current)?.let { return RestoreResult(it) }
        val resolved = RestorePolicy.resolveFile(created, current, path)
            ?: return RestoreResult(RestorePolicy.OUTSIDE_ROOT)
        if (resolved != file.path) return RestoreResult(RestorePolicy.OUTSIDE_ROOT)
        return try {
            if (file.hasUnsavedChanges() || file.read() != expectedAfter) {
                RestoreResult(RestorePolicy.STALE_EDIT)
            } else {
                file.write(before)
                RestoreResult()
            }
        } catch (_: Exception) {
            RestoreResult(RestorePolicy.RESTORE_FAILED)
        }
    }

    interface FileAccess {
        val path: Path
        fun hasUnsavedChanges(): Boolean
        fun read(): String
        fun write(content: String)
    }
}
