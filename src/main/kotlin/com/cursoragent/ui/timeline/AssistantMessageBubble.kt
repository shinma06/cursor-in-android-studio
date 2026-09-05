package com.cursoragent.ui.timeline

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer

class AssistantMessageBubble(initialText: String = "") : JPanel(BorderLayout()) {
    private val contentLabel = MessageTextPane().apply {
        border = JBUI.Borders.empty()
    }
    private val contentBuilder = StringBuilder(initialText)

    init {
        isOpaque = false
        border = JBUI.Borders.empty(0, 10, 0, 10)
        add(contentLabel, BorderLayout.CENTER)
        if (initialText.isNotEmpty()) {
            setContent(initialText)
        }
    }

    fun appendContent(text: String) {
        contentBuilder.append(text)
        refreshLabel()
    }

    fun setContent(text: String) {
        contentBuilder.clear()
        contentBuilder.append(text)
        refreshLabel()
    }

    private fun refreshLabel() {
        contentLabel.text = "<html>${MarkdownRenderer.toHtmlFragment(contentBuilder.toString())}</html>"
        revalidate()
        repaint()
    }
}

/**
 * commonmark passes literal HTML found in the source straight through unescaped
 * by default (that's spec-correct CommonMark behavior for trusted input, but
 * agent responses routinely contain angle-bracket text that looks like a tag —
 * e.g. `List<String>` — which would otherwise vanish or render as a stray
 * unknown element in the JLabel's HTML view). HtmlInline/HtmlBlock nodes are
 * rendered as escaped text instead of raw passthrough to keep such text visible.
 */
object MarkdownRenderer {
    private val parser: Parser = Parser.builder().build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .nodeRendererFactory { context -> EscapingHtmlNodeRenderer(context) }
        .build()

    fun toHtmlFragment(markdown: String): String = renderer.render(parser.parse(markdown))

    private class EscapingHtmlNodeRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        override fun getNodeTypes(): Set<Class<out Node>> = setOf(HtmlInline::class.java, HtmlBlock::class.java)

        override fun render(node: Node) {
            val literal = when (node) {
                is HtmlInline -> node.literal
                is HtmlBlock -> node.literal
                else -> return
            }
            context.writer.text(literal)
        }
    }
}
