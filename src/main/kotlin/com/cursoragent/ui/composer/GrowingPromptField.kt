package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.SwingUtilities

/** Grow by actual editor visual lines (including soft wraps), then let the editor scroll. */
class GrowingPromptField(project: Project) : EditorTextField(project, PlainTextFileType.INSTANCE) {
    internal var onImageTransfer: ((java.awt.datatransfer.Transferable) -> Boolean)? = null
    private var resizePending = false
    private val ime = PromptImeGuard()
    val isComposing: Boolean get() = ime.isComposing

    init {
        // Keep the PSI-backed document, but create it before composer listeners are registered.
        // EditorTextField does not attach earlier listeners when its lazy getter creates it.
        document
        setOneLineMode(false)
        font = AgentUiMetrics.textFont()
        setPlaceholder("Plan, Build, / for skills, @ for context")
        setShowPlaceholderWhenFocused(true)
        border = JBUI.Borders.empty(8, 8, 3, 8)
        isOpaque = false
        preferredSize = JBUI.size(100, 48)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = scheduleResize()
        })
    }

    override fun createEditor(): EditorEx = super.createEditor().also { editor ->
        // A prompt is an embedded form field, even though it supports multiple lines.
        // Use prompt-local shortcuts as well: selection indentation ignores this flag.
        editor.isEmbeddedIntoDialogWrapper = true
        installPromptFocusTraversal(editor.contentComponent)
        com.cursoragent.ui.composer.image.installPromptImageInput(editor) { value ->
            if (!isEnabled || isComposing) true else onImageTransfer?.invoke(value) ?: false
        }
        ime.reset()
        editor.contentComponent.addInputMethodListener(ime)
        editor.setBackgroundColor(AgentUiColors.composerBackground)
        editor.settings.apply {
            isUseSoftWraps = true
            isRightMarginShown = false
            isFoldingOutlineShown = false
            isCaretRowShown = false
            isLineNumbersShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
        }
        editor.scrollPane.border = JBUI.Borders.empty()
        installPromptScrollBarUI(editor.scrollPane.verticalScrollBar)
        editor.contentComponent.border = JBUI.Borders.empty()
        editor.setVerticalScrollbarVisible(false)
        editor.setHorizontalScrollbarVisible(false)
        // This listener belongs to this editor's soft-wrap model, not a global service.
        editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
            override fun softWrapsChanged() = scheduleResize()
            override fun recalculationEnds() = scheduleResize()
        })
    }

    override fun onEditorAdded(editor: Editor) {
        super.onEditorAdded(editor)
        scheduleResize()
    }

    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)
        scheduleResize()
    }

    private fun scheduleResize() {
        if (resizePending) return
        resizePending = true
        SwingUtilities.invokeLater {
            resizePending = false
            val activeEditor = editor as? EditorEx ?: return@invokeLater
            if (activeEditor.isDisposed || !isDisplayable || activeEditor.scrollingModel.visibleArea.width <= 0) return@invokeLater
            val lineHeight = activeEditor.lineHeight
            val contentHeight = activeEditor.offsetToXY(document.textLength).y + lineHeight
            val padding = insets.top + insets.bottom
            val sizing = promptSizing(contentHeight, lineHeight, padding, JBUI.scale(48))
            activeEditor.setVerticalScrollbarVisible(sizing.scrolls)
            if (preferredSize.height != sizing.height) {
                preferredSize = Dimension(100, sizing.height)
                revalidate()
            }
        }
    }
}

internal data class PromptSizing(val height: Int, val scrolls: Boolean)

internal fun promptSizing(contentHeight: Int, lineHeight: Int, padding: Int, minimumHeight: Int): PromptSizing {
    val maximumContentHeight = maxOf(minimumHeight - padding, 12 * lineHeight)
    return PromptSizing(
        (contentHeight.coerceAtMost(maximumContentHeight) + padding).coerceAtLeast(minimumHeight),
        contentHeight > maximumContentHeight,
    )
}

/** Attachments use their own remove controls; undo must not restore only their consumed trigger. */
internal fun consumePromptTrigger(project: Project, document: Document, offset: Int) {
    WriteCommandAction.writeCommandAction(project).withName("候補を選択").run<RuntimeException> {
        document.deleteString(offset, offset + 1)
        // ponytail: attachment state has no Undo model; stop at this document's selection boundary.
        // Add atomic attachment Undo only if the draft model gains a matching Undo/Redo contract.
        UndoManager.getInstance(project).nonundoableActionPerformed(
            DocumentReferenceManager.getInstance().create(document), false,
        )
    }
}
