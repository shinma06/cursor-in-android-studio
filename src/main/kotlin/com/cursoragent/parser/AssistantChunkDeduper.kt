package com.cursoragent.parser

/**
 * Deduplicates `AssistantDelta` chunks against a running buffer for one turn.
 *
 * **This heuristic is unverified.** It assumes the CLI's `stream-partial-output`
 * mode can send either true incremental deltas or occasional cumulative/repeated
 * text, and guesses which case it's in via `startsWith`/`endsWith`/`contains`
 * checks — but the M0 CLI spike (see requirements doc §13, `CLAUDE.md`) never
 * got past a `resource_exhausted` quota error to actually observe a real
 * `assistant` event sequence. Treat this as a plausible-looking guess sitting on
 * the critical path of chat rendering correctness, not a confirmed fact about the
 * CLI, until it's checked against real streamed output.
 */
class AssistantChunkDeduper {
    private val buffer = StringBuilder()

    fun dedupe(text: String): String? {
        if (text.isEmpty()) return null
        val current = buffer.toString()
        val chunk = when {
            current.isEmpty() -> text
            text.startsWith(current) -> text.substring(current.length)
            current.endsWith(text) || current.contains(text) -> return null
            else -> text
        }
        if (chunk.isEmpty()) return null
        buffer.append(chunk)
        return chunk
    }
}
