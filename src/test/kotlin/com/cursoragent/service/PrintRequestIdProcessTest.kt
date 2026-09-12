package com.cursoragent.service

import com.cursoragent.parser.PrintAssistantText
import com.cursoragent.settings.*
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PrintRequestIdProcessTest {
    @TempDir lateinit var directory: Path

    @Test fun `real print process with final unterminated JSON confirms only after physical exit`() {
        exercise("success")
    }

    @Test fun `actual abnormal exit stop and missing ID never report print diagnostics`() {
        for (ending in listOf("abnormal", "stop", "missing", "error")) exercise(ending)
    }

    private fun exercise(ending: String) {
        val root = Files.createDirectory(directory.resolve(ending))
        val script = root.resolve("fake-agent")
        val field = if (ending == "missing") "" else ",\"request_id\":\"Synthetic-Opaque\""
        Files.writeString(script, """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
                printf '%s\n' '${PrintAssistantText.VERIFIED_VERSION}'
                exit 0
            fi
            printf '%s\n' '{"type":"system","subtype":"init","session_id":"synthetic-session"}'
            printf '%s' '{"type":"result","subtype":"success","session_id":"synthetic-session","is_error":${ending == "error"},"result":"answer"$field}'
            touch result-written
            while [ ! -f release ]; do sleep 0.05; done
            exit ${if (ending == "abnormal") 7 else 0}
        """.trimIndent())
        assertTrue(script.toFile().setExecutable(true))
        val project = Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> root.toString()
                "isDisposed" -> false
                else -> error("Unexpected Project call: ${method.name}")
            }
        } as Project
        val service = AgentProcessService(project)
        val events = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        val listener = object : AgentProcessListener {
            override fun onPrintCompleted(requestId: PrintRequestId) {
                events += "${requestId.value}:${requestId.sessionId}"
                done.countDown()
            }
            override fun onCompleted(exitCode: Int) { events += "exit:$exitCode"; done.countDown() }
            override fun onStopped() { events += "stopped"; done.countDown() }
            override fun onError(message: String) { events += "error"; done.countDown() }
        }
        val settings = TurnSettings(script.toString(), "", AgentMode.ASK, PermissionMode.ASK_EVERY_TIME, SandboxMode.DEFAULT)
        val turn = service.prepareTurn(service.captureWorkspace(null, WorktreeMode.DEFAULT), settings) { listener }!!
        try {
            service.sendPrompt("synthetic prompt", turn)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!Files.exists(root.resolve("result-written")) && events.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(Files.exists(root.resolve("result-written")), events.toString())
            assertTrue(events.isEmpty(), "Output does not substitute for process termination")
            if (ending == "stop") turn.run.stop()
            Files.createFile(root.resolve("release"))
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(listOf(when (ending) {
                "success" -> "Synthetic-Opaque:synthetic-session"
                "stop" -> "stopped"
                "abnormal" -> "exit:7"
                "error" -> "error"
                else -> "exit:0"
            }), events)
        } finally {
            Files.writeString(root.resolve("release"), "")
            turn.run.stop()
            turn.preparation.close()
            service.dispose()
        }
    }
}
