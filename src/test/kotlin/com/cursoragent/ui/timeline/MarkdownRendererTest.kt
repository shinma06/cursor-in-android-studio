package com.cursoragent.ui.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownRendererTest {
    @Test
    fun `renders a code fence as pre code`() {
        val html = MarkdownRenderer.toHtmlFragment("```kotlin\nval x = 1\n```")
        assertTrue(html.contains("<pre>"))
        assertTrue(html.contains("<code"))
        assertTrue(html.contains("val x = 1"))
    }

    @Test
    fun `escapes html-significant characters in plain text`() {
        val html = MarkdownRenderer.toHtmlFragment("if a < b && b > c")
        assertTrue(html.contains("&lt;"))
        assertTrue(html.contains("&gt;"))
        assertTrue(html.contains("&amp;"))
        assertTrue(!html.contains("a < b"))
    }

    @Test
    fun `renders bold and italic emphasis`() {
        val html = MarkdownRenderer.toHtmlFragment("**bold** and *italic*")
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
    }

    @Test
    fun `does not pass through raw html unescaped`() {
        val html = MarkdownRenderer.toHtmlFragment("<script>alert(1)</script>")
        assertTrue(!html.contains("<script>alert"))
    }

    @Test
    fun `keeps generic type syntax visible instead of treating it as a tag`() {
        val html = MarkdownRenderer.toHtmlFragment("Use a List<String> here.")
        assertTrue(html.contains("List&lt;String&gt;"))
    }

    @Test
    fun `renders an unordered list`() {
        val html = MarkdownRenderer.toHtmlFragment("- one\n- two")
        assertEquals(2, Regex("<li>").findAll(html).count())
    }
}
