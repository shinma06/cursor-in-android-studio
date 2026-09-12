package com.cursoragent.ui.timeline

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.history.ConversationStore
import com.cursoragent.history.newHistoryId
import com.cursoragent.service.AgentTask
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import com.cursoragent.ui.taskSavedSummary
import java.awt.Component
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TaskToolCardTest {
    private fun tool(status: String? = "in_progress", details: AgentTask = AgentTask(name = "合成子")) =
        AgentTool("call", "Task", "other", status, task = details)
    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap(::descendants) else emptyList()
    private fun cards(timeline: ChatTimelinePanel) = descendants(timeline).filterIsInstance<TaskToolCard>()
    private fun text(component: Component) = descendants(component).filterIsInstance<JTextArea>().joinToString("\n") { it.text }

    @Test fun `same task updates one card preserves expansion and literal output without hyperlinks`() = SwingUtilities.invokeAndWait {
        val timeline = ChatTimelinePanel()
        timeline.upsertTask(tool(), "parent")
        val card = cards(timeline).single()
        val toggle = descendants(card).filterIsInstance<JButton>().single()
        toggle.doClick(0)
        val result = "<img src='https://example.invalid/private'> ![x](file:///synthetic)\n日本語"
        repeat(3) { timeline.upsertTask(tool("completed", AgentTask(name = "合成子", resultText = result)), "parent") }
        assertSame(card, cards(timeline).single())
        assertEquals("詳細を閉じる", toggle.text)
        assertTrue(text(card).contains(result))
        assertFalse(descendants(card).any { it is javax.swing.JEditorPane })
        timeline.upsertTask(tool("in_progress"), "parent")
        assertEquals("completed", card.tool.status)
        assertEquals(result, card.tool.task!!.resultText)
        card.setSize(240, 800)
        card.doLayout()
        assertTrue(toggle.parent.width <= card.width)
        toggle.doClick(0)
        assertEquals("詳細を表示", toggle.text)
    }

    @Test fun `parent IDs and turn boundaries separate cards and parent stop cannot imply child success`() = SwingUtilities.invokeAndWait {
        val timeline = ChatTimelinePanel()
        timeline.upsertTask(tool(), "a")
        timeline.upsertTask(tool(), "b")
        assertEquals(2, cards(timeline).size)
        assertEquals(2, timeline.finishTasks().size)
        assertTrue(cards(timeline).all { text(it).contains("終了を確認できません") })
        timeline.addUserMessage("次のターン")
        timeline.upsertTask(tool(), "a")
        assertEquals(3, cards(timeline).size)
        assertEquals(1, timeline.finishTasks().size)
        val background = tool("completed", AgentTask(isBackground = true, durationMs = 0))
        timeline.upsertTask(background, "background")
        assertTrue(taskSavedSummary(background).contains("終了は未確認"))
        assertTrue(text(cards(timeline).last()).contains("0 ms"))
        assertTrue(text(cards(timeline).last()).contains("子のusage: 未取得"))
    }

    @Test fun `known standard row becomes one task row and retains generic content and diff action`() = SwingUtilities.invokeAndWait {
        val timeline = ChatTimelinePanel()
        val diff = AgentToolContent.Diff("synthetic.txt", "before", "after")
        val generic = AgentTool("call", "existing", "other", "completed", listOf(AgentToolContent.Text("provided tool content"), diff))
        var opened: AgentToolContent.Diff? = null
        timeline.upsertStructuredTool(generic) { opened = it }
        timeline.upsertStructuredTool(generic.copy(task = AgentTask(reportedAgentId = "reported"))) { opened = it }
        val card = cards(timeline).single()
        assertTrue(text(card).contains("provided tool content"))
        assertTrue(text(card).contains("cursor/taskのagent ID（用途未確認）: reported"))
        descendants(card).filterIsInstance<JButton>().first { it.text == "詳細を表示" }.doClick(0)
        descendants(card).filterIsInstance<JButton>().first { it.text == "差分を表示" }.doClick(0)
        assertEquals(diff, opened)
        assertEquals(1, descendants(timeline).filterIsInstance<StructuredToolCard>().size)
    }

    @Test fun `failed child survives duplicate completion and saved restoration contains safe summary only`(@TempDir directory: Path) {
        val store = ConversationStore(directory)
        val recorder = ConversationRecorder(Conversation(), store::save)
        SwingUtilities.invokeAndWait {
            recorder.begin(newHistoryId(), "public original prompt")
            val timeline = ChatTimelinePanel()
            val failed = tool("failed", AgentTask(name = "private-name", description = "private description", errorText = "private error", resultText = "private result"))
            val displayed = timeline.upsertTask(failed, "parent")
            recorder.tool("task-key", taskSavedSummary(displayed))
            timeline.upsertTask(tool("completed"), "parent")
            timeline.finishTasks().forEach { (id, value) -> recorder.tool(id, taskSavedSummary(value)) }
            recorder.finish("completed")
            val card = cards(timeline).single()
            assertEquals("failed", card.tool.status)
            assertEquals("private error", card.tool.task!!.errorText)
        }
        val loaded = store.load().conversations.single()
        assertEquals("ツール: 子Task (失敗)", loaded.turns.single().messages.last().text)
        assertFalse(Files.readString(directory.resolve("${loaded.id}.json")).contains("private"))
        SwingUtilities.invokeAndWait {
            val restored = ChatTimelinePanel()
            restored.restore(loaded)
            assertTrue(cards(restored).isEmpty())
            assertTrue(descendants(restored).filterIsInstance<javax.swing.JLabel>().any { it.text == "ツール: 子Task (失敗)" })
        }
    }
}
