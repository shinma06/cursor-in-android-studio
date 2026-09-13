package com.cursoragent.ui.composer.image

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/** Native EditorPaste wrapper: only our marked prompt editor consumes an image; all text delegates. */
class PromptImagePasteHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        val handler = editor.getUserData(IMAGE_INPUT)
        val content = if (handler != null) CopyPasteManager.getInstance().contents else null
        if (content != null && ImageTransfer.accepts(content.transferDataFlavors) && handler!!.invoke(content)) return
        original.execute(editor, caret, dataContext)
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean =
        original.isEnabled(editor, caret, dataContext) || (!editor.isViewer && editor.getUserData(IMAGE_INPUT) != null &&
            CopyPasteManager.getInstance().contents?.let { ImageTransfer.accepts(it.transferDataFlavors) } == true)

    override fun executeInCommand(editor: Editor, dataContext: DataContext) = original.executeInCommand(editor, dataContext)
}

private val IMAGE_INPUT = Key.create<(Transferable) -> Boolean>("cursor.agent.prompt.image.input")

/** Install only on the fresh embedded prompt editor, which has no custom drop handler to replace. */
internal fun installPromptImageInput(editor: Editor, handle: (Transferable) -> Boolean) {
    editor.putUserData(IMAGE_INPUT, handle)
    (editor as? EditorImpl)?.let { prompt ->
        prompt.settings.isDndEnabled = true
        prompt.setDropHandler(object : EditorDropHandler {
            override fun canHandleDrop(flavors: Array<DataFlavor>) = ImageTransfer.accepts(flavors)
            override fun handleDrop(value: Transferable, project: Project?, editorWindow: EditorWindow?) {
                handle(value)
            }
        })
    }
}
