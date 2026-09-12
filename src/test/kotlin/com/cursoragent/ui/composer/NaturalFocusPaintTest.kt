package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentMode
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.composer.context.TokenCountsButton
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.ceil

class NaturalFocusPaintTest {
    private fun withFocus(test: ((Component?) -> Unit) -> Unit) = SwingUtilities.invokeAndWait {
        val previousManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val previousDark = !JBColor.isBright()
        val previousPressedBackground = UIManager.get("ActionButton.pressedBackground")
        var owner: Component? = null
        KeyboardFocusManager.setCurrentKeyboardFocusManager(object : DefaultKeyboardFocusManager() {
            override fun getFocusOwner(): Component? = owner
        })
        try {
            test { owner = it }
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(previousManager)
            JBColor.setDark(previousDark)
            if (previousPressedBackground == null) {
                UIManager.getDefaults().remove("ActionButton.pressedBackground")
            } else {
                UIManager.put("ActionButton.pressedBackground", previousPressedBackground)
            }
        }
    }

    private fun theme(dark: Boolean) {
        JBColor.setDark(dark)
        // Target SDK expUI themes: pressedBackground is #FFFFFF26 / #0000001D.
        UIManager.put("ActionButton.pressedBackground", if (dark) Color(255, 255, 255, 0x26) else Color(0, 0, 0, 0x1D))
    }

    private fun pixels(button: AbstractButton, scale: Double = 1.0, background: Color? = null): IntArray {
        val image = BufferedImage(ceil(button.width * scale).toInt(), ceil(button.height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            if (background != null) {
                graphics.color = background
                graphics.fillRect(0, 0, image.width, image.height)
            }
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
            theme(dark)
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
            theme(dark)
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
    fun `plain controls remain distinguishable when focus is composited onto composer background`() = withFocus { focus ->
        for (dark in listOf(false, true)) {
            theme(dark)
            for (button in listOf(SelectorButton(), TokenCountsButton())) {
                button.setSize(28, 28)
                focus(null)
                val normal = pixels(button, background = AgentUiColors.composerBackground)
                focus(button)
                val focused = pixels(button, background = AgentUiColors.composerBackground)
                val center = button.width * (button.height / 2) + button.width / 2
                val contrast = ColorUtil.calculateContrastRatio(Color(normal[center]), Color(focused[center]))
                // Regression floor for the existing IDE fill, not an accessibility conformance claim.
                assertTrue(contrast >= 1.25, "Focus disappears into parent: dark=$dark contrast=$contrast")
                focus(null)
                button.model.isRollover = true
                assertArrayEquals(focused, pixels(button, background = AgentUiColors.composerBackground))
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
