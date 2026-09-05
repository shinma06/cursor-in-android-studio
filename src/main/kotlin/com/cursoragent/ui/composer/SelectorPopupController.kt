package com.cursoragent.ui.composer

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
internal class SelectorPopupController(private val button: JComponent) {
    private var popup: JBPopup? = null

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
            if (owner != null && owner !== button && !SwingUtilities.isDescendingFrom(owner, button) &&
                !SwingUtilities.isDescendingFrom(owner, next.content) && owner !== next.content &&
                next.isVisible && !next.isDisposed
            ) {
                next.cancel()
            }
        }
        focusManager.addPropertyChangeListener("focusOwner", focusListener)
        next.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                focusManager.removePropertyChangeListener("focusOwner", focusListener)
                if (popup === next) popup = null
            }
        })
        try {
            next.show(PopupShowOptions.aboveComponent(button).withPopupComponentUnscaledGap(4))
        } catch (failure: Throwable) {
            focusManager.removePropertyChangeListener("focusOwner", focusListener)
            if (popup === next) popup = null
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
    }
}

/** Keep the bottom edge above the trigger as search/Auto changes the popup's preferred height. */
internal fun popupBoundsAbove(anchor: Rectangle, size: Dimension, screen: Rectangle, gap: Int): Rectangle {
    val width = size.width.coerceIn(1, screen.width.coerceAtLeast(1))
    val bottom = (anchor.y - gap).coerceAtLeast(screen.y + 1)
    val height = size.height.coerceIn(1, (bottom - screen.y).coerceAtLeast(1))
    return Rectangle(anchor.x.coerceIn(screen.x, screen.x + screen.width - width), bottom - height, width, height)
}
