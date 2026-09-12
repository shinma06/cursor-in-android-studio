package com.cursoragent.ui.composer.mention

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore

/** The cap applies to matches, not to the first files traversed in the project. */
internal class MentionCandidateSearch(private val query: String) {
    val matches = mutableListOf<Mention>()
    fun visit(candidate: Mention): Boolean {
        if (candidate.displayLabel.contains(query, ignoreCase = true) || candidate.insertToken.contains(query, ignoreCase = true)) matches.add(candidate)
        return matches.size < 500
    }
}

object MentionCandidateSource {
    private val fixedCandidates = listOf(
        Mention(MentionKind.GIT_DIFF, "Git diff (unstaged + staged)", "git-diff"),
        Mention(MentionKind.BRANCH, "Branch diff (vs main)", "branch"),
        Mention(MentionKind.TERMINAL, "Terminal (recent output)", "terminal"),
        Mention(MentionKind.DOCS, "Docs (tool hint only)", "docs"),
        Mention(MentionKind.WEB, "Web (tool hint only)", "web"),
    )

    fun buildCandidates(project: Project, query: String = ""): List<Mention> {
        val search = MentionCandidateSearch(query.trim())
        val fixed = fixedCandidates.filter { it.displayLabel.contains(query.trim(), true) || it.insertToken.contains(query.trim(), true) }
        val projectDir = project.guessProjectDir() ?: return fixed
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (project.isDisposed || Thread.currentThread().isInterrupted) return@iterateContent false
            if (file == projectDir || (!file.isDirectory && file.fileType.isBinary)) return@iterateContent true
            val relativePath = VfsUtilCore.getRelativePath(file, projectDir, '/') ?: return@iterateContent true
            val kind = if (file.isDirectory) MentionKind.FOLDER else MentionKind.FILE
            val token = if (file.isDirectory) "$relativePath/" else relativePath
            search.visit(Mention(kind, relativePath, token))
        }
        return search.matches + fixed
    }
}
