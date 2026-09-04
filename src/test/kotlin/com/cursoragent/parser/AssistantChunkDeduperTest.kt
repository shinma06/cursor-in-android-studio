package com.cursoragent.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins down dedup behavior against live CLI observations (Teams plan, 2026-09).
 */
class AssistantChunkDeduperTest {
    @Test
    fun `first chunk passes through unchanged`() {
        val deduper = AssistantChunkDeduper()
        assertEquals("Hello", deduper.dedupe("Hello"))
    }

    @Test
    fun `true incremental deltas each pass through unchanged`() {
        val deduper = AssistantChunkDeduper()
        assertEquals("Hello", deduper.dedupe("Hello"))
        assertEquals(", world", deduper.dedupe(", world"))
        assertEquals("!", deduper.dedupe("!"))
    }

    @Test
    fun `a cumulative resend is reduced to only the new suffix`() {
        val deduper = AssistantChunkDeduper()
        deduper.dedupe("Hello")
        assertEquals(", world", deduper.dedupe("Hello, world"))
    }

    @Test
    fun `an exact repeat of everything so far is dropped`() {
        val deduper = AssistantChunkDeduper()
        deduper.dedupe("Hello")
        assertNull(deduper.dedupe("Hello"))
    }

    @Test
    fun `disconnected orphan fragment is replaced by a longer message`() {
        val deduper = AssistantChunkDeduper()
        assertEquals(" overwrite it with the", deduper.dedupe(" overwrite it with the"))
        assertEquals(
            "I'll read the file first, then overwrite it with the new content.\n",
            deduper.dedupe("I'll read the file first, then overwrite it with the new content.\n"),
        )
    }
}
