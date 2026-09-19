package com.cursoragent.ui.timeline

import com.cursoragent.history.ChatMessage
import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationStore
import com.cursoragent.history.SavedTurn
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.ImageView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MarkdownImageSafetyTest {
    @Test
    fun `inline reference and nested alt images become escaped readable text`() {
        val html = MarkdownRenderer.toHtmlFragment(
            "![日本語 **太字** `code` <tag> ![子](child.png)](image.png?a=1&b=2)\n\n" +
                "![参照][image]\n\n[image]: <source\"quoted.png> \"title\"",
        )
        assertTrue(html.contains("[画像: 日本語 太字 code &lt;tag&gt; 子]（取得元: image.png?a=1&amp;b=2）"), html)
        assertTrue(html.contains("[画像: 参照]（取得元: source&quot;quoted.png）"), html)
        assertFalse(html.contains("<img"), html)
        assertFalse(html.contains("<a "), html)
    }

    @Test
    fun `empty alt missing destination and all source schemes remain non-actionable text`() {
        for (source in listOf("", "https://example.invalid/image.png", "http://example.invalid/image.png",
            "//example.invalid/image.png", "file:///synthetic/image.png", "data:image/png;base64,AAAA",
            "javascript:alert(1)", "unknown:bad%zz", "相対/画像.png")) {
            val html = MarkdownRenderer.toHtmlFragment("![]($source)")
            assertTrue(html.contains("[画像]（取得元: ${source.ifEmpty { "未指定" }}）"), html)
            assertFalse(html.contains("<img"), html)
            assertFalse(html.contains("<a "), html)
        }
    }

    @Test
    fun `literal HTML stays escaped and normal Markdown including explicit links is retained`() {
        val html = MarkdownRenderer.toHtmlFragment(
            "<img src=\"image.png\">\n\n# 見出し\n\n- **強調**\n\n" +
                "[公式](https://example.invalid/docs)\n\n```text\n![code](image.png)\n```",
        )
        assertTrue(html.contains("&lt;img src=&quot;image.png&quot;&gt;"), html)
        assertTrue(html.contains("<h1>見出し</h1>"), html)
        assertTrue(html.contains("<li><strong>強調</strong></li>"), html)
        assertTrue(html.contains("<a href=\"https://example.invalid/docs\">公式</a>"), html)
        assertTrue(html.contains("![code](image.png)"), html)
        assertFalse(html.contains("<img"), html)
    }

    @Test
    fun `positive control observes standard Swing image reading entirely in memory`() {
        val source = InMemoryImageSource()
        SwingUtilities.invokeAndWait {
            val pane = MessageTextPane()
            (pane.document as HTMLDocument).base = source.base
            pane.text = "<html><img src=\"control.png\"></html>"
            paint(pane)
            assertTrue(imageViews(pane.ui.getRootView(pane)) > 0)
        }
        assertTrue(source.opened.await(5, TimeUnit.SECONDS), "Control must observe a read, without external networking")
        assertTrue(source.reads.get() > 0)
    }

    @Test
    fun `real pane live cumulative correction and Copy create no image load and preserve source`() = SwingUtilities.invokeAndWait {
        val source = InMemoryImageSource()
        val copies = mutableListOf<String>()
        val bubble = AssistantMessageBubble(copyMarkdown = { copies.add(it) })
        val pane = descendants(bubble).filterIsInstance<MessageTextPane>().single()
        (pane.document as HTMLDocument).base = source.base
        val snapshots = listOf("![日本語](live.png)", "![日本語](live.png)\r\n\r\n![続き][image]\n\n[image]: next.png\n",
            "訂正 ![別](corrected.png)  \n<img src=\"literal.png\">", "")
        for (raw in snapshots) {
            bubble.setContent(raw)
            assertNoImages(pane)
            descendants(bubble).filterIsInstance<JButton>().single().doClick(0)
            assertNoImages(pane)
            assertEquals(0, source.reads.get())
        }
        assertEquals(snapshots.dropLast(1), copies)
    }

    @Test
    fun `saved history restore retains source and produces no image views`(@TempDir directory: Path) {
        val raw = "  ![保存画像](saved.png)\r\n\r\n![参照][image]\n\n[image]: 相対/画像.png\n"
        val saved = Conversation(turns = listOf(SavedTurn(state = "completed", messages = listOf(ChatMessage(role = "assistant", text = raw)))))
        val store = ConversationStore(directory)
        store.save(saved)
        val loaded = store.load().conversations.single()
        SwingUtilities.invokeAndWait {
            val timeline = ChatTimelinePanel()
            timeline.restore(loaded)
            val pane = descendants(timeline).filterIsInstance<MessageTextPane>().single()
            assertNoImages(pane)
            assertTrue(pane.document.getText(0, pane.document.length).contains("[画像: 保存画像]（取得元: saved.png）"))
            assertEquals(raw, loaded.turns.single().messages.single().text)
            val copies = mutableListOf<String>()
            val copied = AssistantMessageBubble(loaded.turns.single().messages.single().text) { copies.add(it) }
            descendants(copied).filterIsInstance<JButton>().single().doClick(0)
            assertEquals(listOf(raw), copies)
            assertNoImages(descendants(copied).filterIsInstance<MessageTextPane>().single())
        }
        assertEquals(saved, store.load().conversations.single())
    }

    private fun assertNoImages(pane: MessageTextPane) {
        paint(pane)
        assertFalse((pane.document as HTMLDocument).getIterator(HTML.Tag.IMG).isValid)
        assertEquals(0, imageViews(pane.ui.getRootView(pane)))
    }

    private fun paint(pane: MessageTextPane) {
        pane.setSize(300, 800)
        pane.setSize(300, pane.preferredSize.height.coerceAtLeast(1))
        val graphics = BufferedImage(300, pane.height, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try { pane.paint(graphics) } finally { graphics.dispose() }
    }

    private fun imageViews(view: View): Int = (if (view is ImageView) 1 else 0) +
        (0 until view.viewCount).sumOf { imageViews(view.getView(it)) }

    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap { descendants(it) } else emptyList()

    /** Per-document handler, no global URL factory, sockets, files, or external URLs. */
    @Suppress("DEPRECATION")
    private class InMemoryImageSource {
        private val png = ByteArrayOutputStream().apply {
            ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", this)
        }.toByteArray()
        val reads = AtomicInteger()
        val opened = CountDownLatch(1)
        val base = URL(null, "fixture://images/${UUID.randomUUID()}/", object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = object : URLConnection(url) {
                override fun connect() = Unit
                override fun getInputStream(): ByteArrayInputStream {
                    reads.incrementAndGet()
                    opened.countDown()
                    return ByteArrayInputStream(png)
                }
            }
        })
    }
}
