package com.cursoragent.ui.timeline

import com.cursoragent.ui.AgentUiMetrics
import com.intellij.util.ui.JBUI
import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConversationDisplayTest {
    @Test
    fun `creating messages never replaces or grows the shared HTML stylesheet`() = SwingUtilities.invokeAndWait {
        val shared = javax.swing.text.html.HTMLEditorKit().styleSheet
        val parents = shared.styleSheets?.toList().orEmpty()
        val panes = (1..3).map { MessageTextPane() }
        assertSame(shared, javax.swing.text.html.HTMLEditorKit().styleSheet)
        assertEquals(parents, shared.styleSheets?.toList().orEmpty())
        val local = panes.map { (it.editorKit as javax.swing.text.html.HTMLEditorKit).styleSheet }
        assertEquals(3, local.toSet().size)
        local.forEach { assertEquals(listOf(shared), it.styleSheets.toList()) }
    }

    @Test
    fun `wrapping long code retains document whitespace selection and raw copy`() = SwingUtilities.invokeAndWait {
        val raw = "本文\n\n```kotlin\n\tval 日本語 = \"${"abcdefgh".repeat(30)}\"  \n    second()\n```"
        val copies = mutableListOf<String>()
        val bubble = AssistantMessageBubble(raw) { copies.add(it) }
        val pane = descendants(bubble).filterIsInstance<MessageTextPane>().single()
        val scroller = descendants(bubble).filterIsInstance<JScrollPane>().single()
        val transcript = JPanel(TranscriptLayout(14)).apply { add(bubble); setSize(300, 900) }
        fun layout() { repeat(3) { transcript.doLayout() } }
        layout()
        val original = pane.document
        val text = original.getText(0, original.length)
        assertTrue(text.contains("\tval 日本語"))
        val start = text.indexOf("abcdefgh")
        pane.caret.setDot(start + 60)
        pane.caret.moveDot(start)
        val selected = pane.selectedText
        val unwrappedHeight = bubble.height
        assertTrue(scroller.horizontalScrollBar.isVisible)
        pane.applyDisplaySettings(20, true)
        layout()
        assertSame(original, pane.document)
        assertEquals(text, pane.document.getText(0, pane.document.length))
        assertEquals(selected, pane.selectedText)
        assertEquals(start, pane.caret.dot)
        assertEquals(start + 60, pane.caret.mark)
        assertTrue(bubble.height > unwrappedHeight)
        assertFalse(scroller.horizontalScrollBar.isVisible)
        assertTrue(pane.modelToView2D(start + 200).y > pane.modelToView2D(start).y)
        descendants(bubble).filterIsInstance<JButton>().single().doClick(0)
        assertEquals(listOf(raw), copies)
        pane.applyDisplaySettings(0, false)
        layout()
        assertTrue(scroller.horizontalScrollBar.isVisible)
        assertEquals(unwrappedHeight, bubble.height)
        assertEquals(selected, pane.selectedText)
    }

    @Test
    fun `native theme refresh and font changes keep two messages isolated and preserve selection`() = SwingUtilities.invokeAndWait {
        val first = MessageTextPane().apply { text = "<html>日本語の本文 ${"sample ".repeat(40)}</html>"; setSize(280, 1) }
        val second = MessageTextPane().apply { text = "<html>別の会話</html>" }
        val oldFont = second.font
        val oldDocument = first.document
        first.select(2, 8)
        val selection = first.selectedText
        val oldHeight = first.preferredSize.height
        first.applyDisplaySettings(28, true)
        assertEquals(JBUI.Fonts.label(28f).size2D, first.font.size2D)
        assertTrue(first.preferredSize.height > oldHeight)
        first.updateUI()
        assertEquals(selection, first.selectedText)
        assertSame(oldDocument, first.document)
        assertEquals(oldFont, second.font)
        first.applyDisplaySettings(-50, false)
        assertEquals(AgentUiMetrics.textFont(), first.font)
        assertEquals(selection, first.selectedText)
    }

    @Test
    fun `nested message forwards vertical wheel and keeps horizontal wheel local`() = SwingUtilities.invokeAndWait {
        val pane = MessageTextPane().apply { text = "<html><pre>${"code".repeat(200)}</pre></html>" }
        val inner = pane.scrollable()
        val transcript = JPanel(TranscriptLayout(14)).apply { add(inner); setSize(250, 1000); doLayout() }
        val outer = JScrollPane(transcript)
        val received = mutableListOf<java.awt.event.MouseWheelEvent>()
        outer.addMouseWheelListener { received.add(it) }
        fun wheel(modifiers: Int) = java.awt.event.MouseWheelEvent(inner, java.awt.event.MouseEvent.MOUSE_WHEEL,
            1L, modifiers, 20, 20, 20, 20, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.25)
        inner.dispatchEvent(wheel(0))
        assertEquals(1, received.size)
        assertEquals(1.25, received.single().preciseWheelRotation)
        inner.dispatchEvent(wheel(java.awt.event.InputEvent.SHIFT_DOWN_MASK))
        assertEquals(1, received.size)
    }

    private fun descendants(parent: Container): Sequence<java.awt.Component> = sequence {
        for (child in parent.components) {
            yield(child)
            if (child is Container) yieldAll(descendants(child))
        }
    }
}
