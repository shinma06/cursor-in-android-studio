package com.cursoragent.service

/**
 * Parses `agent mcp list` output. Format observed on `2026.09.02-c22c1a3` (2026-09-04):
 * ```
 * gitlab: ready
 * github: ready
 * context7: ready
 * ```
 */
data class McpServerEntry(
    val id: String,
    val status: String,
)

object McpListParser {
    private val linePattern = Regex("^([^:]+):\\s*(.+)$")

    fun parse(raw: String): List<McpServerEntry> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val match = linePattern.matchEntire(line) ?: return@mapNotNull null
                McpServerEntry(id = match.groupValues[1].trim(), status = match.groupValues[2].trim())
            }
            .toList()

    fun format(entries: List<McpServerEntry>): String {
        if (entries.isEmpty()) return "(no MCP servers listed)"
        val idWidth = entries.maxOf { it.id.length }.coerceAtLeast(2)
        return buildString {
            appendLine(String.format("%-${idWidth}s  %s", "ID", "Status"))
            entries.forEach { entry ->
                appendLine(String.format("%-${idWidth}s  %s", entry.id, entry.status))
            }
        }.trimEnd()
    }
}
