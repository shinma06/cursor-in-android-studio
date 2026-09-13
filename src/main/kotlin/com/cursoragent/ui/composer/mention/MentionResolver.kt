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
     * Split from [buildShellBackedContext] so callers can run the fast, EDT-bound
     * half (file/folder VFS reads, and terminal output — the Terminal API also
     * requires the EDT) on the EDT and push the slow half (spawns `git`) to a
     * background thread — `sendPrompt` blocking the EDT on a git subprocess for
     * every single message would violate the requirements doc's own
     * non-functional requirement against EDT-blocking work (§7).
     */
    fun buildFileAndFolderContext(
        promptText: String,
        explicit: List<Mention> = emptyList(),
        onFileContent: (String, String) -> Unit = { _, _ -> },
    ): String? {
        val blocks = contextMentions(promptText, explicit).mapNotNull { mention ->
            when (mention.kind) {
                MentionKind.TERMINAL -> buildTerminalBlock()
                MentionKind.FILE -> buildFileBlock(mention.insertToken, onFileContent)
                    ?: "@${mention.insertToken}: (file unavailable; no contents attached)"
                MentionKind.FOLDER -> buildFolderBlock(mention.insertToken.removeSuffix("/"))
                    ?: "@${mention.insertToken}: (folder unavailable; no listing attached)"
                else -> null
            }
        }
        return blocks.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    /** Call off the EDT — shells out to `git`. */
    fun buildShellBackedContext(promptText: String, explicit: List<Mention> = emptyList()): String? {
        val blocks = contextMentions(promptText, explicit).mapNotNull { mention ->
            when (mention.kind) {
                MentionKind.GIT_DIFF -> buildGitDiffBlock()
                MentionKind.BRANCH -> buildBranchDiffBlock()
                MentionKind.DOCS -> "(User referenced Docs — hint only; use a configured docs tool if available. No documentation was fetched by this attachment.)"
                MentionKind.WEB -> "(User referenced Web — hint only; use an available web tool if relevant. No search was run by this attachment.)"
                else -> null
            }
        }
        return blocks.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun buildFileBlock(relativePath: String, onContent: (String, String) -> Unit = { _, _ -> }): String? {
        val projectDir = project.guessProjectDir() ?: return null
        val file = VfsUtilCore.findRelativeFile(relativePath, projectDir) ?: return null
        if (file.isDirectory) return buildFolderBlock(relativePath)
        if (file.fileType.isBinary) return "@$relativePath: (binary contents not attached)"
        val content = runCatching {
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)?.text
                ?: String(file.contentsToByteArray(), Charsets.UTF_8)
        }.getOrNull() ?: return null
        onContent(relativePath.removePrefix("./"), content)
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
