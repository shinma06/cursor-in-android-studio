package com.cursoragent.ui.composer.context

import com.cursoragent.parser.TokenUsage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Container
import java.awt.image.BufferedImage
import kotlin.math.ceil
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities

class ContextUsageTest {
    private val usage = TokenUsage(21022, 31, 8896, 0)

    @Test
    fun `painted focus and document share the center at odd sizes and device scales`() {
        SwingUtilities.invokeAndWait {
            val glyph = ContextUsageView().button.icon
            val button = object : TokenCountsButton() {
                var focused = false
                override fun hasFocus() = focused
            }
            fun paintedCenter(size: Int, scale: Double, focus: Boolean): Pair<Double, Double> {
                button.setSize(size, size)
                button.focused = focus
                button.icon = if (focus) null else glyph
                val extent = ceil(size * scale).toInt() + 2
                val image = BufferedImage(extent, extent, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    graphics.scale(scale, scale)
                    button.paint(graphics)
                } finally {
                    graphics.dispose()
                }
                val pixels = (0 until extent).flatMap { y ->
                    (0 until extent).filter { x -> (image.getRGB(x, y) ushr 24) >= 128 }.map { x -> x to y }
                }
                assertTrue(pixels.isNotEmpty())
                return (pixels.minOf { it.first } + pixels.maxOf { it.first }) / 2.0 to
                    (pixels.minOf { it.second } + pixels.maxOf { it.second }) / 2.0
            }
            for (size in listOf(28, 29, 35)) {
                for (scale in listOf(1.0, 1.25, 1.5, 2.0)) {
                    val frame = paintedCenter(size, scale, true)
                    val icon = paintedCenter(size, scale, false)
                    assertEquals(frame.first, icon.first, 0.5, "horizontal center: size=$size scale=$scale")
                    assertEquals(frame.second, icon.second, 0.5, "vertical center: size=$size scale=$scale")
                }
            }
        }
    }


    @Test
    fun `new turn or session rejects delayed counters from previous generation`() {
        val state = ContextUsageState()
        val old = state.begin()
        assertTrue(state.accept(old, usage))
        val current = state.begin()
        assertNull(state.usage)
        assertFalse(state.accept(old, usage))
        assertTrue(state.accept(current, usage.copy(inputTokens = 0)))
        assertEquals(0L, state.usage?.inputTokens)
        assertTrue(state.accept(current, null))
        assertNull(state.usage)
    }

    @Test
    fun `embedded view toggles and closes without resetting counters`() {
        SwingUtilities.invokeAndWait {
            val view = ContextUsageView()
            assertFalse(view.panel.isVisible)
            view.button.doClick()
            assertTrue(view.panel.isVisible)
            val ticket = view.beginTurn()
            view.update(ticket, usage)
            fun descendants(c: Container): List<java.awt.Component> = c.components.flatMap {
                listOf(it) + if (it is Container) descendants(it) else emptyList()
            }
            assertTrue(descendants(view.panel).filterIsInstance<JLabel>().any { it.text == "21,022" })
            view.button.doClick()
            assertFalse(view.panel.isVisible)
            view.button.doClick()
            assertTrue(descendants(view.panel).filterIsInstance<JLabel>().any { it.text == "21,022" })
            descendants(view.panel).filterIsInstance<JButton>().single().doClick()
            assertFalse(view.panel.isVisible)
            view.reset()
            view.update(ticket, usage)
            assertFalse(descendants(view.panel).filterIsInstance<JLabel>().any { it.text == "21,022" })
        }
    }

    @Test
    fun `missing counters are hidden while reported zero remains visible and reset clears old rows`() {
        SwingUtilities.invokeAndWait {
            val view = ContextUsageView()
            view.button.doClick()
            fun visibleTexts(c: Container): List<String> = c.components.filter { it.isVisible }.flatMap {
                (if (it is JLabel) listOf(it.text) else emptyList()) +
                    (if (it is Container) visibleTexts(it) else emptyList())
            }
            assertTrue(visibleTexts(view.panel).contains("まだ応答を開始していません"))
            val ticket = view.beginTurn()
            view.update(ticket, usage)
            assertTrue(visibleTexts(view.panel).contains("21,022"))
            view.update(ticket, TokenUsage(null, 0, null, null))
            val partial = visibleTexts(view.panel)
            assertTrue(partial.containsAll(listOf("出力", "0")))
            assertFalse(partial.any { it in listOf("入力", "キャッシュ読み取り", "キャッシュ書き込み", "21,022", "取得不可", "使用率・残量", "種別別内訳") })
            view.reset()
            val reset = visibleTexts(view.panel)
            assertTrue(reset.contains("まだ応答を開始していません"))
            assertFalse(reset.contains("出力"))
            assertFalse(reset.contains("0"))
        }
    }

}
