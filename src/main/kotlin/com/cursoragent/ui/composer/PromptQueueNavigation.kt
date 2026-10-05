package com.cursoragent.ui.composer

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Key

private val QUEUE_NAVIGATION = Key.create<(Boolean) -> Boolean>("cursor.agent.prompt.queue.navigation")

internal fun installPromptQueueNavigation(editor: Editor, navigate: (Boolean) -> Boolean) {
    editor.putUserData(QUEUE_NAVIGATION, navigate)
}

/** Wrap the native vertical actions so user Keymap remapping and normal caret movement stay native. */
abstract class PromptQueueNavigation(
    private val original: EditorActionHandler,
    private val reverse: Boolean,
) : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (editor.isDisposed) return
        val navigate = editor.getUserData(QUEUE_NAVIGATION)
        if (navigate == null) {
            original.execute(editor, caret, dataContext)
            return
        }
        val current = caret ?: editor.caretModel.currentCaret
        val boundary = if (reverse) 0 else editor.document.textLength
        if (!editor.isViewer && editor.contentComponent.isFocusOwner && editor.document.charsSequence.isBlank() &&
            editor.caretModel.caretCount == 1 && !current.hasSelection() && current.offset == boundary &&
            navigate(reverse)) return
        original.execute(editor, caret, dataContext)
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext) =
        original.isEnabled(editor, caret, dataContext)

    override fun executeInCommand(editor: Editor, dataContext: DataContext) = original.executeInCommand(editor, dataContext)

    class Up(original: EditorActionHandler) : PromptQueueNavigation(original, true)
    class Down(original: EditorActionHandler) : PromptQueueNavigation(original, false)
}
