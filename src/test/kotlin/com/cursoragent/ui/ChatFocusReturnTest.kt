package com.cursoragent.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ChatFocusReturnTest {
    @Test
    fun `verified Terminal origin survives chat controls and is consumed by the next focus return`() = SwingUtilities.invokeAndWait {
        val terminal = JPanel()
        val input = JButton().also(terminal::add)
        val editor = JButton()
        assertEquals(ChatFocusOrigin.TERMINAL, chatFocusOrigin(input, terminal, true))
        assertEquals(ChatFocusOrigin.EDITOR, chatFocusOrigin(editor, terminal, true))
        assertNull(chatFocusOrigin(JButton(), terminal, false))
        assertNull(chatFocusOrigin(null, terminal, true))

        val fixture = TerminalFixture()
        var editorCalls = 0
        var reads = 0
        val focus = ChatFocusReturn(fixture.project, { reads++; fixture.window }, { editorCalls++ })
        focus.restore()
        assertEquals(1, editorCalls)
        assertEquals(0, reads, "ordinary editor return need not inspect or initialize Terminal")
        focus.remember(ChatFocusOrigin.TERMINAL)
        focus.remember(null) // Navigation within the chat is not a new workspace origin.
        focus.restore()
        assertEquals(1, fixture.activations)
        assertEquals(1, reads)
        assertEquals(1, editorCalls)
        focus.restore()
        assertEquals(2, editorCalls)
        assertEquals(1, reads, "one Terminal origin must not persist after returning")
        focus.remember(ChatFocusOrigin.TERMINAL)
        focus.remember(ChatFocusOrigin.EDITOR)
        focus.restore()
        assertEquals(3, editorCalls, "entering from the editor replaces an older Terminal origin")
        assertEquals(1, fixture.activations)
    }

    @Test
    fun `closed missing foreign or disposed Terminal falls back without creating content and disposed projects do nothing`() = SwingUtilities.invokeAndWait {
        val fixture = TerminalFixture()
        val foreign = TerminalFixture()
        var window: ToolWindow? = fixture.window
        var editorCalls = 0
        val focus = ChatFocusReturn(fixture.project, { window }, { editorCalls++ })
        for (invalidate in listOf<() -> Unit>(
            { window = null },
            { window = foreign.window },
            { fixture.disposed = true },
            { fixture.available = false },
            { fixture.contentCreated = false },
            { fixture.contentCount = 0 },
        )) {
            window = fixture.window
            fixture.disposed = false
            fixture.available = true
            fixture.contentCreated = true
            fixture.contentCount = 1
            focus.remember(ChatFocusOrigin.TERMINAL)
            invalidate()
            focus.restore()
        }
        assertEquals(6, editorCalls)
        assertEquals(0, fixture.activations)
        assertEquals(0, foreign.activations)
        fixture.projectDisposed = true
        focus.remember(ChatFocusOrigin.TERMINAL)
        focus.restore()
        assertEquals(6, editorCalls)
        assertEquals(0, fixture.activations)
    }

    private class TerminalFixture {
        var projectDisposed = false
        var disposed = false
        var available = true
        var contentCreated = true
        var contentCount = 1
        var activations = 0
        val project = Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            when (method.name) {
                "isDisposed" -> projectDisposed
                else -> error("Unexpected Project call: ${method.name}")
            }
        } as Project
        private val contents = Proxy.newProxyInstance(ContentManager::class.java.classLoader, arrayOf(ContentManager::class.java)) { _, method, _ ->
            when (method.name) {
                "getContentCount" -> contentCount
                else -> error("Unexpected ContentManager call: ${method.name}")
            }
        } as ContentManager
        val window = Proxy.newProxyInstance(ToolWindow::class.java.classLoader, arrayOf(ToolWindow::class.java)) { _, method, args ->
            when (method.name) {
                "getProject" -> project
                "isDisposed" -> disposed
                "isAvailable" -> available
                "getContentManagerIfCreated" -> if (contentCreated) contents else null
                "activate" -> { assertNull(args!![0]); assertEquals(false, args[1]); activations++; null }
                else -> error("Unexpected ToolWindow call: ${method.name}")
            }
        } as ToolWindow
    }
}
