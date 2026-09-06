package com.cursoragent.ui.composer.context

import com.cursoragent.parser.TokenUsage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities

class ContextUsageTest {
    private val usage = TokenUsage(21022, 31, 8896, 0)

    @Test
    fun `new turn or session rejects delayed counters from previous generation`() {
        val state = ContextUsageState()
        val old = state.clear()
        assertTrue(state.accept(old, usage))
        val current = state.clear()
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
}
