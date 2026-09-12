package com.cursoragent.ui.composer.mention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MentionTokenExtractorTest {
    @Test
    fun `typed references preserve space paths and deduplicate equivalent legacy tokens`() {
        val typed = listOf(Mention(MentionKind.FILE, "日本語 file.kt", "日本語 file.kt"),
            Mention(MentionKind.FILE, "A.kt", "A.kt"), Mention(MentionKind.FILE, "docs", "docs"))
        val merged = contextMentions("@A.kt @./A.kt @docs", typed)
        assertEquals(4, merged.size)
        assertEquals("日本語 file.kt", merged.first().insertToken)
        assertEquals(1, merged.count { it.kind == MentionKind.FILE && it.insertToken == "A.kt" })
        assertEquals(setOf(MentionKind.FILE, MentionKind.DOCS), merged.filter { it.insertToken == "docs" }.map { it.kind }.toSet())
    }

    @Test
    fun `extracts a single file token`() {
        assertEquals(listOf("src/Foo.kt"), MentionTokenExtractor.extractTokens("Please look at @src/Foo.kt for context"))
    }

    @Test
    fun `extracts multiple distinct tokens in send order`() {
        val prompt = "Compare @git-diff with @src/Foo.kt and also @src/Foo.kt again"
        assertEquals(listOf("git-diff", "src/Foo.kt"), MentionTokenExtractor.extractTokens(prompt))
    }

    @Test
    fun `extracts a folder token keeping its trailing slash`() {
        assertEquals(listOf("src/main/"), MentionTokenExtractor.extractTokens("Refactor everything under @src/main/"))
    }

    @Test
    fun `returns empty list when there are no mention tokens`() {
        assertEquals(emptyList<String>(), MentionTokenExtractor.extractTokens("just a normal question, no mentions here"))
    }

    @Test
    fun `does not treat an email address as a mention`() {
        assertEquals(emptyList<String>(), MentionTokenExtractor.extractTokens("contact me at user@example.com please"))
    }
}
