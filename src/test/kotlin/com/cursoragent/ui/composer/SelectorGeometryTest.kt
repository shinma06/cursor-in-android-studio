package com.cursoragent.ui.composer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicButtonUI

class SelectorGeometryTest {
    @Test
    fun `IDE delegate minimum width does not inflate selector hit target`() = SwingUtilities.invokeAndWait {
        val button = SelectorButton().apply { text = "Auto"; showsChevron = true }
        val natural = button.preferredSize
        button.setUI(object : BasicButtonUI() {
            override fun getPreferredSize(c: JComponent) = Dimension(500, 80)
        })
        assertEquals(natural, button.preferredSize)
        button.text = "Composer 2.5"
        assertTrue(button.preferredSize.width > natural.width)
        assertTrue(button.preferredSize.width < 500)
        button.preferredSize = Dimension(24, 24)
        assertEquals(Dimension(24, 24), button.preferredSize)
    }

    @Test
    fun `chevron remains vertically centered independently of font baseline`() = SwingUtilities.invokeAndWait {
        for (fontSize in listOf(11f, 18f)) {
            val button = SelectorButton().apply {
                text = ""
                showsChevron = true
                foreground = Color.WHITE
                font = font.deriveFont(fontSize)
                setSize(60, 32)
            }
            val image = BufferedImage(button.width, button.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try { button.paint(graphics) } finally { graphics.dispose() }
            val paintedRows = (0 until image.height).filter { y ->
                (0 until image.width).any { x -> (image.getRGB(x, y) ushr 24) > 0 }
            }
            assertTrue(paintedRows.isNotEmpty())
            assertEquals(button.height / 2.0, (paintedRows.first() + paintedRows.last()) / 2.0, 1.0)
        }
    }
}
