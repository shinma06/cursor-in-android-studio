package com.cursoragent.service

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class AgentRunTest {
    @Test
    fun `completed new run does not allow restore while an older stopped process is alive`() {
        val project = Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            error("Unexpected project access: ${method.name}")
        } as Project
        val service = AgentProcessService(project)
        val first = service.prepareRun(object : AgentProcessListener {})
        var exited = false
        first.attachProcess({}) { exited }
        service.finishPreparation(first)
        first.stop()
        val second = service.prepareRun(object : AgentProcessListener {})
        second.complete(0)
        service.finishPreparation(second)
        assertTrue(service.isRestoreBlocked())
        exited = true
        first.complete(0)
        assertFalse(service.isRestoreBlocked())
        service.dispose()
    }

    @Test
    fun `stopped preparation rejects a late process and old callbacks cannot finish the next run`() {
        val received = mutableListOf<String>()
        fun run(name: String) = AgentRun(object : AgentProcessListener {
            override fun onAssistantDelta(text: String) {
                received += "$name:$text"
            }
            override fun onCompleted(exitCode: Int) {
                received += "$name:done"
            }
        })

        val old = run("A")
        old.stop()
        val next = run("B")
        var destroyed = 0
        var exited = false
        assertTrue(old.blocksRestore)
        old.attachProcess({ destroyed++ }) { exited }
        old.finishPreparation()
        assertTrue(old.blocksRestore)
        old.emit { it.onAssistantDelta("late") }
        exited = true
        old.complete(0)
        old.stop()
        assertFalse(old.isActive)
        assertFalse(old.blocksRestore)
        assertEquals(1, destroyed)
        assertEquals(emptyList<String>(), received)
        next.emit { it.onAssistantDelta("current") }
        next.complete(0)
        next.complete(0)
        assertEquals(listOf("B:current", "B:done"), received)

        val active = run("C")
        active.attachProcess({ destroyed++ }) { false }
        active.stop()
        active.stop()
        active.complete(1)
        assertEquals(2, destroyed)
        assertEquals(listOf("B:current", "B:done"), received)
    }
}
