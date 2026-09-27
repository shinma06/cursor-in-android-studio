package com.cursoragent.acp

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID

class AcpPrivateDiagnosticsTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `private correlation is opt in permission restricted bounded and contains only identifiers`() {
        val property = AcpPrivateDiagnostics.DIRECTORY_PROPERTY
        val old = System.getProperty(property)
        val tab = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val trace = UUID.randomUUID()
        try {
            System.clearProperty(property)
            assertNull(AcpPrivateDiagnostics.forConversation(tab.toString(), conversation.toString()))
            System.setProperty(property, temp.toString())
            assertNull(AcpPrivateDiagnostics.forConversation("private prompt", conversation.toString()))
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwx------"))
            val sink = AcpPrivateDiagnostics.forConversation(tab.toString(), conversation.toString())!!
            sink(123, Instant.EPOCH, null, null)
            sink(123, Instant.EPOCH, trace, null)
            val files = Files.list(temp).use { it.toList() }
            assertEquals(2, files.size)
            val records = files.map {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(it))
                JsonParser.parseString(Files.readString(it)).asJsonObject
            }
            records.forEach {
                assertEquals(setOf("at", "event", "tab", "conversation", "pid", "processStartedAt", "trace"), it.keySet())
                assertEquals(tab.toString(), it["tab"].asString)
                assertEquals(conversation.toString(), it["conversation"].asString)
                assertEquals(123L, it["pid"].asLong)
                assertEquals(Instant.EPOCH.toString(), it["processStartedAt"].asString)
            }
            assertEquals(setOf("process-started", "prompt-dispatch"), records.map { it["event"].asString }.toSet())
            assertEquals(trace.toString(), records.single { !it["trace"].isJsonNull }["trace"].asString)
            val failure = AcpPrivateDiagnostics.Failure(AcpPrivateDiagnostics.Site.PROCESS_SAMPLE,
                AcpPrivateDiagnostics.Category.ACP, AcpPrivateDiagnostics.ResultStage.NOT_RECEIVED, false,
                listOf(AcpProcessTree.DiagnosticChild(456, Instant.EPOCH, null, null, null)))
            sink(123, Instant.EPOCH, trace, failure)
            val failureFile = Files.list(temp).use { it.toList() }.single { it !in files }
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(failureFile))
            val detail = JsonParser.parseString(Files.readString(failureFile)).asJsonObject
            assertEquals(records.first().keySet() + setOf("site", "category", "resultStage", "terminal", "children"), detail.keySet())
            assertEquals("first-failure", detail["event"].asString)
            assertEquals("PROCESS_SAMPLE", detail["site"].asString)
            assertEquals("ACP", detail["category"].asString)
            assertEquals("NOT_RECEIVED", detail["resultStage"].asString)
            assertEquals(trace.toString(), detail["trace"].asString)
            val child = detail.getAsJsonArray("children").single().asJsonObject
            assertEquals(setOf("pid", "processStartedAt", "alive", "parentPid", "parentStartedAt"), child.keySet())
            assertTrue(child["alive"].isJsonNull)
            assertTrue(child["parentPid"].isJsonNull)
            assertTrue(child["parentStartedAt"].isJsonNull)
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwxr-xr-x"))
            sink(123, Instant.EPOCH, trace, null)
            assertEquals(3, Files.list(temp).use { it.count() }.toInt())
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwx------"))
            val link = temp.resolve("alias")
            Files.createSymbolicLink(link, temp)
            System.setProperty(property, link.toString())
            AcpPrivateDiagnostics.forConversation(tab.toString(), conversation.toString())!!(123, Instant.EPOCH, trace, null)
            assertEquals(4, Files.list(temp).use { it.count() }.toInt())
            Files.delete(link)
            repeat(140) { sink(123, null, trace, null) }
            assertTrue(Files.list(temp).use { it.count() } <= 128)
            assertTrue(Files.list(temp).use { it.count() } > 2)
        } finally {
            if (old == null) System.clearProperty(property) else System.setProperty(property, old)
        }
    }
}
