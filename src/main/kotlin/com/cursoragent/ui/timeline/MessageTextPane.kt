package com.cursoragent.ui.timeline

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.MouseWheelEvent
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.SizeRequirements
import javax.swing.SwingUtilities
import javax.swing.text.Element
import javax.swing.text.StyleConstants
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.CSS
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.ParagraphView
import javax.swing.text.html.StyleSheet

/** Selectable HTML; presentation updates retain the document and its selection. */
class MessageTextPane : JEditorPane() {
    private var revision = 0
    private var measuredRevision = -1
    private var measuredWidth = -1
    private var measuredFont: java.awt.Font? = null
    private var measuredHeight = 0
    private var customFontSize = 0
    private var wrapCode = false
    private var initialized = false
    private var settingsConnection: MessageBusConnection? = null

    init {
        editorKit = object : HTMLEditorKit() {
            private val factory = object : HTMLFactory() {
                override fun create(element: Element): View {
                    // Swing's default LineView deliberately never wraps PRE. Retain the parsed
                    // text/whitespace and use its native paragraph layout only for wrapped code.
                    if (wrapCode && element.attributes.getAttribute(StyleConstants.NameAttribute) == HTML.Tag.IMPLIED &&
                        element.attributes.getAttribute(CSS.Attribute.WHITE_SPACE) == "pre"
                    ) {
                        return object : ParagraphView(element) {
                            override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements =
                                super.calculateMinorAxisRequirements(axis, r).apply { minimum = 0 }
                        }
                    }
                    return super.create(element)
                }
            }
            override fun getViewFactory(): ViewFactory = factory
        }.apply {
            // Do not add display rules to HTMLEditorKit's shared default stylesheet.
            styleSheet = StyleSheet().apply {
                addStyleSheet(styleSheet)
                addRule("body { margin: 0; }")
                addRule("p { margin-top: 0; margin-bottom: 8px; }")
                addRule("ul, ol { margin-left: 18px; margin-top: 4px; margin-bottom: 8px; }")
                addRule("pre { margin: 6px 0; }")
            }
        }
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true)
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        margin = JBUI.emptyInsets()
        document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) { revision++ }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) { revision++ }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) { revision++ }
        })
        initialized = true
        refreshSettings()
    }

    override fun addNotify() {
        super.addNotify()
        val application = ApplicationManager.getApplication() ?: return
        settingsConnection?.disconnect()
        settingsConnection = application.messageBus.connect().also { connection ->
            connection.subscribe(AgentSettingsState.DISPLAY_CHANGED, Runnable {
                if (SwingUtilities.isEventDispatchThread()) refreshSettings()
                else SwingUtilities.invokeLater { if (settingsConnection === connection) refreshSettings() }
            })
        }
        refreshSettings()
    }

    override fun removeNotify() {
        settingsConnection?.disconnect()
        settingsConnection = null
        super.removeNotify()
    }

    private fun refreshSettings() {
        val settings = ApplicationManager.getApplication()?.getService(AgentSettingsState::class.java)
        applyDisplaySettings(settings?.conversationFontSize ?: 0, settings?.wrapCodeLines ?: false)
    }

    internal fun applyDisplaySettings(fontSize: Int, wrap: Boolean) {
        customFontSize = if (fontSize in 8..36) fontSize else 0
        val changedWrap = wrapCode != wrap
        wrapCode = wrap
        if (changedWrap) rebuildViews() else refreshFont()
        revision++
        revalidate()
        repaint()
    }

    override fun updateUI() {
        // Native theme/scale changes also rebuild views. Preserve the selection direction.
        if (initialized) rebuildViews() else super.updateUI()
    }

    private fun rebuildViews() {
        val dot = caret.dot
        val mark = caret.mark
        super.updateUI()
        refreshFont()
        caret.setDot(mark)
        caret.moveDot(dot)
        revision++
    }

    private fun refreshFont() {
        font = if (customFontSize == 0) AgentUiMetrics.textFont() else JBUI.Fonts.label(customFontSize.toFloat())
    }

    /** Horizontal scrolling is local to this message; the transcript retains its native vertical scroll. */
    internal fun scrollable(): JScrollPane = object : JScrollPane(this, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
        init {
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }
        override fun processMouseWheelEvent(event: MouseWheelEvent) {
            val transcript = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, parent)
            if (!event.isShiftDown && transcript != null) {
                transcript.dispatchEvent(SwingUtilities.convertMouseEvent(this, event, transcript))
                event.consume()
            } else {
                super.processMouseWheelEvent(event)
            }
        }
        override fun getPreferredSize(): Dimension {
            val available = (width - insets.left - insets.right).coerceAtLeast(1)
            val contentWidth = maxOf(available, minimumContentWidth())
            this@MessageTextPane.setSize(contentWidth, 1)
            val barHeight = if (contentWidth > available) horizontalScrollBar.preferredSize.height else 0
            return Dimension(available, this@MessageTextPane.preferredSize.height + barHeight + insets.top + insets.bottom)
        }
        override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)
    }

    private fun minimumContentWidth(): Int = kotlin.math.ceil(getUI().getRootView(this).getMinimumSpan(View.X_AXIS).toDouble()).toInt()

    override fun getScrollableTracksViewportWidth(): Boolean =
        (parent as? JViewport)?.let { minimumContentWidth() <= it.width } ?: true

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
        return Dimension(maxOf(available, minimumContentWidth()), measuredHeight + insets.top + insets.bottom)
    }

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)
}
