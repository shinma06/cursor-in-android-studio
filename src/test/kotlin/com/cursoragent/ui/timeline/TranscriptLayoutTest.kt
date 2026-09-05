package com.cursoragent.ui.timeline

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranscriptLayoutTest {
    @Test
    fun `short rows stay at natural height in a tall viewport`() = SwingUtilities.invokeAndWait {
        val panel = JPanel(TranscriptLayout(14))
        val first = JPanel().apply { preferredSize = Dimension(200, 32) }
        val second = JPanel().apply { preferredSize = Dimension(200, 48) }
        panel.add(first)
        panel.add(second)
        panel.setSize(400, 900)
        panel.doLayout()
        assertEquals(32, first.height)
        assertEquals(46, second.y)
        assertEquals(48, second.height)
        assertEquals(94, panel.preferredSize.height)
    }

    @Test
    fun `narrow transcript wraps text and removing a row leaves no gap`() = SwingUtilities.invokeAndWait {
        val panel = JPanel(TranscriptLayout(14))
        val message = JPanel(BorderLayout()).apply {
            add(JTextArea("A sentence that should wrap naturally. ".repeat(15)).apply {
                lineWrap = true
                wrapStyleWord = true
            })
        }
        val status = JPanel().apply { preferredSize = Dimension(100, 20) }
        panel.add(message)
        panel.add(status)
        panel.setSize(600, 900)
        panel.doLayout()
        val wideHeight = message.height
        panel.setSize(300, 900)
        panel.doLayout()
        assertTrue(message.height > wideHeight)
        assertEquals(300, message.width)
        assertEquals(message.height + 14, status.y)
        panel.remove(status)
        assertEquals(message.height, panel.preferredSize.height)
    }
}
