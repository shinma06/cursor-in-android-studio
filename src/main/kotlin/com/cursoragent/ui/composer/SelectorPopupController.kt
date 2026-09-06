package com.cursoragent.ui.composer

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupShowOptions
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.ScreenUtil
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Own one selector popup, including the focus listener only while it is open. All calls are on EDT. */
internal class SelectorPopupController(private val button: JComponent, private val ownsChildPopups: Boolean = false) {
    private var popup: JBPopup? = null
    private var child: JBPopup? = null

    private fun contains(component: Component, root: JComponent): Boolean =
        component === root || SwingUtilities.isDescendingFrom(component, root)


    init {
        button.addHierarchyListener {
            if (!button.isShowing) popup?.cancel()
        }
    }

    fun toggle(createPopup: () -> JBPopup) {
        popup?.takeUnless { it.isDisposed }?.let {
            it.cancel()
            return
        }
        val next = createPopup()
        popup = next
        // The platform suppresses the outside-click/reopen race for the same trigger.
        PopupUtil.setPopupToggleComponent(next, button)
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val focusListener = PropertyChangeListener { event ->
            val owner = event.newValue as? Component
            if (next.isVisible && !next.isDisposed && owner != null && owner !== button && !SwingUtilities.isDescendingFrom(owner, button) &&
                !contains(owner, next.content) && child?.let { contains(owner, it.content) } != true
            ) {
                next.cancel()
            }
        }
        focusManager.addPropertyChangeListener("focusOwner", focusListener)
        // Model cards share outside-click ownership; clicks inside either card must keep both alive.
        val mouseListener = java.awt.event.AWTEventListener { event ->
            if (event is java.awt.event.MouseEvent && event.id == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                val source = event.component
                if (!contains(source, button) && !contains(source, next.content) &&
                    child?.let { contains(source, it.content) } != true
                ) next.cancel()
            }
        }
        if (ownsChildPopups) java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
        val cleanup = {
            closeChild()
            if (ownsChildPopups) java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener)
            focusManager.removePropertyChangeListener("focusOwner", focusListener)
            if (popup === next) popup = null
        }
        Disposer.register(next, Disposable { cleanup() })
        next.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                cleanup()
            }
        })
        try {
            next.show(PopupShowOptions.aboveComponent(button).withPopupComponentUnscaledGap(4))
        } catch (failure: Throwable) {
            cleanup()
            next.cancel()
            throw failure
        }
    }

    fun repackAbove() {
        val active = popup?.takeIf { it.isVisible && !it.isDisposed } ?: return
        if (!button.isShowing) return
        active.pack(true, true)
        val anchor = Rectangle(button.locationOnScreen, button.size)
        val bounds = popupBoundsAbove(anchor, active.size, ScreenUtil.getScreenRectangle(button), JBUI.scale(4))
        active.setSize(bounds.size)
        active.setLocation(bounds.location)
        repackChild()
    }

    fun showChild(content: JComponent, focus: JComponent, requestFocus: Boolean, onClosed: () -> Unit) {
        val parent = popup?.takeIf { it.isVisible && !it.isDisposed } ?: return
        closeChild()
        val next = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().createComponentPopupBuilder(content, focus)
            .setFocusable(true)
            .setRequestFocus(requestFocus)
            .setCancelOnClickOutside(false)
            .setCancelOnWindowDeactivation(false)
            .setCancelOnOtherWindowOpen(false)
            .setCancelKeyEnabled(false)
            .createPopup()
        child = next
        next.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (child === next) {
                    child = null
                    onClosed()
                }
            }
        })
        try {
            val parentBounds = Rectangle(parent.content.locationOnScreen, parent.content.size)
            val bounds = popupBoundsBeside(parentBounds, content.preferredSize, ScreenUtil.getScreenRectangle(button), JBUI.scale(4))
            next.show(com.intellij.ui.awt.RelativePoint(bounds.location))
            next.setSize(bounds.size)
            next.setLocation(bounds.location)
        } catch (failure: Throwable) {
            closeChild()
            onClosed()
            throw failure
        }
    }

    fun closeChild() {
        val previous = child
        child = null
        previous?.cancel()
    }

    fun repackChild() {
        val parent = popup?.takeIf { it.isVisible && !it.isDisposed } ?: return
        val active = child?.takeIf { it.isVisible && !it.isDisposed } ?: return
        active.pack(true, true)
        val bounds = popupBoundsBeside(Rectangle(parent.content.locationOnScreen, parent.content.size),
            active.size, ScreenUtil.getScreenRectangle(button), JBUI.scale(4))
        active.setSize(bounds.size)
        active.setLocation(bounds.location)
    }
}

/** Keep the bottom edge above the trigger as search/Auto changes the popup's preferred height. */
internal fun popupBoundsAbove(anchor: Rectangle, size: Dimension, screen: Rectangle, gap: Int): Rectangle {
    val width = size.width.coerceIn(1, screen.width.coerceAtLeast(1))
    val bottom = (anchor.y - gap).coerceAtLeast(screen.y + 1)
    val height = size.height.coerceIn(1, (bottom - screen.y).coerceAtLeast(1))
    return Rectangle(anchor.x.coerceIn(screen.x, screen.x + screen.width - width), bottom - height, width, height)
}

/** Prefer the left cascade, fall back to the right, then fit within a small screen. */
internal fun popupBoundsBeside(parent: Rectangle, size: Dimension, screen: Rectangle, gap: Int): Rectangle {
    val width = size.width.coerceIn(1, screen.width.coerceAtLeast(1))
    val height = size.height.coerceIn(1, screen.height.coerceAtLeast(1))
    val left = parent.x - gap - width
    val right = parent.x + parent.width + gap
    val x = when {
        left >= screen.x -> left
        right + width <= screen.x + screen.width -> right
        else -> left.coerceIn(screen.x, screen.x + screen.width - width)
    }
    val y = (parent.y + parent.height - height).coerceIn(screen.y, screen.y + screen.height - height)
    return Rectangle(x, y, width, height)
}
