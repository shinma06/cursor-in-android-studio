package com.cursoragent.ui

import com.cursoragent.ui.composer.context.EditorContextReader
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import com.cursoragent.ui.composer.mention.MentionResolver
import com.intellij.openapi.project.Project

/** Snapshot explicit attachments with their owning request; resolve references when that turn starts. */
class PromptContextBuilder(
    private val project: Project,
    private val mentionResolver: MentionResolver,
) {
    /** Editor/VFS/Terminal reads run on EDT. Queued snapshots do not read another draft's chips. */
    fun buildEdtContext(userText: String, snapshot: PromptContextSnapshot? = null): String? {
        val context = snapshot ?: PromptContextSnapshot(emptyList(), emptyList(), true)
        val automatic = if (context.automaticEnabled) EditorContextReader.current(project) else null
        val fileContents = mutableMapOf<String, String>()
        val referenceContext = mentionResolver.buildFileAndFolderContext(userText, context.mentions) { path, content -> fileContents[path] = content }
        val selections = context.selectionBlocks(automatic, fileContents)
        val activeFile = automatic?.takeUnless { it.path in fileContents || context.selections.any { selection -> selection.fileUrl == it.fileUrl } }
            ?.let { "Active file: ${it.path}" }
        return (listOfNotNull(activeFile, referenceContext) + selections)
            .joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    /** Git-backed references are resolved off EDT; explicit attachment identities stay fixed. */
    fun buildBackgroundContext(userText: String, snapshot: PromptContextSnapshot? = null): String? =
        mentionResolver.buildShellBackedContext(userText, snapshot?.mentions.orEmpty())

    fun assemble(fullContext: String?, userText: String): String = buildString {
        if (!fullContext.isNullOrBlank()) append(fullContext).append("\n\n")
        append(userText)
    }
}
