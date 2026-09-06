package com.cursoragent.parser

import com.google.gson.JsonElement

/** Terminal result counters observed in the CLI fixture, not current context occupancy. */
data class TokenUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cacheReadTokens: Long?,
    val cacheWriteTokens: Long?,
) {
    companion object {
        fun parse(element: JsonElement?): TokenUsage? {
            if (element == null || !element.isJsonObject) return null
            val json = element.asJsonObject
            fun counter(name: String): Long? = runCatching {
                val value = json.get(name) ?: return null
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
                value.asBigDecimal.longValueExact().takeIf { it >= 0 }
            }.getOrNull()
            return TokenUsage(
                counter("inputTokens"), counter("outputTokens"),
                counter("cacheReadTokens"), counter("cacheWriteTokens"),
            ).takeIf { it.inputTokens != null || it.outputTokens != null || it.cacheReadTokens != null || it.cacheWriteTokens != null }
        }
    }
}
