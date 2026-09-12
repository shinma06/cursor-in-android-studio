package com.cursoragent.ui.composer.context

import com.cursoragent.ui.composer.mention.Mention
import com.cursoragent.ui.composer.mention.MentionKind

/** Immutable editor selection. Its document stamp is checked before a new send/queue registration. */
data class SelectionContext(
    val fileUrl: String,
    val path: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val documentStamp: Long,
) {
    init {
        require(fileUrl.isNotBlank() && path.isNotBlank())
        require(startOffset >= 0 && endOffset > startOffset && startLine > 0 && endLine >= startLine)
        require(text.isNotEmpty())
    }

    val key: String get() = "$fileUrl:$startOffset:$endOffset"
    val label: String get() = "$path:$startLine–$endLine"
    fun block(): String = "Selection: $label\n```\n$text\n```"
}

data class EditorContext(val fileUrl: String, val path: String, val selection: SelectionContext?)

/** A send/queue owns a copy; editing another draft never changes its explicit attachments. */
data class PromptContextSnapshot(
    val selections: List<SelectionContext>,
    val mentions: List<Mention>,
    val automaticEnabled: Boolean,
) {
    fun selectionBlocks(automatic: EditorContext?, filePaths: Set<String>): List<String> {
        val explicit = selections.filter { it.path !in filePaths }
        val autoSelection = automatic?.selection?.takeIf { automaticEnabled && it.path !in filePaths }
            ?.takeUnless { candidate -> explicit.any { it.key == candidate.key } }
        return (explicit + listOfNotNull(autoSelection)).map(SelectionContext::block)
    }
}

/** Owned by one Composer, independent from prompt text and other conversation tabs. */
class PromptContextDraft {
    private val selections = linkedMapOf<String, SelectionContext>()
    private val mentions = linkedMapOf<Pair<MentionKind, String>, Mention>()
    var automaticEnabled = true

    fun add(selection: SelectionContext) { selections[selection.key] = selection }
    fun add(mention: Mention) { mentions[mention.kind to mention.insertToken] = mention }
    fun removeSelection(key: String) { selections.remove(key) }
    fun removeMention(mention: Mention) { mentions.remove(mention.kind to mention.insertToken) }
    fun replaceSelection(key: String, selection: SelectionContext) {
        selections.remove(key)
        add(selection)
    }
    fun clearExplicit() { selections.clear(); mentions.clear() }
    fun snapshot(isCurrent: (SelectionContext) -> Boolean = { true }): PromptContextSnapshot {
        val stale = selections.values.filterNot(isCurrent)
        require(stale.isEmpty()) { "追加後に変更された選択があります。再追加または削除してください: ${stale.joinToString { it.label }}" }
        return PromptContextSnapshot(selections.values.toList(), mentions.values.toList(), automaticEnabled)
    }
}
