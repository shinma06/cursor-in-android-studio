package com.cursoragent.ui.timeline

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.time.Duration
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
        val result = findConversationMatches(listOf("aa"), "(?:)|$", ConversationFindOptions(regex = true))
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
        assertTrue(expensive.hits.isEmpty())
        assertTrue(System.nanoTime() - started < 2_000_000_000, "nested repetitions must remain bounded")
        val timedOut = findConversationMatches(listOf("text"), "text", ConversationFindOptions(), budgetNanos = 0)
        assertNotNull(timedOut.notice)
        val tooLong = findConversationMatches(listOf("text"), "a".repeat(4_097), ConversationFindOptions())
        assertNotNull(tooLong.notice)
    }

    @Test
    fun `unsupported syntax and enormous repeats are rejected before matching`() {
        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            listOf("(?=a)", "(?<=a)b", "(a)\\1", "(?:){2147483647}+", "(?:(?:){2147483647}+){2147483647}+", "((a{1000}){1000}){1000}").forEach { query ->
                val result = findConversationMatches(listOf("normal transcript"), query, ConversationFindOptions(regex = true))
                assertTrue(result.hits.isEmpty(), query)
                assertNotNull(result.notice, query)
            }
        }
    }

    @Test
    fun `supported regex and unicode case keep UTF16 offsets`() {
        val text = "😀 Ää 日本語\n123"
        val result = findConversationMatches(listOf(text), "😀|ä|日本語|[0-9]{3}", ConversationFindOptions(regex = true))
        assertNull(result.notice)
        assertEquals(listOf("😀", "Ä", "ä", "日本語", "123"), result.hits.map { text.substring(it.start, it.end) })
        assertEquals(2, result.hits.first().end)
        val literal = "{1000}{1000}"
        assertEquals(1, findConversationMatches(listOf(literal), "\\Q$literal\\E", ConversationFindOptions(regex = true)).hits.size)
        assertEquals(1, findConversationMatches(listOf("aaa"), "a{2,3}", ConversationFindOptions(regex = true)).hits.size)
    }

    @Test
    fun `cancellation stops a matcher that has already begun reading`() {
        val text = "abc".repeat(1_000_000)
        val outcome = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                findConversationMatches(listOf(text), "(a|b|c)+z", ConversationFindOptions(regex = true), budgetNanos = 5_000_000_000)
            } catch (error: Throwable) {
                outcome.set(error)
            }
        }.apply { isDaemon = true }
        worker.start()
        try {
            val deadline = System.nanoTime() + 2_000_000_000
            while (worker.isAlive && worker.stackTrace.none { it.className == "com.google.re2j.Machine" } && System.nanoTime() < deadline) Thread.sleep(1)
            assertTrue(worker.isAlive, "probe must reach an in-progress match")
            assertTrue(worker.stackTrace.any { it.className.startsWith("com.google.re2j.") }, "matcher must have started before cancellation")
            worker.interrupt()
            worker.join(1_000)
            assertFalse(worker.isAlive, "cancelled matcher must release its worker")
            assertInstanceOf(CancellationException::class.java, outcome.get())
        } finally {
            worker.interrupt()
            worker.join(1_000)
        }
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
