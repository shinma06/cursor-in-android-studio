package com.cursoragent.ui.composer

import com.cursoragent.ui.AgentUiColors
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

    init {
        setOneLineMode(false)
        setPlaceholder("Plan, Build, / for skills, @ for context")
        setShowPlaceholderWhenFocused(true)
        border = JBUI.Borders.empty(10, 10, 4, 10)
        isOpaque = false
        preferredSize = JBUI.size(100, 58)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = scheduleResize()
        })
    }

    override fun createEditor(): EditorEx = super.createEditor().also { editor ->
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
        editor.scrollPane.verticalScrollBar.apply {
            setUI(PromptScrollBarUI())
            preferredSize = JBUI.size(10, 0)
            isOpaque = false
        }
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
            val sizing = promptSizing(contentHeight, lineHeight, padding, JBUI.scale(58))
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
