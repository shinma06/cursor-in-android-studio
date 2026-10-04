package com.cursoragent.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** A temporary dispatcher sees modifier releases from child inputs, and acts only inside this root. */
internal class SidebarChatNavigation(
    private val owner: JComponent,
    private val valid: () -> Boolean,
    private val onFinish: (Boolean) -> Unit,
) : Disposable, KeyEventDispatcher, WindowFocusListener {
    private val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val window = SwingUtilities.getWindowAncestor(owner)
    private var shortcuts = emptyList<KeyboardShortcut>()
    private var disposed = false
    private val focusListener = PropertyChangeListener {
        if (!ownsFocus()) finish(false)
    }

    init {
        manager.addKeyEventDispatcher(this)
        manager.addPropertyChangeListener("focusOwner", focusListener)
        window?.addWindowFocusListener(this)
    }

    fun useShortcuts(values: Array<out Shortcut>) { shortcuts = values.filterIsInstance<KeyboardShortcut>() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (disposed) return false
        if (!valid() || !ownsFocus()) { finish(false); return false }
        if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) { finish(false); return true }
        if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ENTER) { finish(true); return true }
        if (acceptsSidebarChatRelease(event, shortcuts)) finish(true)
        return false
    }

    private fun ownsFocus(): Boolean = manager.focusOwner?.let { SwingUtilities.isDescendingFrom(it, owner) } == true

    private fun finish(accept: Boolean) {
        if (disposed) return
        disposed = true
        manager.removeKeyEventDispatcher(this)
        manager.removePropertyChangeListener("focusOwner", focusListener)
        window?.removeWindowFocusListener(this)
        onFinish(accept)
    }

    override fun windowGainedFocus(event: WindowEvent) = Unit
    override fun windowLostFocus(event: WindowEvent) = finish(false)
    override fun dispose() = finish(false)
}

/** Default Ctrl/Meta+Alt navigation commits after Ctrl/Meta are up, not when Alt alone is released. */
internal fun acceptsSidebarChatRelease(event: KeyEvent, shortcuts: List<KeyboardShortcut>): Boolean {
    if (event.id != KeyEvent.KEY_RELEASED) return false
    val primary = InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK
    return shortcuts.any { shortcut ->
        if (shortcut.secondKeyStroke != null) false
        else if (shortcut.firstKeyStroke.modifiers and primary != 0) event.modifiersEx and primary == 0
        else acceptsRecentChatRelease(event, listOf(shortcut))
    }
}

internal fun <T> adjacentSidebarChat(ids: List<T>, current: T?, highlighted: T?, reverse: Boolean): T? {
    if (ids.isEmpty()) return null
    val index = ids.indexOf(highlighted ?: current)
    return ids[if (index < 0) { if (reverse) ids.lastIndex else 0 } else (index + if (reverse) -1 else 1).coerceIn(0, ids.lastIndex)]
}
