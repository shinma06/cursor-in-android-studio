package com.cursoragent.service

import com.cursoragent.settings.WorktreeMode
import java.nio.file.Files
import java.nio.file.Path

/** Immutable provenance of one CLI invocation, never reconstructed from a later UI setting. */
data class RestoreTarget(val rootPath: String?, val worktreeMode: WorktreeMode?) {
    companion object {
        val UNKNOWN = RestoreTarget(null, null)

        /** The supplied root is the CLI workspace, not a guessed isolated-worktree location. */
        fun capture(projectRoot: String?, worktreeMode: WorktreeMode): RestoreTarget =
            RestoreTarget(realDirectory(projectRoot)?.toString(), worktreeMode)

        internal fun realDirectory(path: String?): Path? = runCatching {
            path?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?.takeIf { it.isAbsolute && Files.isDirectory(it) }?.toRealPath()
        }.getOrNull()
    }
}

/** A null rejection means that the operation succeeded. Safe to show directly in Japanese UI. */
data class RestoreResult(val rejectionReason: String? = null) {
    val restored: Boolean get() = rejectionReason == null
}

object RestorePolicy {
    const val UNKNOWN_TARGET = "作成時の復元先を確認できないため復元できません。新しい会話を開始してください。"
    const val ISOLATED = "分離した作業コピーの実際の保存先を確認できないため、チェックポイント復元とRevertは使用できません。"
    const val MISSING_ROOT = "プロジェクトの保存先を確認できないため復元できません。"
    const val CHANGED_TARGET = "作成時と現在のプロジェクトまたはWorktree設定が異なるため復元できません。"
    const val OUTSIDE_ROOT = "編集されたファイルが確認済みのプロジェクト内にないためRevertできません。"
    const val STALE_EDIT = "この編集の後にファイルが変更されているためRevertできません。"
    const val BUSY = "応答の準備・実行・停止処理、または復元が続いているため操作できません。完了後にもう一度お試しください。"
    const val SNAPSHOT_UNAVAILABLE = "この応答のチェックポイントを作成できませんでした。コミットのあるGitプロジェクトで使用してください。"
    const val RESTORE_FAILED = "ファイルを復元できませんでした。"

    fun rejectionReason(created: RestoreTarget, current: RestoreTarget): String? {
        if (created.worktreeMode == null) return UNKNOWN_TARGET
        if (created.worktreeMode == WorktreeMode.ISOLATED || current.worktreeMode == WorktreeMode.ISOLATED) {
            return ISOLATED
        }
        if (created.rootPath == null) return UNKNOWN_TARGET
        if (current.rootPath == null || current.worktreeMode == null) return MISSING_ROOT
        if (created != current) return CHANGED_TARGET
        // Do not follow a captured root that has since been replaced by a symlink to another root.
        if (RestoreTarget.realDirectory(created.rootPath)?.toString() != created.rootPath) return MISSING_ROOT
        return null
    }

    /** Resolve CLI-relative paths only against the recorded root; reject traversal and symlink escapes. */
    fun resolveFile(created: RestoreTarget, current: RestoreTarget, path: String): Path? {
        if (rejectionReason(created, current) != null || path.isBlank()) return null
        return runCatching {
            val root = Path.of(created.rootPath!!)
            val candidate = root.resolve(path).normalize()
            val resolved = candidate.toRealPath()
            resolved.takeIf {
                candidate.startsWith(root) && it.startsWith(root) && Files.isRegularFile(it) &&
                    root.relativize(candidate).none { part -> part.toString() == ".git" } &&
                    root.relativize(it).none { part -> part.toString() == ".git" }
            }
        }.getOrNull()
    }
}
