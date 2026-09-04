package com.cursoragent.ui.composer.mention

import java.io.File

/**
 * Pure git helpers for `@branch` mention context. Kept separate from [MentionResolver]
 * so branch-resolution logic can be unit-tested without a Project fixture.
 */
object BranchDiffBuilder {
    fun buildBlock(
        workspace: File,
        runGit: (File, Array<String>) -> String?,
    ): String? {
        val currentBranch = runGit(workspace, arrayOf("rev-parse", "--abbrev-ref", "HEAD"))?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return "@branch: (could not determine current branch)"

        val baseBranch = resolveBaseBranch(workspace, runGit)
        val diff = runGit(workspace, arrayOf("diff", "$baseBranch...HEAD"))
            ?: return "@branch ($currentBranch vs $baseBranch): (git diff failed)"

        if (diff.isBlank()) {
            return "@branch ($currentBranch vs $baseBranch): (no diff)"
        }

        return buildString {
            append("@branch ($currentBranch vs $baseBranch):")
            append("\n```diff\n$diff\n```")
        }
    }

    internal fun resolveBaseBranch(
        workspace: File,
        runGit: (File, Array<String>) -> String?,
    ): String {
        val originHead = runGit(workspace, arrayOf("symbolic-ref", "refs/remotes/origin/HEAD"))
            ?.substringAfterLast('/')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (originHead != null) return originHead

        val mainExists = runGit(workspace, arrayOf("rev-parse", "--verify", "main")) != null
        if (mainExists) return "main"

        val masterExists = runGit(workspace, arrayOf("rev-parse", "--verify", "master")) != null
        if (masterExists) return "master"

        return "main"
    }
}
