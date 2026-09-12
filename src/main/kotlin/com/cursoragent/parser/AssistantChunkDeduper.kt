package com.cursoragent.parser

/**
 * Legacy fallback for unknown print producers or metadata outside the verified contract.
 * [PrintAssistantText] owns strategy selection; ACP must never use this heuristic.
 *
 * Mixed chunk shapes were observed in live print stream-json (Teams plan, CLI
 * `2026.09.02-c22c1a3`, 2026-09): `--stream-partial-output` interleaves true
 * incremental fragments with occasional cumulative/resend full sentences. The
 * heuristics below handle prefix-extension and drop exact repeats; disconnected
 * short orphan fragments are dropped when a longer replacement message arrives.
 * This is a heuristic, not a proof for every stream or an ACP chunk contract;
 * keep its regression tests and re-evaluate before reuse across transports.
 *
 * [dedupe] always returns the full text that should be displayed (or null if
 * [text] adds nothing new) rather than an incremental fragment — a "resend the
 * whole cumulative message" event can't be expressed as a suffix to append, so
 * callers must always re-set the displayed content from the return value
 * ([com.cursoragent.ui.timeline.ChatTimelinePanel.setAssistantText]) instead of
 * appending it.
 */
class AssistantChunkDeduper {
    private val buffer = StringBuilder()

    fun dedupe(text: String): String? {
        if (text.isEmpty()) return null
        val current = buffer.toString()
        when {
            current.isEmpty() -> buffer.append(text)
            text.startsWith(current) -> {
                val added = text.substring(current.length)
                if (added.isEmpty()) return null
                buffer.append(added)
            }
            current.endsWith(text) || current == text || current.contains(text) -> return null
            looksLikeReplacement(current, text) -> {
                buffer.clear()
                buffer.append(text)
            }
            else -> buffer.append(text)
        }
        return buffer.toString()
    }

    private fun looksLikeReplacement(current: String, text: String): Boolean {
        if (text.contains('\n')) return true
        if (text.length >= 24 && !current.contains(text)) return true
        return false
    }
}
