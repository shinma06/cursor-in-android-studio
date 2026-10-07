package com.cursoragent.service

import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AgentMetadataCommandTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `metadata runner keeps exact argv workspace and exit failure contract`() {
        val executable = temp.resolve("fake metadata")
        executable.writeText("#!/bin/sh\nprintf '%s\\n' \"\$PWD\" \"\$@\"\n")
        assertTrue(executable.toFile().setExecutable(true))
        assertEquals(executable.toString(), resolveAgentExecutable(executable.toString()))
        val output = runAgentMetadataCommand(executable.toString(), temp.toString(), 5_000, "--list-models", "value with spaces")
        assertEquals(listOf(temp.toRealPath().toString(), "--list-models", "value with spaces"), output!!.trimEnd().lines())
        executable.writeText("#!/bin/sh\nprintf 'partial - output\\n'\nexit 7\n")
        assertNull(runAgentMetadataCommand(executable.toString(), temp.toString(), 5_000, "--list-models"))
    }
}
