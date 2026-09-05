package com.cursoragent.ui.timeline

import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageTextPaneTest {
    @Test
    fun `HTML reflows on width change and cached height updates after streaming text`() = SwingUtilities.invokeAndWait {
        val pane = MessageTextPane()
        val row = JPanel(BorderLayout()).apply { add(pane) }
        val transcript = JPanel(TranscriptLayout(14)).apply { add(row) }
        pane.text = "<html><p>${"日本語の応答と折り返しを確認します。 ".repeat(12)}</p></html>"
        transcript.setSize(600, 900)
        transcript.doLayout()
        val wideHeight = row.height
        transcript.setSize(300, 900)
        transcript.doLayout()
        assertTrue(row.height > wideHeight)
        val narrowHeight = row.height
        pane.text = "<html><p>こんにちは。</p></html>"
        transcript.doLayout()
        assertTrue(row.height < narrowHeight)
        assertTrue(row.height in 10..80)
    }
}
