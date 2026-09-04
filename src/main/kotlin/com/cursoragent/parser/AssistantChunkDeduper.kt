package com.cursoragent.parser

/**
 * Deduplicates `AssistantDelta` chunks against a running buffer for one turn.
 *
 * Verified against live `cursor-agent` stream-json (Teams plan, CLI
 * `2026.09.02-c22c1a3`, 2026-09): `--stream-partial-output` interleaves true
 * incremental fragments with occasional cumulative/resend full sentences. The
 * heuristics below handle prefix-extension and drop exact repeats; disconnected
 * short orphan fragments are dropped when a longer replacement message arrives.
 */
class AssistantChunkDeduper {
    private val buffer = StringBuilder()

    fun dedupe(text: String): String? {
        if (text.isEmpty()) return null
        val current = buffer.toString()
        val chunk = when {
            current.isEmpty() -> text
            text.startsWith(current) -> text.substring(current.length)
            current.endsWith(text) || current == text -> return null
            text.length > current.length && !text.startsWith(current) && looksLikeReplacement(current, text) -> {
                buffer.clear()
                text
            }
            current.endsWith(text) || current.contains(text) -> return null
            else -> text
        }
        if (chunk.isEmpty()) return null
        buffer.clear()
        buffer.append(
            when {
                text.startsWith(current) -> text
                looksLikeReplacement(current, text) -> text
                else -> current + chunk
            },
        )
        return chunk
    }

    private fun looksLikeReplacement(current: String, text: String): Boolean {
        if (text.contains('\n')) return true
        if (text.length >= 24 && !current.contains(text)) return true
        return false
    }
}
