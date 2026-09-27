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
            sink(123, Instant.EPOCH, null)
            sink(123, Instant.EPOCH, trace)
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
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwxr-xr-x"))
            sink(123, Instant.EPOCH, trace)
            assertEquals(2, Files.list(temp).use { it.count() }.toInt())
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rwx------"))
            val link = temp.resolve("alias")
            Files.createSymbolicLink(link, temp)
            System.setProperty(property, link.toString())
            AcpPrivateDiagnostics.forConversation(tab.toString(), conversation.toString())!!(123, Instant.EPOCH, trace)
            assertEquals(3, Files.list(temp).use { it.count() }.toInt())
            Files.delete(link)
            repeat(140) { sink(123, null, trace) }
            assertTrue(Files.list(temp).use { it.count() } <= 128)
            assertTrue(Files.list(temp).use { it.count() } > 2)
        } finally {
            if (old == null) System.clearProperty(property) else System.setProperty(property, old)
        }
    }
}
