package com.cursoragent.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ModelListParserTest {
    @Test
    fun `command failure differs from successful empty catalog`() {
        assertEquals(ModelCatalogState.Failed, modelCatalogResult(null))
        assertEquals(ModelCatalogState.Loaded(emptyList()), modelCatalogResult("Available models\n"))
        assertEquals(ModelCatalogState.Loaded(listOf(ModelOption("auto", "Auto"))), modelCatalogResult("auto - Auto"))
    }

    @Test
    fun `parses a hand-written sample`() {
        val raw = """
            Available models

            auto - Auto (current, default)
            gpt-5.3-codex-low - Codex 5.3 Low

            Tip: use --model <id> to switch.
        """.trimIndent()

        val models = ModelListParser.parse(raw)

        assertEquals(
            listOf(ModelOption("auto", "Auto (current, default)"), ModelOption("gpt-5.3-codex-low", "Codex 5.3 Low")),
            models,
        )
    }

    @Test
    fun `parses the real agent --list-models fixture`() {
        val fixture = File("src/test/resources/cli-output-fixtures/list-models.txt").readText()

        val models = ModelListParser.parse(fixture)

        assertTrue(models.isNotEmpty())
        assertTrue(models.any { it.id == "auto" })
        assertTrue(models.none { it.label.isEmpty() })
        assertTrue(models.none { it.id.contains(" - ") })
    }
}
