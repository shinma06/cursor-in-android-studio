package com.cursoragent.ui.composer.mention

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File

/**
 * Expands `@token`s left in a sent prompt (inserted by [MentionPopupController]) into
 * the context blocks the CLI actually sees — file/folder contents, `git diff` output,
 * or a plain hint for the MCP-backed Docs/Web entries (see requirements doc F-10/F-11/
 * F-12/F-14; F-13/Terminal is a no-op pending the IntelliJ Terminal API verification
 * tracked in §13).
 */
class MentionResolver(private val project: Project) {
    fun buildContext(promptText: String): String? {
        val tokens = MentionTokenExtractor.extractTokens(promptText)
        if (tokens.isEmpty()) return null
        return tokens.mapNotNull(::resolveToken).joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun resolveToken(token: String): String? = when {
        token == "git-diff" -> buildGitDiffBlock()
        token == "terminal" -> null
        token == "docs" -> "(User referenced @docs — if relevant, use any configured MCP docs-search tool for this.)"
        token == "web" -> "(User referenced @web — if relevant, use any configured MCP web-search tool for this.)"
        token.endsWith("/") -> buildFolderBlock(token.removeSuffix("/"))
        else -> buildFileBlock(token)
    }

    private fun buildFileBlock(relativePath: String): String? {
        val projectDir = project.guessProjectDir() ?: return null
        val file = VfsUtilCore.findRelativeFile(relativePath, projectDir) ?: return null
        if (file.isDirectory) return buildFolderBlock(relativePath)
        val content = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() ?: return null
        return "@$relativePath:\n```\n$content\n```"
    }

    private fun buildFolderBlock(relativePath: String): String? {
        val projectDir = project.guessProjectDir() ?: return null
        val dir = VfsUtilCore.findRelativeFile(relativePath, projectDir) ?: return null
        if (!dir.isDirectory) return buildFileBlock(relativePath)
        val listing = dir.children
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            .joinToString("\n") { if (it.isDirectory) "${it.name}/" else it.name }
        return "@$relativePath/ (directory listing):\n$listing"
    }

    private fun buildGitDiffBlock(): String? {
        val workspace = project.basePath?.let(::File) ?: return null
        val staged = runGit(workspace, "diff", "--staged")
        val unstaged = runGit(workspace, "diff")
        if (staged.isNullOrBlank() && unstaged.isNullOrBlank()) return "@git-diff: (no uncommitted changes)"

        return buildString {
            append("@git-diff:")
            if (!staged.isNullOrBlank()) append("\n```diff\n# staged\n$staged\n```")
            if (!unstaged.isNullOrBlank()) append("\n```diff\n# unstaged\n$unstaged\n```")
        }
    }

    private fun runGit(workspace: File, vararg args: String): String? {
        return try {
            val commandLine = GeneralCommandLine("git", *args).withWorkDirectory(workspace)
            val output = ExecUtil.execAndGetOutput(commandLine)
            output.stdout.takeIf { output.exitCode == 0 }
        } catch (e: Exception) {
            null
        }
    }
}
