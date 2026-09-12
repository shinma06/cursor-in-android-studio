package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.context.TokenCountsButton
import com.intellij.ui.JBColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.ceil

class NaturalFocusPaintTest {
    private fun withFocus(test: ((Component?) -> Unit) -> Unit) = SwingUtilities.invokeAndWait {
        val previousManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val previousDark = !JBColor.isBright()
        var owner: Component? = null
        KeyboardFocusManager.setCurrentKeyboardFocusManager(object : DefaultKeyboardFocusManager() {
            override fun getFocusOwner(): Component? = owner
        })
        try {
            test { owner = it }
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(previousManager)
            JBColor.setDark(previousDark)
        }
    }

    private fun pixels(button: AbstractButton, scale: Double = 1.0): IntArray {
        val image = BufferedImage(ceil(button.width * scale).toInt(), ceil(button.height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.scale(scale, scale)
            button.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }

    @Test
    fun `focus shares hover paint without an extra frame and keeps toggle states distinct`() = withFocus { focus ->
        for (dark in listOf(false, true)) {
            JBColor.setDark(dark)
            for (height in listOf(28, 32)) for (scale in listOf(1.0, 1.25, 1.5, 2.0)) {
                val selector = SelectorButton().apply { setSize(96, height) }
                val counts = TokenCountsButton().apply { setSize(height, height) }
                val toggle = AutoToggle().apply { size = preferredSize }
                for (button in listOf(selector, counts, toggle)) {
                    val preferred = button.preferredSize
                    focus(null)
                    button.model.isRollover = false
                    val normal = pixels(button, scale)
                    focus(button)
                    assertTrue(button.hasFocus())
                    val focused = pixels(button, scale)
                    focus(null)
                    button.model.isRollover = true
                    val hovered = pixels(button, scale)
                    assertArrayEquals(hovered, focused, "${button.javaClass.simpleName}: dark=$dark scale=$scale")
                    assertFalse(normal.contentEquals(focused), "Keyboard target must remain visible")
                    assertEquals(preferred, button.preferredSize)
                }
                toggle.model.isRollover = false
                focus(toggle)
                val off = pixels(toggle, scale)
                toggle.isSelected = true
                val on = pixels(toggle, scale)
                assertFalse(off.contentEquals(on))
                focus(null)
                toggle.model.isRollover = true
                assertArrayEquals(on, pixels(toggle, scale))
            }
        }
    }

    @Test
    fun `mode pills retain selection identity while keyboard focus becomes visible`() = withFocus { focus ->
        for (dark in listOf(false, true)) {
            JBColor.setDark(dark)
            val settings = AgentSettingsState()
            val mode = ModeSelector(settings)
            for (value in AgentMode.entries) {
                mode.selectMode(value)
                mode.size = mode.preferredSize
                val label = mode.text
                val icon = mode.icon
                val accessibleName = mode.accessibleContext.accessibleName
                val preferred = mode.preferredSize
                focus(null)
                mode.model.isRollover = false
                val normal = pixels(mode)
                focus(mode)
                val focused = pixels(mode)
                focus(null)
                mode.model.isRollover = true
                assertArrayEquals(focused, pixels(mode))
                assertFalse(normal.contentEquals(focused), "Fixed-color pill must identify focus: $value")
                assertEquals(value, settings.mode)
                assertEquals(label, mode.text)
                assertSame(icon, mode.icon)
                assertEquals(accessibleName, mode.accessibleContext.accessibleName)
                assertEquals(preferred, mode.preferredSize)
            }
        }
    }

    @Test
    fun `native space actions remain enabled and disabled controls cannot activate`() = withFocus { focus ->
        for (button in listOf(SelectorButton(), AutoToggle(), TokenCountsButton())) {
            var actions = 0
            button.addActionListener { actions++ }
            focus(button)
            assertTrue(button.isFocusable)
            for (key in listOf("pressed SPACE", "released SPACE")) {
                val binding = button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key))
                val action = button.actionMap.get(binding)
                assertNotNull(action)
                action.actionPerformed(ActionEvent(button, ActionEvent.ACTION_PERFORMED, key))
            }
            assertEquals(1, actions)
            button.isEnabled = false
            button.doClick(0)
            assertEquals(1, actions)
        }
    }
}
