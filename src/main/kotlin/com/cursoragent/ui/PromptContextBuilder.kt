package com.cursoragent.ui

import com.cursoragent.ui.composer.mention.MentionResolver
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil

/**
 * Assembles the prompt string sent to the CLI: active-file context, @mention
 * expansions, then the user's raw input. Split from [AgentUiController] (#11).
 */
class PromptContextBuilder(
    private val project: Project,
    private val mentionResolver: MentionResolver,
) {
    /** VFS reads — call on the EDT before starting a background thread. */
    fun buildEdtContext(userText: String): String? {
        val parts = listOfNotNull(
            buildActiveFileContext(),
            mentionResolver.buildFileAndFolderContext(userText),
        )
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    /** Spawns `git` / reads terminal — call off the EDT. */
    fun buildBackgroundContext(userText: String): String? =
        mentionResolver.buildShellBackedContext(userText)

    fun assemble(fullContext: String?, userText: String): String = buildString {
        if (!fullContext.isNullOrBlank()) append(fullContext).append("\n\n")
        append(userText)
    }

    private fun buildActiveFileContext(): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null

        val selectedText = editor.selectionModel.selectedText?.trim().orEmpty()
        val projectDir = project.guessProjectDir()
        val relativePath = projectDir?.let { VfsUtil.getRelativePath(file, it) } ?: file.path

        return buildString {
            append("Active file: $relativePath")
            if (selectedText.isNotEmpty()) {
                append("\nSelection:\n```\n$selectedText\n```")
            }
        }
    }
}
