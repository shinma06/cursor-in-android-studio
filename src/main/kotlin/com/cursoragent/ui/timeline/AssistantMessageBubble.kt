package com.cursoragent.ui.timeline

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer

class AssistantMessageBubble(
    initialText: String = "",
    private val copyMarkdown: (String) -> Unit = { text ->
        val clipboard = CopyPasteManager.getInstance()
        clipboard.setContents(StringSelection(text))
        check(clipboard.getContents<String>(DataFlavor.stringFlavor) == text)
    },
) : JPanel(BorderLayout()) {
    private var rawMarkdown = ""
    private val copyButton = JButton("コピー").apply {
        isEnabled = false
        isDefaultCapable = false
        accessibleContext.accessibleName = "Markdown原文をコピー"
        toolTipText = "この応答のMarkdown原文をコピー"
        inputMap.put(KeyStroke.getKeyStroke("pressed ENTER"), "pressed")
        inputMap.put(KeyStroke.getKeyStroke("released ENTER"), "released")
        addActionListener { copyResponse() }
    }
    private val contentLabel = MessageTextPane().apply {
        border = JBUI.Borders.empty()
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(0, 8, 0, 8)
        add(contentLabel, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(4)
            add(copyButton)
        }, BorderLayout.SOUTH)
        if (initialText.isNotEmpty()) {
            setContent(initialText)
        }
    }

    private fun copyResponse() {
        val snapshot = rawMarkdown
        if (snapshot.isEmpty()) return
        try {
            copyMarkdown(snapshot)
            copyButton.text = "コピー済み"
            copyButton.toolTipText = "Markdown原文をコピーしました"
        } catch (_: Exception) {
            copyButton.text = "コピー失敗"
            copyButton.toolTipText = "コピーできませんでした。もう一度押して再試行してください"
        }
        copyButton.accessibleContext.accessibleDescription = copyButton.toolTipText
        revalidate()
        repaint()
    }

    fun setContent(text: String) {
        if (rawMarkdown != text) {
            rawMarkdown = text
            copyButton.text = "コピー"
            copyButton.toolTipText = "この応答のMarkdown原文をコピー"
            copyButton.accessibleContext.accessibleDescription = copyButton.toolTipText
        }
        copyButton.isEnabled = text.isNotEmpty()
        contentLabel.text = "<html>${MarkdownRenderer.toHtmlFragment(text)}</html>"
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
