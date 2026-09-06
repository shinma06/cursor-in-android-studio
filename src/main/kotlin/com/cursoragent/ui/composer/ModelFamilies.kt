package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption

internal enum class ModelAxis(val caption: String, val help: String) {
    THINKING("Thinking", "回答前に考えるモードを切り替えます。"),
    FAST("Fast", "高速版に切り替えます。利用量や料金が変わる場合があります。"),
    CONTEXT("Context", "一度に扱える情報量を切り替えます。"),
    EFFORT("Effort", "回答にかける思考の強さを切り替えます。"),
}

internal data class ModelVariant(
    val option: ModelOption,
    val thinking: Boolean,
    val fast: Boolean,
    val context: String?,
    val effort: String?,
) {
    fun value(axis: ModelAxis): String? = when (axis) {
        ModelAxis.THINKING -> thinking.toString()
        ModelAxis.FAST -> fast.toString()
        ModelAxis.CONTEXT -> context
        ModelAxis.EFFORT -> effort
    }
}

internal data class ModelFamily(val id: String, val name: String, val variants: List<ModelVariant>) {
    /** Only offer transitions that leave all other known option values unchanged. */
    fun choices(current: ModelVariant, axis: ModelAxis): List<ModelVariant> {
        val compatible = variants.filter { candidate ->
            ModelAxis.entries.filterNot { it == axis }.all { other ->
                // Missing context text is unknown, not a claim about the default capacity.
                other == ModelAxis.CONTEXT && (candidate.context == null || current.context == null) ||
                    candidate.value(other) == current.value(other)
            }
        }
        val choices = compatible.distinctBy { it.value(axis) }
        // An absent capacity must never be presented as a made-up context option.
        return (if (axis == ModelAxis.CONTEXT) choices.filter { it.context != null } else choices)
            .sortedBy { variant ->
                if (axis == ModelAxis.EFFORT) EFFORT_ORDER.indexOf(variant.effort).let { if (it < 0) 99 else it }
                else if (variant.value(axis) == "false") 0 else 1
            }
    }

    fun selectionLabel(variant: ModelVariant): String {
        if (variants.size == 1) return variant.option.displayName()
        return buildList {
            add(name)
            variant.effort?.let { add(optionValueLabel(ModelAxis.EFFORT, it)) }
            if (variant.thinking && variant.effort == null) add("Thinking")
            if (variant.fast) add("Fast")
            if (variants.mapNotNull { it.context }.distinct().size > 1) variant.context?.let(::add)
        }.joinToString(" ")
    }

    fun matches(query: String): Boolean = name.contains(query, true) || variants.any {
        it.option.label.contains(query, true) || it.option.id.contains(query, true)
    }
}

private val EFFORT_ORDER = listOf(null, "none", "minimal", "low", "medium", "high", "xhigh", "max")

internal fun optionValueLabel(axis: ModelAxis, value: String?): String = when (axis) {
    ModelAxis.EFFORT -> when (value) {
        null -> "既定"
        "none" -> "None"
        "xhigh" -> "Extra High"
        else -> value.replaceFirstChar { it.uppercase() }
    }
    ModelAxis.CONTEXT -> value?.uppercase() ?: "不明"
    else -> if (value == "true") "オン" else "オフ"
}

/**
 * CLI aliases are opaque execution IDs. Recognize only trailing option tokens, retain the
 * original ID, and keep unknown suffixes/model versions separate. No provider catalog is invented.
 */
internal fun modelFamilies(options: List<ModelOption>): List<ModelFamily> {
    data class Parsed(val baseId: String, val name: String, val variant: ModelVariant)
    val suffix = Regex("-(extra-high|xhigh|none|minimal|low|medium|high|max|thinking|fast|[0-9]+(?:\\.[0-9]+)?[km])$", RegexOption.IGNORE_CASE)
    val contextLabel = Regex("(?:^|\\s)([0-9]+(?:\\.[0-9]+)?[KM])(?=\\s|$)", RegexOption.IGNORE_CASE)
    val parsed = options.filterNot { it.id == "auto" }.map { option ->
        var base = option.id
        var thinking = false
        var fast = false
        var effort: String? = null
        var context: String? = null
        while (true) {
            val match = suffix.find(base) ?: break
            val token = match.groupValues[1].lowercase()
            when {
                token == "thinking" -> thinking = true
                token == "fast" -> fast = true
                token.first().isDigit() -> context = token.uppercase()
                else -> effort = if (token == "extra-high") "xhigh" else token
            }
            base = base.substring(0, match.range.first)
        }
        val label = option.displayName()
        context = context ?: contextLabel.find(label)?.groupValues?.get(1)?.uppercase()
        var name = contextLabel.replace(label, " ")
        // Remove only tokens corroborated by the alias; retain qualifiers such as (NO ZDR).
        if (thinking) name = name.replace(Regex("\\bThinking\\b", RegexOption.IGNORE_CASE), "")
        if (fast) name = name.replace(Regex("\\bFast\\b", RegexOption.IGNORE_CASE), "")
        if (effort != null) name = name.replace(Regex("\\b${if (effort == "xhigh") "Extra High" else effort}\\b", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("\\s+"), " ").trim()
        Parsed(base, name.ifEmpty { label }, ModelVariant(option, thinking, fast, context, effort))
    }
    return parsed.groupBy { it.baseId }.flatMap { (id, entries) ->
        val variants = entries.map { it.variant }
        val ambiguous = entries.map { it.name }.distinct().size != 1 ||
            variants.map { ModelAxis.entries.map(it::value) }.distinct().size != variants.size
        if (ambiguous) entries.map { ModelFamily(it.variant.option.id, it.variant.option.displayName(), listOf(it.variant)) }
        else listOf(ModelFamily(id, entries.first().name, variants))
    }
}
