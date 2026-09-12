package com.cursoragent.service

import com.intellij.execution.configurations.GeneralCommandLine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread

class PrintVersionProbeTest {
    @TempDir lateinit var directory: Path

    private fun command(body: String): GeneralCommandLine {
        val executable = directory.resolve("agent")
        Files.writeString(executable, "#!/bin/sh\n$body\n")
        check(executable.toFile().setExecutable(true))
        return GeneralCommandLine(executable.toString(), "-p", "private prompt")
            .withWorkDirectory(directory.toFile()).withEnvironment("PROBE_MARKER", "expected")
    }

    @Test fun `probe uses selected executable directory environment and only version argument`() {
        val command = command("""
            test "${'$'}#" = 1 && test "${'$'}1" = --version || exit 1
            test "${'$'}PROBE_MARKER" = expected || exit 2
            test "${'$'}(pwd -P)" = "${directory.toRealPath()}" || exit 3
            printf '2026.09.10-fd3934a\n'
        """.trimIndent())
        val run = AgentRun(object : AgentProcessListener {})
        assertEquals("2026.09.10-fd3934a", probePrintVersion(command, run))
        assertTrue(run.isActive)
    }

    @Test fun `failed probe stays unknown and stopped run never starts probe`() {
        val command = command("exit 9")
        val run = AgentRun(object : AgentProcessListener {})
        assertNull(probePrintVersion(command, run))
        run.stop()
        assertNull(probePrintVersion(command("touch started"), run))
        assertFalse(Files.exists(directory.resolve("started")))
    }

    @Test fun `stop destroys running probe and timeout returns unknown`() {
        val command = command("echo ${'$'}${'$'} > pid\nexec sleep 30")
        val run = AgentRun(object : AgentProcessListener {})
        val result = FutureTask { probePrintVersion(command, run) }
        val worker = thread { result.run() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!Files.exists(directory.resolve("pid")) && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(Files.exists(directory.resolve("pid")))
        val pid = Files.readString(directory.resolve("pid")).trim().toLong()
        run.stop()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertNull(result.get(1, TimeUnit.SECONDS))
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        val timed = AgentRun(object : AgentProcessListener {})
        val started = System.nanoTime()
        assertNull(probePrintVersion(command, timed))
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(10))
        val timedPid = Files.readString(directory.resolve("pid")).trim().toLong()
        assertFalse(ProcessHandle.of(timedPid).map { it.isAlive }.orElse(false))
    }
}
