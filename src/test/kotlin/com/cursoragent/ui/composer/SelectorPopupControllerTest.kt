package com.cursoragent.ui.composer

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.beans.PropertyChangeEvent
import java.lang.reflect.Proxy
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

class SelectorPopupControllerTest {
    @Test
    fun `resizing for Auto keeps popup bottom above visible trigger and fits screen edge`() {
        val screen = Rectangle(-1200, 0, 1200, 900)
        val anchor = Rectangle(-120, 830, 95, 24)
        for (height in listOf(300, 100, 450)) {
            val bounds = popupBoundsAbove(anchor, Dimension(320, height), screen, 4)
            assertEquals(anchor.y - 4, bounds.y + bounds.height)
            assertEquals(height, bounds.height)
            assertTrue(screen.contains(bounds))
            assertFalse(bounds.intersects(anchor))
        }
    }

    @Test
    fun `same trigger closes without recreation and outside focus removes listener`() = SwingUtilities.invokeAndWait {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val original = manager.getPropertyChangeListeners("focusOwner").toSet()
        val controller = SelectorPopupController(JButton())
        var created = 0
        fun create(): JBPopup { created++; return fakePopup() }
        controller.toggle(::create)
        assertEquals(original.size + 1, manager.getPropertyChangeListeners("focusOwner").size)
        controller.toggle(::create)
        assertEquals(1, created)
        assertEquals(original, manager.getPropertyChangeListeners("focusOwner").toSet())
        controller.toggle(::create)
        val listener = manager.getPropertyChangeListeners("focusOwner").first { it !in original }
        listener.propertyChange(PropertyChangeEvent(manager, "focusOwner", null, JButton()))
        assertEquals(original, manager.getPropertyChangeListeners("focusOwner").toSet())
        controller.toggle(::create)
        assertEquals(3, created)
        controller.toggle(::create)
        assertEquals(original, manager.getPropertyChangeListeners("focusOwner").toSet())
    }

    private fun fakePopup(): JBPopup {
        var visible = false
        var disposed = false
        val content = JPanel()
        val listeners = mutableListOf<JBPopupListener>()
        return Proxy.newProxyInstance(JBPopup::class.java.classLoader, arrayOf(JBPopup::class.java)) { proxy, method, args ->
            when (method.name) {
                "getContent" -> content
                "isVisible" -> visible
                "isDisposed" -> disposed
                "addListener" -> { listeners.add(args!![0] as JBPopupListener); null }
                "show" -> { visible = true; null }
                "cancel" -> {
                    visible = false
                    disposed = true
                    listeners.forEach { it.onClosed(LightweightWindowEvent(proxy as JBPopup, false)) }
                    null
                }
                "toString" -> "TestPopup"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                else -> null
            }
        } as JBPopup
    }
}
