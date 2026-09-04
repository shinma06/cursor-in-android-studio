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
 * F-12/F-14/F-16; F-13/Terminal via [TerminalOutputReader]). Split into
 * [buildFileAndFolderContext] (EDT-safe) and
 * [buildShellBackedContext] (spawns a process, call off the EDT) — see their docs.
 */
class MentionResolver(private val project: Project) {
    /**
     * Split from [buildShellBackedContext] so callers can run the fast, VFS-only
     * half (file/folder reads) on the EDT and push the slow half (spawns `git`)
     * to a background thread — `sendPrompt` blocking the EDT on a git subprocess
     * for every single message would violate the requirements doc's own
     * non-functional requirement against EDT-blocking work (§7).
     */
    fun buildFileAndFolderContext(promptText: String): String? {
        val tokens = MentionTokenExtractor.extractTokens(promptText)
        val blocks = tokens.mapNotNull { token ->
            when {
                isFixedToken(token) -> null
                token.endsWith("/") -> buildFolderBlock(token.removeSuffix("/"))
                else -> buildFileBlock(token)
            }
        }
        return blocks.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    /** Call off the EDT — shells out to `git` and reads terminal output. */
    fun buildShellBackedContext(promptText: String): String? {
        val tokens = MentionTokenExtractor.extractTokens(promptText)
        val blocks = tokens.mapNotNull { token ->
            when (token) {
                "git-diff" -> buildGitDiffBlock()
                "branch" -> buildBranchDiffBlock()
                "terminal" -> buildTerminalBlock()
                "docs" -> "(User referenced @docs — if relevant, use any configured MCP docs-search tool for this.)"
                "web" -> "(User referenced @web — if relevant, use any configured MCP web-search tool for this.)"
                else -> null
            }
        }
        return blocks.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun isFixedToken(token: String) =
        token == "git-diff" || token == "branch" || token == "terminal" || token == "docs" || token == "web"

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

    private fun buildBranchDiffBlock(): String? {
        val workspace = project.basePath?.let(::File) ?: return null
        return BranchDiffBuilder.buildBlock(workspace) { dir, args -> runGit(dir, *args) }
    }

    private fun buildTerminalBlock(): String? {
        val output = TerminalOutputReader.readRecentOutputBlocking(project)
            ?: return "@terminal: (no open terminal tab or output unavailable)"
        return "@terminal (recent output):\n```\n$output\n```"
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
