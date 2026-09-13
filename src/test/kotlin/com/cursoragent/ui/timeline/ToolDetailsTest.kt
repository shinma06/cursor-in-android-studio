package com.cursoragent.ui.timeline

import com.cursoragent.parser.ParsedToolCall
import com.cursoragent.parser.ShellResultDetails
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ToolDetailsTest {
    private fun descendants(value: Component): List<Component> = listOf(value) +
        if (value is Container) value.components.flatMap(::descendants) else emptyList()

    @Test fun `long shell output folds without losing bytes and repeated completion keeps one expanded card`() = SwingUtilities.invokeAndWait {
        val output = "  <html>literal\n" + "long output\n".repeat(200) + "last  "
        val timeline = ChatTimelinePanel()
        timeline.addUserMessage("first turn")
        val shell = ParsedToolCall("same", "completed", "shell", "synthetic command",
            shellResult = ShellResultDetails("synthetic", 7, output, "", null))
        timeline.addShellResultCard(shell)
        var card = descendants(timeline).filterIsInstance<ToolCallBubble>().single()
        assertFalse(card.expanded)
        val toggle = descendants(card).filterIsInstance<JButton>().single()
        toggle.doClick(0)
        assertTrue(card.expanded)
        assertTrue(descendants(card).filterIsInstance<JTextArea>().single().text.endsWith(output))
        timeline.addShellResultCard(shell)
        timeline.addToolCallStarted(shell.copy(subtype = "started"))
        card = descendants(timeline).filterIsInstance<ToolCallBubble>().single()
        assertTrue(card.expanded)
        assertTrue(descendants(card).filterIsInstance<JTextArea>().single().text.endsWith(output))
        descendants(card).filterIsInstance<JButton>().single().doClick(0)
        assertFalse(card.expanded)
        timeline.addUserMessage("next turn")
        timeline.addShellResultCard(shell.copy(shellResult = shell.shellResult!!.copy(exitCode = 0, stdout = "")))
        val next = descendants(timeline).filterIsInstance<ToolCallBubble>().last()
        assertEquals(2, descendants(timeline).filterIsInstance<ToolCallBubble>().size)
        assertFalse(next.expanded)
        assertTrue(descendants(next).filterIsInstance<JTextArea>().single().text.endsWith("出力はありません"))
    }
}
