package com.cursoragent.parser

/** Normalizes one print turn once, before both display and persistence receive full replacements. */
class PrintAssistantText(version: String?, partialOutput: Boolean) {
    private enum class Mode { DELTAS, COMPLETE_MESSAGES, LEGACY }
    private var mode = if (version == VERIFIED_VERSION) {
        if (partialOutput) Mode.DELTAS else Mode.COMPLETE_MESSAGES
    } else Mode.LEGACY
    private val legacy = AssistantChunkDeduper()
    private val text = StringBuilder()

    fun accept(event: StreamEvent.AssistantDelta): String? {
        if (event.text.isEmpty()) return null
        if (mode == Mode.DELTAS && event.kind == PrintAssistantKind.UNRECOGNIZED) {
            // One-way compatibility retreat; do not alternate strategies for individual events.
            legacy.dedupe(text.toString())
            mode = Mode.LEGACY
        }
        return when (mode) {
            Mode.LEGACY -> legacy.dedupe(event.text)
            Mode.COMPLETE_MESSAGES -> text.append(event.text).toString()
            Mode.DELTAS -> when (event.kind) {
                PrintAssistantKind.DELTA -> text.append(event.text).toString()
                else -> null
            }
        }
    }

    companion object {
        // Exact controlled producer from #116; newer versions are not implicitly certified.
        const val VERIFIED_VERSION = "2026.09.10-fd3934a"
    }
}

enum class PrintAssistantKind { DELTA, TOOL_FLUSH, FINAL_FLUSH, UNRECOGNIZED }
