package com.cursoragent.ui.timeline

import java.awt.event.InputMethodEvent
import java.text.AttributedString
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConversationFindPanelTest {
    @Test
    fun `zero width markers paint at text and HTML boundaries and clear without changing selection`() {
        for ((html, query) in listOf(false to "\\A", false to "(?m)^", false to "\\z", true to "\\A", true to "(?m)^", true to "\\z")) {
            val jobs = mutableListOf<() -> Unit>()
            lateinit var pane: javax.swing.text.JTextComponent
            lateinit var panel: ConversationFindPanel
            lateinit var before: java.awt.image.BufferedImage
            lateinit var original: String
            lateinit var offsets: List<Int>
            fun render(): java.awt.image.BufferedImage {
                val image = java.awt.image.BufferedImage(pane.width, pane.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                image.createGraphics().let { graphics -> try { pane.paint(graphics) } finally { graphics.dispose() } }
                return image
            }
            onEdt {
                pane = if (html) AssistantMessageBubble("alpha\n\nbeta").searchableText else javax.swing.JTextArea("ab\ncd").apply { lineWrap = true; wrapStyleWord = true }
                pane.setSize(300, 120)
                pane.select(1, 2)
                render() // Initialize the text views before taking the comparison image.
                before = render()
                original = pane.document.getText(0, pane.document.length)
                offsets = when (query) { "\\z" -> listOf(original.length); "(?m)^" -> if (html) listOf(0, 1, 7) else listOf(0, 3); else -> listOf(0) }
                panel = ConversationFindPanel({ listOf(pane) }, { true }, {}, { jobs.add(it); CompletableFuture<Unit>() })
                panel.open()
                panel.regex.isSelected = true
                panel.search.text = query
                panel.searchNow()
            }
            jobs.removeFirst().invoke()
            onEdt {
                val after = render()
                assertEquals("1 / ${offsets.size}", panel.count.text)
                for (offset in offsets) {
                    val bounds = pane.modelToView2D(offset).bounds
                    val height = bounds.height.coerceAtLeast(pane.getFontMetrics(pane.font).height)
                    val changed = (bounds.x until (bounds.x + 3).coerceAtMost(pane.width)).sumOf { x ->
                        (bounds.y until (bounds.y + height).coerceAtMost(pane.height)).count { y -> before.getRGB(x, y) != after.getRGB(x, y) }
                    }
                    assertTrue(changed > 0, "zero-width marker must paint: html=$html query=$query offset=$offset")
                }
                if (offsets.size > 1) {
                    panel.move(-1)
                    assertEquals("${offsets.size} / ${offsets.size}", panel.count.text)
                    val moved = render()
                    assertFalse(after.getRGB(0, 0, pane.width, pane.height, null, 0, pane.width).contentEquals(moved.getRGB(0, 0, pane.width, pane.height, null, 0, pane.width)), "current marker must move independently from all matches")
                }
                assertEquals(original, pane.document.getText(0, pane.document.length))
                assertEquals(1, pane.selectionStart)
                assertEquals(2, pane.selectionEnd)
                panel.closeSearch()
                val cleared = render()
                assertArrayEquals(before.getRGB(0, 0, pane.width, pane.height, null, 0, pane.width), cleared.getRGB(0, 0, pane.width, pane.height, null, 0, pane.width))
                panel.dispose()
            }
        }
    }

    @Test
    fun `a match in long code scrolls both the inner horizontal and outer transcript viewport`() {
        val jobs = mutableListOf<() -> Unit>()
        lateinit var panel: ConversationFindPanel
        lateinit var outer: javax.swing.JScrollPane
        lateinit var inner: javax.swing.JScrollPane
        onEdt {
            val code = (1..80).joinToString("\n") { if (it == 80) "x".repeat(160) + "needle" else "line $it" }
            val bubble = AssistantMessageBubble("```\n$code\n```")
            val transcript = javax.swing.JPanel(TranscriptLayout(14)).apply { add(bubble) }
            outer = javax.swing.JScrollPane(transcript).apply { setSize(260, 180); doLayout() }
            transcript.setSize(outer.viewport.extentSize.width, 2_000)
            repeat(4) {
                transcript.doLayout()
                transcript.setSize(outer.viewport.extentSize.width, transcript.preferredSize.height)
                outer.viewport.viewSize = transcript.size
                outer.doLayout()
            }
            inner = bubble.components.filterIsInstance<javax.swing.JScrollPane>().single()
            assertTrue(transcript.height > 500, "fixture must overflow the transcript")
            outer.viewport.viewPosition = java.awt.Point(0, 0)
            inner.viewport.viewPosition = java.awt.Point(0, 0)
            panel = ConversationFindPanel({ listOf(bubble.searchableText) }, { true }, {}, { jobs.add(it); CompletableFuture<Unit>() })
            panel.open()
            panel.search.text = "needle"
            panel.searchNow()
        }
        jobs.removeFirst().invoke()
        onEdt {
            assertEquals("1 / 1", panel.count.text)
            assertTrue(outer.viewport.viewPosition.y > 100, "the transcript must reveal the match's line, not merely its message")
            assertTrue(inner.viewport.viewPosition.x > 100, "the code viewport must reveal the matching column")
            val pane = inner.viewport.view as MessageTextPane
            val offset = pane.document.getText(0, pane.document.length).indexOf("needle")
            val point = SwingUtilities.convertPoint(pane, pane.modelToView2D(offset).bounds.location, outer.viewport.view)
            assertTrue(outer.viewport.viewRect.contains(point), "the matching line and column must actually be in view")
            panel.dispose()
        }
    }

    @Test
    fun `search highlights rendered positions cycles and preserves source selection and other highlights`() {
        val jobs = mutableListOf<() -> Unit>()
        lateinit var pane: MessageTextPane
        lateinit var panel: ConversationFindPanel
        var closed = 0
        var text = ""
        lateinit var document: javax.swing.text.Document
        onEdt {
            pane = AssistantMessageBubble("**日本語** 日本語\n\n```kotlin\nval 日本語 = 1\n```").searchableText
            pane.setSize(250, 400)
            document = pane.document
            text = document.getText(0, document.length)
            pane.select(1, 4)
            pane.highlighter.addHighlight(1, 2, javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.BLUE))
            panel = ConversationFindPanel({ listOf(pane) }, { true }, { closed++ }, { jobs.add(it); CompletableFuture<Unit>() })
            panel.open()
            panel.search.text = "日本語"
            panel.searchNow()
        }
        jobs.removeFirst().invoke()
        onEdt {
            assertEquals("1 / 3", panel.count.text)
            val ranges = pane.highlighter.highlights.filter { it.endOffset - it.startOffset == 3 }
            assertEquals(4, ranges.size) // all three matches plus the selected match
            ranges.forEach { assertEquals("日本語", text.substring(it.startOffset, it.endOffset)) }
            panel.move(-1)
            assertEquals("3 / 3", panel.count.text)
            panel.move(1)
            assertEquals("1 / 3", panel.count.text)
            assertSame(document, pane.document)
            assertEquals(text, document.getText(0, document.length))
            assertEquals(1, pane.selectionStart)
            assertEquals(4, pane.selectionEnd)
            panel.closeSearch()
            assertFalse(panel.isVisible)
            assertEquals(1, closed)
            assertEquals(1, pane.highlighter.highlights.size)
            panel.dispose()
        }
    }

    @Test
    fun `late search cannot paint after text refresh tab switch IME close or disposal`() {
        val jobs = mutableListOf<() -> Unit>()
        lateinit var pane: MessageTextPane
        lateinit var panel: ConversationFindPanel
        var active = true
        onEdt {
            pane = AssistantMessageBubble("old old").searchableText
            panel = ConversationFindPanel({ listOf(pane) }, { active }, {}, { jobs.add(it); CompletableFuture<Unit>() })
            panel.open()
            panel.search.text = "old"
            panel.searchNow()
            pane.text = "<html>new</html>"
            panel.invalidateResults()
        }
        jobs.removeFirst().invoke()
        onEdt {
            assertEquals(0, pane.highlighter.highlights.size)
            panel.search.text = "new"
            panel.searchNow()
            active = false
            panel.invalidateResults()
        }
        jobs.removeFirst().invoke()
        onEdt {
            assertEquals(0, pane.highlighter.highlights.size)
            active = true
            panel.invalidateResults()
            panel.searchNow()
            panel.search.textEditor.inputMethodListeners.forEach {
                it.inputMethodTextChanged(InputMethodEvent(panel.search.textEditor, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    AttributedString("変換中").iterator, 0, null, null))
            }
            panel.closeSearch()
            assertTrue(panel.isVisible, "IME must keep Escape/close from altering search")
        }
        jobs.removeFirst().invoke()
        onEdt {
            assertEquals(0, pane.highlighter.highlights.size)
            panel.search.textEditor.inputMethodListeners.forEach {
                it.inputMethodTextChanged(InputMethodEvent(panel.search.textEditor, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, null, 0, null, null))
            }
            panel.searchNow()
            panel.closeSearch()
        }
        jobs.removeFirst().invoke()
        onEdt {
            assertEquals(0, pane.highlighter.highlights.size)
            panel.open()
            panel.searchNow()
            panel.dispose()
        }
        jobs.removeFirst().invoke()
        onEdt { assertEquals(0, pane.highlighter.highlights.size) }
    }

    @Test
    fun `timeline scope includes restored display bodies and skips tools statuses and request labels`() = onEdt {
        val timeline = ChatTimelinePanel()
        val conversation = com.cursoragent.history.Conversation(turns = listOf(com.cursoragent.history.SavedTurn(
            state = "completed", messages = listOf(
                com.cursoragent.history.ChatMessage(role = "user", text = "user <tag>"),
                com.cursoragent.history.ChatMessage(role = "assistant", text = "**assistant**"),
                com.cursoragent.history.ChatMessage(role = "assistant", text = "表示metadata", presentation = "acp_content"),
                com.cursoragent.history.ChatMessage(role = "tool", text = "tool secret"),
                com.cursoragent.history.ChatMessage(role = "error", text = "error secret"),
            ),
        )))
        timeline.restore(conversation)
        val text = timeline.searchableBodies().map { it.document.getText(0, it.document.length) }
        assertEquals(3, text.size)
        assertTrue(text[0].contains("user <tag>"))
        assertTrue(text[1].contains("assistant"))
        assertFalse(text[1].contains("**"))
        assertTrue(text[2].contains("表示metadata"))
        assertFalse(text.any { it.contains("secret") })
        assertEquals("**assistant**", conversation.turns.single().messages[1].text)
        timeline.dispose()
    }

    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
}
