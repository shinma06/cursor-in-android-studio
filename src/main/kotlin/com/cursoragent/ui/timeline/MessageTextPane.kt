package com.cursoragent.ui.timeline

import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.UIManager
import javax.swing.text.View
import javax.swing.text.html.HTMLEditorKit

/** Selectable HTML with height measured at the width allocated by the transcript. */
class MessageTextPane : JEditorPane() {
    private var revision = 0
    private var measuredRevision = -1
    private var measuredWidth = -1
    private var measuredFont: java.awt.Font? = null
    private var measuredHeight = 0

    init {
        editorKit = HTMLEditorKit().apply {
            styleSheet.addRule("body { margin: 0; }")
            styleSheet.addRule("p { margin-top: 0; margin-bottom: 8px; }")
            styleSheet.addRule("ul, ol { margin-left: 18px; margin-top: 4px; margin-bottom: 8px; }")
            styleSheet.addRule("pre { margin: 6px 0; }")
        }
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true)
        font = UIManager.getFont("Label.font")
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        margin = JBUI.emptyInsets()
        document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                revision++
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                revision++
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                revision++
            }
        })
    }

    override fun getPreferredSize(): Dimension {
        if (width <= 0) return Dimension(0, getFontMetrics(font).height)
        val available = (width - insets.left - insets.right).coerceAtLeast(1)
        if (available != measuredWidth || revision != measuredRevision || font != measuredFont) {
            val view = getUI().getRootView(this)
            view.setSize(available.toFloat(), 0f)
            measuredHeight = kotlin.math.ceil(view.getPreferredSpan(View.Y_AXIS).toDouble()).toInt()
            measuredWidth = available
            measuredRevision = revision
            measuredFont = font
        }
        return Dimension(available, measuredHeight + insets.top + insets.bottom)
    }

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)
}
