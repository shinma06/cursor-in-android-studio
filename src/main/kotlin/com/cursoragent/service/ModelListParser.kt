package com.cursoragent.service

/**
 * Parses `agent --list-models` output. Format observed on `2026.09.02-c22c1a3`
 * (requirements doc §13):
 * ```
 * Available models
 *
 * auto - Auto (current, default)
 * gpt-5.3-codex-low - Codex 5.3 Low
 * ...
 *
 * Tip: use --model <id> ...
 * ```
 * i.e. one `<id> - <label>` pair per line, bracketed by a header line and a
 * trailing "Tip:" line to ignore.
 */
object ModelListParser {
    fun parse(raw: String): List<ModelOption> {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "Available models" && !it.startsWith("Tip:") }
            .mapNotNull { line ->
                val separator = line.indexOf(" - ")
                if (separator <= 0) return@mapNotNull null
                ModelOption(id = line.substring(0, separator).trim(), label = line.substring(separator + 3).trim())
            }
            .toList()
    }
}
