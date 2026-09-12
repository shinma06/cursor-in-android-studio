package com.cursoragent.ui.timeline

import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssistantMessageCopyTest {
    @Test
    fun `copy preserves Markdown source whitespace entities and literal HTML`() = SwingUtilities.invokeAndWait {
        val raw = "  # 見出し\r\n\r\n**強調** &amp; <tag>\n```kotlin\n\tval x = 1  \n```\n"
        val copies = mutableListOf<String>()
        val bubble = AssistantMessageBubble(raw) { copies.add(it) }
        val button = button(bubble)
        button.doClick(0)
        assertEquals(listOf(raw), copies)
        assertEquals("コピー済み", button.text)
        assertEquals("Markdown原文をコピーしました", button.accessibleContext.accessibleDescription)
    }

    @Test
    fun `empty is disabled and streaming replaces the copied snapshot including corrections`() = SwingUtilities.invokeAndWait {
        val copies = mutableListOf<String>()
        val bubble = AssistantMessageBubble(copyMarkdown = { copies.add(it) })
        val button = button(bubble)
        assertFalse(button.isEnabled)
        button.doClick(0)
        assertTrue(copies.isEmpty())
        bubble.setContent("**途")
        button.doClick(0)
        bubble.setContent("**途中**")
        assertEquals("コピー", button.text)
        button.doClick(0)
        bubble.setContent("訂正した本文")
        button.doClick(0)
        bubble.setContent("訂正した本文")
        assertEquals("コピー済み", button.text)
        bubble.setContent("")
        assertFalse(button.isEnabled)
        button.doClick(0)
        bubble.setContent(" \n\t")
        button.doClick(0)
        assertEquals(listOf("**途", "**途中**", "訂正した本文", " \n\t"), copies)
    }

    @Test
    fun `clipboard failure offers retry without exposing exception or losing source`() = SwingUtilities.invokeAndWait {
        var failing = true
        val copies = mutableListOf<String>()
        val bubble = AssistantMessageBubble("本文") {
            if (failing) throw IllegalStateException("private clipboard diagnostic")
            copies.add(it)
        }
        val button = button(bubble)
        button.doClick(0)
        assertEquals("コピー失敗", button.text)
        assertTrue(button.isEnabled)
        assertFalse(button.toolTipText.contains("private"))
        assertTrue(copies.isEmpty())
        failing = false
        button.doClick(0)
        assertEquals(listOf("本文"), copies)
        assertEquals("コピー済み", button.text)
    }

    @Test
    fun `other responses and later turns cannot replace the source of an earlier bubble`() = SwingUtilities.invokeAndWait {
        val copies = mutableListOf<String>()
        val first = AssistantMessageBubble("停止までの本文") { copies.add(it) }
        val second = AssistantMessageBubble("別タブの本文") { copies.add(it) }
        second.setContent("別タブの続き")
        button(first).doClick(0)
        button(second).doClick(0)
        button(first).doClick(0)
        assertEquals(listOf("停止までの本文", "別タブの続き", "停止までの本文"), copies)
    }

    @Test
    fun `copy remains below reflowed text at narrow widths and supports focused keyboard activation`() = SwingUtilities.invokeAndWait {
        val bubble = AssistantMessageBubble("日本語の長い応答です。 ".repeat(40)) {}
        val transcript = JPanel(TranscriptLayout(14)).apply { add(bubble) }
        val button = button(bubble)
        assertTrue(button.isFocusable)
        assertFalse(button.isDefaultCapable)
        assertEquals("Markdown原文をコピー", button.accessibleContext.accessibleName)
        for (key in listOf("pressed ENTER", "released ENTER", "pressed SPACE", "released SPACE")) {
            val action = button.inputMap.get(KeyStroke.getKeyStroke(key))
            assertTrue(action != null && button.actionMap.get(action) != null, key)
        }
        transcript.setSize(600, 900)
        transcript.doLayout()
        val wideHeight = bubble.height
        transcript.setSize(200, 900)
        transcript.doLayout()
        assertTrue(bubble.height > wideHeight)
        button.doClick(0)
        transcript.doLayout()
        val pane = bubble.components.filterIsInstance<MessageTextPane>().single()
        val footer = button.parent
        assertTrue(footer.y >= pane.y + pane.height)
        assertTrue(button.x >= 0 && button.x + button.width <= footer.width)
        assertTrue(footer.y + footer.height <= bubble.height)
    }

    private fun button(container: Container): JButton = container.components
        .filterIsInstance<Container>().flatMap { it.components.toList() }.filterIsInstance<JButton>().single()
}
