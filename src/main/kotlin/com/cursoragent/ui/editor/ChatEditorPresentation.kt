package com.cursoragent.ui.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** One live view, independent of the number of native editor splits displaying its handle. EDT only. */
internal class ChatEditorPresentation(
    val project: Project,
    content: JComponent,
    private val preferredFocus: JComponent,
    private val canMove: () -> Boolean,
    private val selectOwner: () -> Unit,
    private val onReturn: (Boolean) -> Unit,
    private val onFailure: (Throwable?) -> Unit,
    private val shortcutAllowed: () -> Boolean = { true },
    private val headerActions: () -> List<com.intellij.openapi.actionSystem.AnAction> = { emptyList() },
) : Disposable {
    @Volatile private var disposed = false
    private var returning = false
    private val editors = linkedSetOf<ChatFileEditor>()
    private val file = ChatVirtualFile(this)
    val alive: Boolean get() = !disposed && !project.isDisposed
    val inEditor: Boolean get() = editors.isNotEmpty()
    val available: Boolean get() = alive && canMove()
    val shortcutAvailable: Boolean get() = available && shortcutAllowed()
    private val shortcut = ActionManager.getInstance().getAction(ChatEditorToggleAction.ID)
    private val view = object : JPanel(BorderLayout()), UiDataProvider {
        override fun uiDataSnapshot(sink: DataSink) { if (alive) sink[KEY] = this@ChatEditorPresentation }
    }.apply {
        isOpaque = false
        add(content)
    }
    val panel = JPanel(BorderLayout()).apply { isOpaque = false; add(view) }

    // Returning focus from the tool window to the already selected native editor has no selectNotify.
    private val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val focusListener = PropertyChangeListener { event ->
        val target = event.newValue as? Component
        if (alive && !returning && target != null && editors.any { SwingUtilities.isDescendingFrom(target, it.component) }) selectOwner()
    }

    init {
        shortcut?.registerCustomShortcutSet(shortcut.shortcutSet, view)
        focusManager.addPropertyChangeListener("focusOwner", focusListener)
    }

    fun focusInEditor() { if (available) open() }

    fun toggle() {
        if (!available) return
        if (inEditor) returnToPanel(true) else open()
    }

    fun focus() {
        if (!alive) return
        if (inEditor) open() else preferredFocus.requestFocusInWindow()
    }

    fun updateTitle(title: String) {
        if (!alive || file.name == title) return
        file.rename(this, title)
        if (inEditor) FileEditorManager.getInstance(project).updateFilePresentation(file)
    }

    private fun open() {
        if (!alive) return
        ApplicationManager.getApplication().assertIsDispatchThread()
        try {
            FileEditorManager.getInstance(project).openFile(file, true)
            if (!inEditor) { returnToPanel(false); onFailure(null) }
        } catch (failure: com.intellij.openapi.progress.ProcessCanceledException) {
            returnToPanel(false)
            throw failure
        } catch (failure: Exception) {
            returnToPanel(false)
            onFailure(failure)
        }
    }

    fun returnToPanel(focus: Boolean) {
        if (!alive || returning) return
        returning = true
        try {
            FileEditorManager.getInstance(project).closeFile(file)
        } finally {
            restorePanel()
            returning = false
        }
        if (focus) selectOwner()
        onReturn(focus)
    }

    private fun restorePanel() {
        if (!alive || view.parent === panel) return
        panel.removeAll()
        panel.add(view)
        panel.revalidate()
        panel.repaint()
    }

    private fun showEditor(editor: ChatFileEditor, select: Boolean = true) {
        if (!alive || editor !in editors || returning) return
        if (select) selectOwner()
        if (view.parent === editor.body) return
        // A Swing view cannot belong to two split editors; the inactive split offers a focus link.
        editors.forEach { it.showPlaceholder() }
        panel.removeAll()
        panel.add(JButton("エディターで表示").apply { addActionListener { open() } })
        editor.body.removeAll()
        editor.body.add(view)
        editor.body.revalidate()
        editor.body.repaint()
        panel.revalidate()
        panel.repaint()
    }

    fun createEditor(): FileEditor = ChatFileEditor().also {
        check(alive)
        editors.add(it)
        if (editors.size == 1) showEditor(it, select = false) else it.showPlaceholder()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        shortcut?.unregisterCustomShortcutSet(view)
        focusManager.removePropertyChangeListener("focusOwner", focusListener)
        try {
            if (!project.isDisposed) FileEditorManager.getInstance(project).closeFile(file)
        } finally {
            editors.clear()
            panel.removeAll()
            view.removeAll()
            file.presentation = null
            file.setValid(false)
        }
    }

    private inner class ChatFileEditor : UserDataHolderBase(), FileEditor {
        @Volatile private var closed = false
        val body = JPanel(BorderLayout()).apply { isOpaque = false }
        private val component = object : JPanel(BorderLayout()), UiDataProvider {
            override fun uiDataSnapshot(sink: DataSink) { if (alive && !closed) sink[KEY] = this@ChatEditorPresentation }
        }.apply {
            isOpaque = false
            val back = JButton("Agentパネルに戻す").apply {
                toolTipText = "会話・下書き・実行を保持したままパネルへ戻します。"
                addActionListener { if (available) returnToPanel(true) }
            }
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(4)
                val actions = headerActions()
                if (actions.isNotEmpty()) {
                    val toolbar = ActionManager.getInstance().createActionToolbar(
                        "CursorAgent.ChatEditor", com.intellij.openapi.actionSystem.DefaultActionGroup(actions), true,
                    )
                    toolbar.targetComponent = this
                    add(toolbar.component, BorderLayout.WEST)
                }
                add(back, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(body)
        }
        fun showPlaceholder() {
            body.removeAll()
            body.add(JButton("この場所に会話を表示").apply {
                addActionListener { showEditor(this@ChatFileEditor); preferredFocus.requestFocusInWindow() }
            })
            body.revalidate()
            body.repaint()
        }
        override fun getComponent(): JComponent = component
        override fun getPreferredFocusedComponent() = preferredFocus
        override fun getName() = "Agent"
        override fun getFile(): VirtualFile = this@ChatEditorPresentation.file
        override fun setState(state: FileEditorState) = Unit
        override fun isModified() = false
        override fun isValid() = alive && !closed
        override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
        override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
        override fun selectNotify() = showEditor(this)
        override fun dispose() {
            if (closed) return
            closed = true
            val ownedView = view.parent === body
            editors.remove(this)
            body.removeAll()
            if (!alive || returning) return
            if (editors.isEmpty()) { restorePanel(); onReturn(false) }
            else if (ownedView) showEditor(editors.last(), select = false)
        }
    }

    companion object {
        val KEY = DataKey.create<ChatEditorPresentation>("CursorAgent.ChatEditorPresentation")
    }
}

/** In-memory identity belongs to this project's live tab; it is never a document or provider session. */
private class ChatVirtualFile(@Volatile var presentation: ChatEditorPresentation?) : LightVirtualFile("New Agent")

class ChatFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) =
        file is ChatVirtualFile && file.presentation?.let { it.project === project && it.alive } == true
    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        check(accept(project, file))
        return requireNotNull((file as ChatVirtualFile).presentation).createEditor()
    }
    override fun getEditorTypeId() = "CursorAgent.ChatEditor"
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
