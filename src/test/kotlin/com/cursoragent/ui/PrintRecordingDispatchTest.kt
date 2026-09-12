package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.history.ConversationStore
import com.cursoragent.history.newHistoryId
import com.cursoragent.parser.PrintAssistantText
import com.cursoragent.parser.StreamEvent
import com.cursoragent.parser.StreamJsonParser
import com.cursoragent.service.AgentProcessListener
import com.cursoragent.service.AgentRun
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.SwingUtilities

class PrintRecordingDispatchTest {
    @TempDir lateinit var directory: Path

    @Test fun `live fixture full replacements reach only their turn and saved body exactly once`() {
        val store = ConversationStore(directory)
        val recorder = ConversationRecorder(Conversation(), store::save)
        val turnId = newHistoryId()
        recorder.begin(turnId, "original prompt")
        val displayed = mutableListOf<String>()
        val assistant = TurnAssistantText({ displayed.add(it); recorder.assistant(it) }, recorder::newAssistant)
        var current = true
        lateinit var run: AgentRun
        run = AgentRun(object : AgentProcessListener {
            override fun onAssistantText(text: String) {
                updateCurrentTurnOnEdt({ false }, { current }, { run.wasStopped }) { assistant.printText(text) }
            }
            override fun onResultFallback(text: String) {
                updateCurrentTurnOnEdt({ false }, { current }, { run.wasStopped }) { assistant.printFallback(text) }
            }
        })
        val print = PrintAssistantText(PrintAssistantText.VERIFIED_VERSION, true)
        val parser = StreamJsonParser { event -> run.emit { listener -> when (event) {
            is StreamEvent.AssistantDelta -> print.accept(event)?.let(listener::onAssistantText)
            is StreamEvent.Result -> if (!event.isError) event.result?.let(listener::onResultFallback)
            else -> Unit
        } } }
        javaClass.getResource("/issue-116/tools.jsonl")!!.readText().lineSequence().forEach(parser::parseLine)
        SwingUtilities.invokeAndWait {}
        recorder.finish("completed")
        val expected = "START-A\nBETWEEN-BDONE-C 46"
        assertEquals(expected, displayed.last())
        assertEquals(expected, store.load().conversations.single().turns.single().messages.last().text)
        assertEquals(turnId, store.load().conversations.single().turns.single().id)
        val before = displayed.toList()
        current = false
        run.emit { it.onAssistantText("superseded turn") }
        run.stop()
        run.emit { it.onAssistantText("stopped output") }
        SwingUtilities.invokeAndWait {}
        assertEquals(before, displayed)
    }
}
