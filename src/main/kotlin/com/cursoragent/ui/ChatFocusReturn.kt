package com.cursoragent.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

internal enum class ChatFocusOrigin { EDITOR, TERMINAL }

/** Read before chat content creation or activation can move focus. Other controls keep the last origin. */
internal fun chatFocusOrigin(project: Project): ChatFocusOrigin? {
    if (project.isDisposed) return null
    val manager = ToolWindowManager.getInstance(project)
    val terminal = manager.getToolWindow("Terminal")?.takeUnless { it.isDisposed || it.project !== project }
        ?.contentManagerIfCreated?.selectedContent?.component
    return chatFocusOrigin(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner, terminal, manager.isEditorComponentActive)
}

internal fun chatFocusOrigin(focus: Component?, terminal: Component?, editorActive: Boolean): ChatFocusOrigin? = when {
    focus != null && terminal != null && SwingUtilities.isDescendingFrom(focus, terminal) -> ChatFocusOrigin.TERMINAL
    focus != null && editorActive -> ChatFocusOrigin.EDITOR
    else -> null
}

/** Keep only origin, not editor/terminal components. Resolve the live target at the moment of Escape. */
internal class ChatFocusReturn(
    private val project: Project,
    private val terminal: () -> ToolWindow? = { ToolWindowManager.getInstance(project).getToolWindow("Terminal") },
    private val activateEditor: () -> Unit = { ToolWindowManager.getInstance(project).activateEditorComponent() },
) {
    private var origin = ChatFocusOrigin.EDITOR

    fun remember(value: ChatFocusOrigin?) { if (value != null) origin = value }

    fun restore() {
        if (project.isDisposed) return
        val previous = origin
        origin = ChatFocusOrigin.EDITOR
        val target = if (previous == ChatFocusOrigin.TERMINAL) terminal()?.takeUnless {
            it.isDisposed || !it.isAvailable || it.project !== project ||
                it.contentManagerIfCreated?.contentCount?.let { count -> count > 0 } != true
        } else null
        // Do not initialize an empty Terminal or start a replacement process just to return focus.
        if (target != null) target.activate(null, false) else activateEditor()
    }
}
