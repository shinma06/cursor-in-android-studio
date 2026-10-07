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

    @Test fun `nested task details keep collapsed state across updates content replacement and parent finish`() = SwingUtilities.invokeAndWait {
        val timeline = ChatTimelinePanel()
        val tool = com.cursoragent.service.AgentTool("task", "Task", "other", "in_progress",
            content = listOf(com.cursoragent.service.AgentToolContent.Text("first")),
            task = com.cursoragent.service.AgentTask(name = "synthetic"))
        timeline.upsertTask(tool, "parent")
        val card = descendants(timeline).filterIsInstance<TaskToolCard>().single()
        val outer = descendants(card).filterIsInstance<JButton>().single { it.text == "詳細を表示" }
        outer.doClick(0)
        fun inner() = descendants(card).filterIsInstance<StructuredToolCard>().single()
        assertTrue(inner().expanded)
        descendants(inner()).filterIsInstance<JButton>().single().doClick(0)
        assertFalse(inner().expanded)
        timeline.upsertTask(tool.copy(content = listOf(com.cursoragent.service.AgentToolContent.Text("updated"))), "parent")
        assertFalse(inner().expanded)
        timeline.upsertTask(tool.copy(content = emptyList()), "parent")
        assertTrue(descendants(card).filterIsInstance<StructuredToolCard>().isEmpty())
        timeline.upsertTask(tool, "parent")
        assertFalse(inner().expanded)
        timeline.finishTasks()
        assertFalse(inner().expanded)
        assertEquals("詳細を閉じる", outer.text)
        assertSame(card, descendants(timeline).filterIsInstance<TaskToolCard>().single())
    }

}
