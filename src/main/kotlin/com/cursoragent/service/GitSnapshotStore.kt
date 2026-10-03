package com.cursoragent.service

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import java.io.File

/**
 * Pure git-plumbing wrapper for checkpoint snapshot/restore. Takes a plain [File]
 * rather than a Project so it's unit-testable against a real temp git repo without
 * spinning up the platform.
 */
class GitSnapshotStore(private val workspaceDir: File) {
    fun isGitRepo(): Boolean = runGit("rev-parse", "--show-toplevel").let {
        it.exitCode == 0 && RestoreTarget.realDirectory(it.stdout.trim()) ==
            RestoreTarget.realDirectory(workspaceDir.absolutePath)
    }

    fun listUntrackedFiles(): List<String> {
        val result = runGit("ls-files", "--others", "--exclude-standard", "-z")
        if (result.exitCode != 0) return emptyList()
        return result.stdout.split('\u0000').filter { it.isNotEmpty() }
    }

    /**
     * Non-destructive: `stash create` only builds commit objects, it never touches
     * the index, working tree, or stash ref list (unlike `stash push`/`stash save`),
     * so this stays invisible to `git stash list`/`git log`. Falls back to the
     * current HEAD when there's no working-tree diff to snapshot (`stash create`
     * prints nothing in that case). Returns null only when neither resolves, e.g. a
     * repo with no commits yet.
     */
    fun createSnapshot(): String? {
        val stashSha = runGit("stash", "create").takeIf { it.exitCode == 0 }?.stdout?.trim()
        if (!stashSha.isNullOrEmpty()) return stashSha
        return runGit("rev-parse", "HEAD").takeIf { it.exitCode == 0 }?.stdout?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Overwrites every tracked path with [sha]'s tree, then deletes files that are
     * still untracked now but weren't in [untrackedFilesAtSnapshot] — i.e. new files
     * the agent created after the snapshot. `git checkout <sha> -- .` only ever
     * writes paths present in <sha>'s tree, it never deletes anything, which is why
     * new *untracked* files need this separate cleanup pass.
     *
     * Known gap: a new file the agent both wrote and `git add`ed during the turn
     * would no longer be untracked, so it's invisible to this cleanup and survives
     * a rollback. Staging by the agent or user can trigger this regardless of
     * transport; the snapshot contract does not promise complete new-file undo.
     */
    fun restore(
        sha: String,
        untrackedFilesAtSnapshot: List<String>,
        created: RestoreTarget,
        current: RestoreTarget,
    ): RestoreResult {
        RestorePolicy.rejectionReason(created, current)?.let { return RestoreResult(it) }
        if (RestoreTarget.realDirectory(workspaceDir.absolutePath)?.toString() != created.rootPath || !isGitRepo()) {
            return RestoreResult(RestorePolicy.CHANGED_TARGET)
        }
        if (!sha.matches(Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}"))) {
            return RestoreResult(RestorePolicy.RESTORE_FAILED)
        }
        val checkoutResult = runGit("checkout", sha, "--", ".")
        if (checkoutResult.exitCode != 0) return RestoreResult(RestorePolicy.RESTORE_FAILED)

        val newUntrackedFiles = listUntrackedFiles() - untrackedFilesAtSnapshot.toSet()
        for (relativePath in newUntrackedFiles) {
            // Delete the directory entry itself (including an untracked symlink), never its target.
            val root = RestoreTarget.realDirectory(workspaceDir.absolutePath)
                ?: return RestoreResult(RestorePolicy.MISSING_ROOT)
            if (root.toString() != created.rootPath) return RestoreResult(RestorePolicy.CHANGED_TARGET)
            val path = root.resolve(relativePath).normalize()
            if (!path.startsWith(root) || runCatching { path.parent.toRealPath().startsWith(root) }.getOrDefault(false).not()) {
                return RestoreResult(RestorePolicy.OUTSIDE_ROOT)
            }
            if (!runCatching { java.nio.file.Files.deleteIfExists(path) }.isSuccess) {
                return RestoreResult(RestorePolicy.RESTORE_FAILED)
            }
        }
        return RestoreResult()
    }

    private fun runGit(vararg args: String): GitResult {
        return try {
            val commandLine = GeneralCommandLine("git", *args).withWorkDirectory(workspaceDir)
            val output = ExecUtil.execAndGetOutput(commandLine)
            GitResult(output.exitCode, output.stdout, output.stderr)
        } catch (e: Exception) {
            GitResult(-1, "", e.message.orEmpty())
        }
    }

    private data class GitResult(val exitCode: Int, val stdout: String, val stderr: String)
}
