package com.cursoragent.ui.timeline

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.KeyboardFocusManager
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent

/** One instance belongs to one timeline. Workers search immutable text; Swing access stays on EDT. */
internal class ConversationFindPanel(
    private val bodies: () -> List<JTextComponent>,
    private val active: () -> Boolean,
    private val onClose: () -> Unit,
    private val submit: (() -> Unit) -> Future<*> = { ApplicationManager.getApplication().executeOnPooledThread(it) },
) : JPanel(BorderLayout(0, JBUI.scale(3))) {
    internal val search = SearchTextField(false)
    internal val matchCase = option("Aa", "大文字・小文字を区別")
    internal val wholeWord = option("単語", "単語単位で検索")
    internal val regex = option(".*", "正規表現で検索（先読み・後読み・後方参照は非対応）")
    internal val count = JLabel("0 / 0")
    private val notice = JTextArea().apply {
        isEditable = false
        isFocusable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        rows = 2
        isVisible = false
    }
    private val previous = button("↑", "前の一致箇所") { move(-1) }
    private val next = button("↓", "次の一致箇所") { move(1) }
    private val debounce = Timer(150) { searchNow() }.apply { isRepeats = false }
    private var worker: Future<*>? = null
    private var generation = 0
    private var disposed = false
    internal var composing = false
        private set
    private var ready = false
    private var targets = emptyList<JTextComponent>()
    private var hits = emptyList<ConversationFindHit>()
    private var selected = -1
    private var incomplete = false
    private val highlights = mutableListOf<Pair<Highlighter, Any>>()
    private var currentHighlight: Pair<Highlighter, Any>? = null
    private var anchor: Pair<JTextComponent, Int>? = null
    private val allPainter = matchPainter(JBColor(Color(0xFFF2A8), Color(0x665D20)))
    private val currentPainter = matchPainter(JBColor(Color(0xFFB85B), Color(0xA66C18)))

    init {
        isVisible = false
        isOpaque = false
        border = JBUI.Borders.empty(4, 8)
        search.textEditor.accessibleContext.accessibleName = "会話内を検索"
        search.textEditor.toolTipText = "表示中のユーザー・Agent本文を検索します。ツールや承認カードは含みません。"
        search.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = queryChanged()
            override fun removeUpdate(e: DocumentEvent) = queryChanged()
            override fun changedUpdate(e: DocumentEvent) = Unit
        })
        search.textEditor.addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(event: InputMethodEvent) {
                composing = event.committedCharacterCount < (event.text?.let { it.endIndex - it.beginIndex } ?: 0)
                invalidateResults()
            }
            override fun caretPositionChanged(event: InputMethodEvent) = Unit
        })
        add(JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(search, BorderLayout.CENTER)
            add(button("×", "会話内検索を閉じる") { closeSearch() }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JPanel(FlowLayout(FlowLayout.LEADING, 2, 0)).apply {
                isOpaque = false
                add(matchCase); add(wholeWord); add(regex)
            }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.TRAILING, 2, 0)).apply {
                isOpaque = false
                add(count); add(previous); add(next)
            }, BorderLayout.EAST)
        }, BorderLayout.CENTER)
        add(notice, BorderLayout.SOUTH)
        updateCount()
    }

    fun open() {
        if (disposed || !active()) return
        isVisible = true
        invalidateResults()
        search.textEditor.requestFocusInWindow()
        search.textEditor.selectAll()
    }

    fun closeSearch() {
        if (disposed || composing) return
        isVisible = false
        invalidateResults()
        onClose()
    }

    fun hasSearchFocus(): Boolean = isVisible && !disposed && !composing &&
        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.let { SwingUtilities.isDescendingFrom(it, this) } == true

    private fun queryChanged() {
        anchor = null
        invalidateResults()
    }

    /** Called synchronously with body replacement or tab activation, before a stale result can paint. */
    fun invalidateResults() {
        generation++
        debounce.stop()
        worker?.cancel(true)
        worker = null
        ready = false
        clearHighlights()
        targets = emptyList()
        hits = emptyList()
        selected = -1
        incomplete = false
        showNotice(null)
        updateCount()
        if (!disposed && isVisible && active() && !composing && search.text.isNotEmpty()) {
            count.text = "検索中…"
            debounce.restart()
        }
    }

    internal fun searchNow() {
        debounce.stop()
        if (disposed || !isVisible || !active() || composing || search.text.isEmpty()) return
        val ticket = generation
        val components = bodies()
        val documents = components.map { it.document }
        val texts = documents.map { it.getText(0, it.length) }
        val query = search.text
        val options = ConversationFindOptions(matchCase.isSelected, wholeWord.isSelected, regex.isSelected)
        worker = submit {
            val result = try { findConversationMatches(texts, query, options) } catch (_: CancellationException) { return@submit }
            SwingUtilities.invokeLater {
                if (disposed || !isVisible || !active() || composing || ticket != generation) return@invokeLater
                // Ownership alone is insufficient if a renderer replaced a Document.
                if (components.indices.any { components[it].document !== documents[it] }) {
                    invalidateResults()
                    return@invokeLater
                }
                targets = components
                hits = result.hits
                incomplete = result.notice != null
                showNotice(result.notice)
                ready = true
                hits.forEach { hit ->
                    val highlighter = targets[hit.document].highlighter
                    highlights.add(highlighter to highlighter.addHighlight(hit.start, hit.end, allPainter))
                }
                selected = hits.indexOfFirst { hit -> anchor?.let { it.first === targets[hit.document] && it.second == hit.start } == true }
                if (selected < 0 && hits.isNotEmpty()) selected = 0
                revealSelected()
            }
        }
    }

    fun move(direction: Int) {
        if (!ready || !isVisible || !active() || composing || disposed || hits.isEmpty()) return
        selected = Math.floorMod(selected + direction, hits.size)
        revealSelected()
    }

    private fun revealSelected() {
        currentHighlight?.let { (highlighter, tag) -> highlighter.removeHighlight(tag) }
        currentHighlight = null
        hits.getOrNull(selected)?.let { hit ->
            val component = targets[hit.document]
            anchor = component to hit.start
            val highlighter = component.highlighter
            currentHighlight = highlighter to highlighter.addHighlight(hit.start, hit.end, currentPainter)
            // Keep the search field focused and preserve the message's native text selection.
            component.modelToView2D(hit.start)?.bounds?.let { bounds ->
                component.modelToView2D(hit.end)?.bounds?.takeIf { it.y == bounds.y }?.let(bounds::add)
                bounds.width = bounds.width.coerceAtLeast(JBUI.scale(4))
                // The code's inner viewport consumes scrollRectToVisible. Reveal the same
                // text rectangle in every enclosing viewport, including the transcript.
                var parent = component.parent
                while (parent != null) {
                    if (parent is JViewport) {
                        (parent.view as? JComponent)?.let { view ->
                            view.scrollRectToVisible(SwingUtilities.convertRectangle(component, bounds, view))
                        }
                    }
                    parent = parent.parent
                }
            }
        }
        updateCount()
    }

    private fun clearHighlights() {
        currentHighlight?.let { (highlighter, tag) -> highlighter.removeHighlight(tag) }
        currentHighlight = null
        highlights.forEach { (highlighter, tag) -> highlighter.removeHighlight(tag) }
        highlights.clear()
    }

    private fun updateCount() {
        count.text = "${if (selected < 0) 0 else selected + 1} / ${hits.size}${if (incomplete) "+" else ""}"
        count.accessibleContext.accessibleName = "一致箇所 ${count.text}${if (incomplete) "（検索未完了）" else ""}"
        previous.isEnabled = ready && hits.isNotEmpty()
        next.isEnabled = previous.isEnabled
    }

    private fun showNotice(text: String?) {
        notice.text = text.orEmpty()
        notice.isVisible = text != null
        revalidate()
    }

    private fun option(text: String, description: String) = JToggleButton(text).apply {
        margin = JBUI.insets(1, 3)
        toolTipText = description
        accessibleContext.accessibleName = description
        addActionListener { queryChanged() }
    }

    private fun button(text: String, description: String, action: () -> Unit) = JButton(text).apply {
        margin = JBUI.insets(1, 4)
        isDefaultCapable = false
        toolTipText = description
        accessibleContext.accessibleName = description
        addActionListener { action() }
    }

    fun dispose() {
        disposed = true
        invalidateResults()
        anchor = null
    }
}

/** A zero-width regex match needs a visible marker; it must not select or modify nearby text. */
private fun matchPainter(color: Color) = object : DefaultHighlighter.DefaultHighlightPainter(color) {
    override fun paintLayer(g: java.awt.Graphics, p0: Int, p1: Int, bounds: java.awt.Shape, c: JTextComponent, view: javax.swing.text.View): java.awt.Shape? {
        if (p0 != p1) return super.paintLayer(g, p0, p1, bounds, c, view)
        val rect = c.modelToView2D(p0)?.bounds ?: return null
        rect.width = JBUI.scale(2)
        g.color = color
        g.fillRect(rect.x, rect.y, rect.width, rect.height)
        return rect
    }
}
