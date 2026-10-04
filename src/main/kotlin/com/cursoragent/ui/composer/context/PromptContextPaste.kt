package com.cursoragent.ui.composer.context

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import java.awt.datatransfer.Transferable

internal class PromptContextPaste(val available: () -> Boolean, val add: (ClipboardContextData) -> Unit)
internal val PROMPT_CONTEXT_PASTE = Key.create<PromptContextPaste>("cursor.agent.prompt.context.paste")

internal fun pasteClipboardContext(editor: Editor, value: Transferable): Boolean {
    val target = editor.getUserData(PROMPT_CONTEXT_PASTE) ?: return false
    val project = editor.project ?: return false
    if (editor.isDisposed || editor.isViewer || !target.available()) return false
    val context = clipboardContext(value, project) ?: return false
    target.add(context)
    return true
}

/** Use the platform's plain-text paste action so caret, selection replacement and Undo stay native. */
class PromptPlainPasteAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    private fun available(event: AnActionEvent): Boolean {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return false
        return !editor.isDisposed && !editor.isViewer && event.project != null && event.project === editor.project &&
            event.project?.isDisposed == false && editor.getUserData(PROMPT_CONTEXT_PASTE)?.available?.invoke() == true
    }

    override fun update(event: AnActionEvent) { event.presentation.isEnabled = available(event) }

    override fun actionPerformed(event: AnActionEvent) {
        if (available(event)) ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_PASTE_SIMPLE)?.let {
            ActionUtil.performAction(it, event)
        }
    }
}
