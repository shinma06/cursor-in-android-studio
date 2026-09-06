package com.cursoragent.ui.header

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Rectangle

class HeaderOptionsPopupTest {
    @Test
    fun `header menu stays below visible trigger and inside screen including short negative-coordinate displays`() {
        for (screen in listOf(Rectangle(0, 0, 1200, 900), Rectangle(-1200, -300, 1200, 400))) {
            for (anchorX in listOf(screen.x + 10, screen.x + screen.width - 40)) {
                val anchor = Rectangle(anchorX, screen.y + 50, 26, 26)
                val bounds = headerPopupBounds(anchor, Dimension(350, 500), screen, 4)
                assertEquals(anchor.y + anchor.height + 4, bounds.y)
                assertTrue(screen.contains(bounds))
                assertFalse(anchor.intersects(bounds))
                assertEquals(minOf(500, screen.y + screen.height - bounds.y), bounds.height)
            }
        }
    }

    @Test
    fun `wide menu is constrained to narrow display and normal menu aligns to right edge`() {
        val screen = Rectangle(0, 0, 300, 500)
        val anchor = Rectangle(250, 20, 26, 26)
        assertEquals(Rectangle(0, 50, 300, 400), headerPopupBounds(anchor, Dimension(350, 400), screen, 4))
        assertEquals(anchor.x + anchor.width - 200, headerPopupBounds(anchor, Dimension(200, 100), screen, 4).x)
    }
}
