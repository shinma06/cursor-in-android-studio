package com.cursoragent.ui.composer

import com.cursoragent.service.ModelListParser
import com.cursoragent.service.ModelOption
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModelFamiliesTest {
    private fun option(id: String, label: String) = ModelOption(id, label)

    @Test
    fun `group by version and retain exact executable IDs across option orderings`() {
        val options = listOf(
            option("claude-4.6-opus-high", "Claude Opus 4.6 1M"),
            option("claude-4.6-opus-high-thinking", "Claude Opus 4.6 1M Thinking"),
            option("claude-4.6-opus-max-thinking", "Claude Opus 4.6 1M Max Thinking"),
            option("claude-opus-5-thinking-high", "Claude Opus 5 1M Thinking"),
        )
        val families = modelFamilies(options)
        assertEquals(listOf("Claude Opus 4.6", "Claude Opus 5"), families.map { it.name })
        assertEquals(options.map { it.id }.toSet(), families.flatMap { it.variants }.map { it.option.id }.toSet())
        assertEquals("high", families.first().variants[1].effort)
        assertEquals("1M", families.first().variants[1].context)
        assertTrue(families.first().variants[1].thinking)
    }

    @Test
    fun `unsupported option combinations are not offered and cannot change other axes`() {
        val family = modelFamilies(listOf(
            option("model-high", "Model High"),
            option("model-thinking-high", "Model High Thinking"),
            option("model-thinking-high-fast", "Model High Thinking Fast"),
            option("model-thinking-max", "Model Max Thinking"),
        )).single()
        val high = family.variants[0]
        assertEquals(1, family.choices(high, ModelAxis.FAST).size)
        assertEquals(2, family.choices(high, ModelAxis.THINKING).size)
        val max = family.variants.last()
        assertEquals(1, family.choices(max, ModelAxis.THINKING).size)
        assertEquals(listOf("high", "max"), family.choices(max, ModelAxis.EFFORT).map { it.effort })
    }

    @Test
    fun `context needs two explicit capacities while missing context does not block Fast`() {
        val family = modelFamilies(listOf(
            option("model-high-300k", "Model 300K High"),
            option("model-high-1m", "Model 1M High"),
            option("model-high-fast", "Model High Fast"),
        )).single()
        assertEquals(setOf("300K", "1M"), family.choices(family.variants[0], ModelAxis.CONTEXT).map { it.context }.toSet())
        assertEquals(2, family.choices(family.variants[0], ModelAxis.FAST).size)
        val fast = family.variants.last()
        assertNull(fast.context)
        assertEquals(0, family.choices(fast, ModelAxis.CONTEXT).size)
    }

    @Test
    fun `default effort is not invented and unknown model suffixes remain separate`() {
        val families = modelFamilies(listOf(
            option("gpt-5.2", "GPT-5.2"), option("gpt-5.2-high", "GPT-5.2 High"),
            option("gpt-5.2-experimental", "GPT-5.2 Experimental"),
            option("auto", "Auto (current, default)"),
        ))
        assertEquals(2, families.size)
        assertNull(families[0].variants[0].effort)
        assertEquals("既定", optionValueLabel(ModelAxis.EFFORT, null))
        assertEquals("GPT-5.2 Experimental", families[1].name)
    }

    @Test
    fun `ambiguous aliases are kept separate rather than silently losing a choice`() {
        val families = modelFamilies(listOf(
            option("model-xhigh", "Model Extra High"),
            option("model-extra-high", "Model Extra High"),
        ))
        assertEquals(2, families.size)
        assertEquals(2, families.flatMap { it.variants }.size)
    }

    @Test
    fun `live catalog keeps every alias and combines real families without provider hardcoding`() {
        val raw = javaClass.getResource("/model-list-2026-09-06.txt")!!.readText()
        val options = ModelListParser.parse(raw)
        val families = modelFamilies(options)
        assertEquals(options.filterNot { it.id == "auto" }.map { it.id }.toSet(), families.flatMap { it.variants }.map { it.option.id }.toSet())
        val opus = families.single { it.id == "claude-opus-5" }
        assertEquals("Claude Opus 5", opus.name)
        assertTrue(opus.variants.size > 10)
        val current = opus.variants.first { it.option.id == "claude-opus-5-thinking-high" }
        assertEquals(2, opus.choices(current, ModelAxis.THINKING).size)
        assertEquals(2, opus.choices(current, ModelAxis.FAST).size)
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), opus.choices(current, ModelAxis.EFFORT).map { it.effort })
        assertEquals(1, opus.choices(current, ModelAxis.CONTEXT).size)
        assertEquals(1, families.count { it.id == "gpt-5.5" })
        assertEquals(1, families.count { it.id == "composer-2.5" })
    }
}
