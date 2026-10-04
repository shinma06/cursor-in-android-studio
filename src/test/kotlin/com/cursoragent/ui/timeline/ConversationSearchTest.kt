package com.cursoragent.ui.timeline

import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConversationSearchTest {
    @Test
    fun `literal case word and unicode matches keep document offsets and do not cross messages`() {
        val bodies = listOf("Cat cat scatter cat_ cat\n日本語 日本語X", "cat")
        fun find(query: String, case: Boolean = false, word: Boolean = false) =
            findConversationMatches(bodies, query, ConversationFindOptions(case, word))
        assertEquals(6, find("cat").hits.size)
        assertEquals(5, find("cat", case = true).hits.size)
        val words = find("cat", word = true).hits
        assertEquals(listOf(0, 4, 21, 0), words.map { it.start })
        words.forEach { assertEquals("cat", bodies[it.document].substring(it.start, it.end).lowercase()) }
        assertEquals(1, find("日本語", word = true).hits.size)
        assertTrue(findConversationMatches(listOf("first", "second"), "firstsecond", ConversationFindOptions()).hits.isEmpty())
        assertEquals(1, findConversationMatches(listOf("a.b axb"), "a.b", ConversationFindOptions()).hits.size)
        assertEquals(2, findConversationMatches(listOf("a.b axb"), "a.b", ConversationFindOptions(regex = true)).hits.size)
    }

    @Test
    fun `regex anchors zero width and invalid patterns terminate without invented matches`() {
        val result = findConversationMatches(listOf("aa"), "(?=a)|$", ConversationFindOptions(regex = true))
        assertNull(result.notice)
        assertEquals(listOf(0, 1, 2), result.hits.map { it.start })
        assertTrue(result.hits.all { it.start == it.end })
        val invalid = findConversationMatches(listOf("anything"), "[", ConversationFindOptions(regex = true))
        assertTrue(invalid.hits.isEmpty())
        assertNotNull(invalid.notice)
        assertTrue(findConversationMatches(listOf("anything"), "", ConversationFindOptions()).hits.isEmpty())
    }

    @Test
    fun `result limit and regex work budget report incomplete results instead of complete zero`() {
        val limited = findConversationMatches(listOf("aaaa"), "a", ConversationFindOptions(), maxMatches = 2)
        assertEquals(2, limited.hits.size)
        assertNotNull(limited.notice)
        val started = System.nanoTime()
        val expensive = findConversationMatches(listOf("a".repeat(30_000) + "!"), "(a+)+$", ConversationFindOptions(regex = true), budgetNanos = 10_000_000)
        assertNotNull(expensive.notice)
        assertTrue(System.nanoTime() - started < 2_000_000_000, "backtracking must cooperate with cancellation")
        val tooLong = findConversationMatches(listOf("text"), "a".repeat(4_097), ConversationFindOptions())
        assertNotNull(tooLong.notice)
    }

    @Test
    fun `interrupted search does not return a usable result`() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(CancellationException::class.java) { findConversationMatches(listOf("hello"), "hello", ConversationFindOptions()) }
        } finally {
            Thread.interrupted()
        }
    }
}
