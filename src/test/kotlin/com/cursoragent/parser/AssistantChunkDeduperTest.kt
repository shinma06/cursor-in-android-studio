package com.cursoragent.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins down the CURRENT (unverified, see the class doc comment) dedup heuristic's
 * behavior so a future change can't silently alter it. These are not a spec for
 * "correct" behavior against the real CLI -- nobody has seen real assistant-delta
 * traffic yet (blocked on the M0 CLI quota, see requirements doc §13).
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
    fun `empty input is dropped`() {
        val deduper = AssistantChunkDeduper()
        assertNull(deduper.dedupe(""))
    }
}
