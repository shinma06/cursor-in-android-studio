package com.cursoragent.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PluginDiagnosticsTest {
    @TempDir
    lateinit var directory: Path

    private fun report(
        commit: String? = "a".repeat(40),
        state: String? = "clean",
        manual: String = "",
        detected: String? = null,
    ) = pluginDiagnosticsText("0.1.0-SNAPSHOT", commit, state, "AI-261.example", manual, detected)

    @Test
    fun `only clean embedded identity claims an exact commit`() {
        assertTrue(report().contains("ビルドID: ${"a".repeat(40)}"))
        for ((commit, state) in listOf("a".repeat(40) to "dirty", null to "clean", "bad" to "clean", "a".repeat(40) to null)) {
            assertTrue(report(commit, state).contains("ビルドID: 未特定"))
            assertFalse(report(commit, state).contains("ビルドID: ${"a".repeat(40)}"))
        }
        val packaged = readBuildIdentity {
            PluginDiagnosticsTest::class.java.getResourceAsStream("/cursor-agent-build.properties")
        }
        assertTrue(packaged.getProperty("source.state") in setOf("clean", "dirty", "unknown"))
    }

    @Test
    fun `identity is published only after complete parsing and closes its resource`() {
        val valid = "source.commit=${"a".repeat(40)}\nsource.state=clean\n"
        for (content in listOf(valid, valid + "broken=\\uQQQQ\n")) {
            var closed = false
            val identity = readBuildIdentity {
                object : ByteArrayInputStream(content.toByteArray()) {
                    override fun close() { closed = true; super.close() }
                }
            }
            assertTrue(closed)
            if (content == valid) {
                assertEquals("a".repeat(40), identity.getProperty("source.commit"))
                assertEquals("clean", identity.getProperty("source.state"))
            } else {
                assertTrue(identity.isEmpty())
                val text = report(identity.getProperty("source.commit"), identity.getProperty("source.state"))
                assertTrue(text.contains("ビルドID: 未特定"))
            }
        }
        assertTrue(readBuildIdentity { null }.isEmpty())
        assertTrue(readBuildIdentity { throw IOException("unreadable") }.isEmpty())
        assertTrue(readBuildIdentity {
            object : ByteArrayInputStream(valid.toByteArray()) {
                override fun close() { throw IOException("close failed") }
            }
        }.isEmpty())
    }

    @Test
    fun `diagnostics checks candidates without executing CLI and preserves unknown fallback`() {
        val marker = directory.resolve("executed").toFile()
        val executable = directory.resolve("agent").toFile().apply {
            writeText("#!/bin/sh\ntouch '${marker.absolutePath}'\n")
            assertTrue(setExecutable(true))
        }
        val automatic = report(detected = executable.absolutePath)
        assertTrue(automatic.contains("自動探索（空欄設定）"))
        assertTrue(automatic.contains("${executable.absolutePath}（ファイル確認のみ。CLI起動は未確認）"))
        val manual = report(manual = executable.absolutePath, detected = "/unused/agent")
        assertTrue(manual.contains("CLI設定: 手動指定:"))
        assertFalse(manual.contains("/unused/agent"))
        val invalidManual = report(manual = directory.resolve("missing").toString(), detected = executable.absolutePath)
        assertTrue(invalidManual.contains("手動指定を利用できません"))
        assertTrue(invalidManual.contains("CLI解決候補: 未解決:"))
        assertFalse(invalidManual.contains("CLI解決候補: ${executable.absolutePath}"))
        assertTrue(report().contains("未解決（実行時にPATH"))
        assertTrue(report(detected = directory.toString()).contains("実行可能ファイルを確認できません"))
        assertTrue(report(detected = "relative/agent").contains("相対パス"))
        assertTrue(manual.contains("CLI版・認証状態: 未取得（CLI未実行）"))
        assertFalse(marker.exists())
    }

    @Test
    fun `paths cannot inject extra diagnostic rows or hidden formatting`() {
        val text = report(manual = "/missing/agent\nInjected: value\r\u001b\u202e")
        assertFalse(text.contains("\nInjected:"))
        assertFalse(text.contains('\r'))
        assertFalse(text.contains('\u001b'))
        assertFalse(text.contains('\u202e'))
        assertEquals(7, text.lines().size)
    }

    @Test
    fun `copy is explicit and copies visible snapshot without another read`() {
        var reads = 0
        val copied = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            val panel = PluginDiagnosticsPanel({ "snapshot-${++reads}" }, copied::add)
            val buttons = panel.components.filterIsInstance<JPanel>().single().components.filterIsInstance<JButton>()
            assertEquals(1, reads)
            assertTrue(copied.isEmpty())
            buttons.last().doClick(0)
            assertEquals(listOf("snapshot-1"), copied)
            assertEquals(1, reads)
            buttons.first().doClick(0)
            assertEquals(2, reads)
            buttons.last().doClick(0)
            assertEquals(listOf("snapshot-1", "snapshot-2"), copied)
        }
    }
}
