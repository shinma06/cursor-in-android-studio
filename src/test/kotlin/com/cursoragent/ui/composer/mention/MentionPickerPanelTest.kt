package com.cursoragent.ui.composer.mention

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class MentionPickerPanelTest {
    @Test
    fun `search reaches matching files beyond the initial display cap`() {
        val all = (0 until 1_200).map { Mention(MentionKind.FILE, "file-$it.kt", "file-$it.kt") }
        val initial = MentionCandidateSearch("")
        for (candidate in all) if (!initial.visit(candidate)) break
        assertEquals(500, initial.matches.size)
        val search = MentionCandidateSearch("file-1100")
        for (candidate in all) if (!search.visit(candidate)) break
        assertEquals(listOf(all[1100]), search.matches)
    }

    @Test
    fun `IME composition cannot choose or cancel a candidate`() = SwingUtilities.invokeAndWait {
        var chosen = 0
        var cancelled = 0
        val panel = MentionPickerPanel({ chosen++ }, { cancelled++ })
        panel.loaded(listOf(Mention(MentionKind.FILE, "日本語.kt", "日本語.kt")))
        val event = java.awt.event.InputMethodEvent(panel.search, java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
            java.text.AttributedString("にほん").iterator, 0, null, null)
        panel.search.inputMethodListeners.forEach { it.inputMethodTextChanged(event) }
        panel.search.actionMap.get("ENTER").actionPerformed(null)
        panel.search.actionMap.get("ESCAPE").actionPerformed(null)
        assertEquals(0, chosen)
        assertEquals(0, cancelled)
    }

    @Test
    fun `query entered while loading filters later candidates and supports zero many choose cancel`() = SwingUtilities.invokeAndWait {
        val chosen = mutableListOf<Mention>()
        var cancelled = 0
        val panel = MentionPickerPanel(chosen::add, { cancelled++ })
        panel.search.text = "日本語"
        assertEquals(0, panel.model.size)
        val candidates = (0 until 500).map { Mention(MentionKind.FILE, "日本語 file $it.kt", "日本語 file $it.kt") }
        panel.loaded(candidates)
        assertEquals(500, panel.model.size)
        panel.search.actionMap.get("DOWN").actionPerformed(null)
        panel.search.actionMap.get("ENTER").actionPerformed(null)
        assertEquals(listOf(candidates[1]), chosen)
        panel.search.text = "not found"
        assertEquals(0, panel.model.size)
        panel.search.actionMap.get("ENTER").actionPerformed(null)
        assertEquals(1, chosen.size)
        panel.search.actionMap.get("ESCAPE").actionPerformed(null)
        assertEquals(1, cancelled)
        panel.failed()
        assertTrue(panel.list.emptyText.text.contains("取得できません"))
    }
}
