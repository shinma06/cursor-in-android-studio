package com.cursoragent.ui.composer.mention

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore

/** Caps how many project files/folders are offered as `@` candidates so a huge
 *  monorepo doesn't make every `@` keystroke iterate the whole content tree. */
private const val MAX_CANDIDATES = 500

object MentionCandidateSource {
    private val fixedCandidates = listOf(
        Mention(MentionKind.GIT_DIFF, "Git diff (unstaged + staged)", "git-diff"),
        Mention(MentionKind.BRANCH, "Branch diff (vs main)", "branch"),
        Mention(MentionKind.TERMINAL, "Terminal (recent output)", "terminal"),
        Mention(MentionKind.DOCS, "Docs (via MCP, if configured)", "docs"),
        Mention(MentionKind.WEB, "Web (via MCP, if configured)", "web"),
    )

    fun buildCandidates(project: Project): List<Mention> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val projectDir = project.guessProjectDir() ?: return fixedCandidates
        val fileCandidates = mutableListOf<Mention>()

        fileIndex.iterateContent { file ->
            if (project.isDisposed || Thread.currentThread().isInterrupted) return@iterateContent false
            if (file == projectDir || (!file.isDirectory && file.fileType.isBinary)) return@iterateContent true
            val relativePath = VfsUtilCore.getRelativePath(file, projectDir, '/') ?: return@iterateContent true
            val kind = if (file.isDirectory) MentionKind.FOLDER else MentionKind.FILE
            val token = if (file.isDirectory) "$relativePath/" else relativePath
            fileCandidates += Mention(kind, relativePath, token)
            fileCandidates.size < MAX_CANDIDATES
        }

        return fileCandidates + fixedCandidates
    }
}
