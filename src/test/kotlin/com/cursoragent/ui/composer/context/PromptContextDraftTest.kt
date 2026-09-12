package com.cursoragent.ui.composer.context

import com.cursoragent.ui.composer.mention.Mention
import com.cursoragent.ui.composer.mention.MentionKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PromptContextDraftTest {
    private fun selection(text: String = "old", stamp: Long = 1) =
        SelectionContext("file:///project/A.kt", "A.kt", 0, 3, 1, 1, text, stamp)

    @Test
    fun `changed selection prevents a new snapshot but an accepted queue item stays frozen`() {
        val draft = PromptContextDraft()
        draft.add(selection())
        val accepted = draft.snapshot { true }
        assertThrows(IllegalArgumentException::class.java) { draft.snapshot { false } }
        assertEquals("old", accepted.selections.single().text)
    }

    @Test
    fun `same selection replaces old content and explicit mention is independent from prompt text`() {
        val draft = PromptContextDraft()
        draft.add(selection())
        draft.add(selection("new", 2))
        val mention = Mention(MentionKind.FILE, "日本語 file.kt", "日本語 file.kt")
        draft.add(mention)
        draft.add(mention)
        assertEquals(listOf(selection("new", 2)), draft.snapshot().selections)
        assertEquals(listOf(mention), draft.snapshot().mentions)
        draft.removeSelection(selection().key)
        draft.removeMention(mention)
        assertTrue(draft.snapshot().selections.isEmpty())
        assertTrue(draft.snapshot().mentions.isEmpty())
    }

    @Test
    fun `send and queue snapshots survive draft replacement deletion and another tab`() {
        val first = PromptContextDraft()
        val other = PromptContextDraft()
        first.add(selection())
        val queued = first.snapshot()
        first.replaceSelection(selection().key, selection("new", 2))
        first.automaticEnabled = false
        first.clearExplicit()
        other.add(selection("tab", 3))
        assertEquals(listOf(selection()), queued.selections)
        assertTrue(queued.automaticEnabled)
        assertTrue(first.snapshot().selections.isEmpty())
        assertEquals("tab", other.snapshot().selections.single().text)
    }

    @Test
    fun `explicit selection deduplicates automatic range and attached full file`() {
        val draft = PromptContextDraft()
        draft.add(selection())
        val automatic = EditorContext(selection().fileUrl, "A.kt", selection())
        assertEquals(listOf(selection().block()), draft.snapshot().selectionBlocks(automatic, emptyMap()))
        assertTrue(draft.snapshot().selectionBlocks(automatic, mapOf("A.kt" to "old")).isEmpty())
        assertEquals(listOf(selection().block()), draft.snapshot().selectionBlocks(automatic, mapOf("A.kt" to "new")))
        draft.clearExplicit()
        draft.automaticEnabled = false
        assertTrue(draft.snapshot().selectionBlocks(automatic, emptyMap()).isEmpty())
    }
}
