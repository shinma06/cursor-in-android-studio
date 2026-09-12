package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
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
    private var resizePending = false
    var isComposing = false
        private set
    private var imeRevision = 0L

    init {
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
        // Disable the IDE's Tab indentation action and use local Swing traversal keys.
        editor.isEmbeddedIntoDialogWrapper = true
        installPromptFocusTraversal(editor.contentComponent)
        editor.contentComponent.addInputMethodListener(object : java.awt.event.InputMethodListener {
            override fun inputMethodTextChanged(event: java.awt.event.InputMethodEvent) {
                val revision = ++imeRevision
                val pending = event.text?.let { it.endIndex - it.beginIndex > event.committedCharacterCount } == true
                isComposing = true
                if (!pending) SwingUtilities.invokeLater { if (revision == imeRevision) isComposing = false }
            }
            override fun caretPositionChanged(event: java.awt.event.InputMethodEvent) = Unit
        })
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
