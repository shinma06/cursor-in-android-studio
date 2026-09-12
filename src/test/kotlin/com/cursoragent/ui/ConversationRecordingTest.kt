package com.cursoragent.ui

import com.cursoragent.history.Conversation
import com.cursoragent.history.ConversationRecorder
import com.cursoragent.history.newHistoryId
import com.cursoragent.service.AgentEvent
import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTabs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConversationRecordingTest {
    @Test fun `normalized print replacement and ACP boundaries record exactly the displayed text`() {
        for (acp in listOf(false, true)) {
            val recorder = ConversationRecorder(Conversation()) {}
            recorder.begin(newHistoryId(), "元prompt")
            val displayed = mutableListOf<String>()
            val text = TurnAssistantText({ displayed.add(it); recorder.assistant(it) }, recorder::newAssistant)
            if (acp) {
                text.acpDelta(AgentEvent.Text("同じ", "a", true))
                text.acpDelta(AgentEvent.Text("同じ", "a", false))
                text.acpDelta(AgentEvent.Text("次", "b", true))
                assertEquals(listOf("元prompt", "同じ同じ", "次"), recorder.conversation.turns.single().messages.map { it.text })
            } else {
                text.printDelta("hello")
                text.printDelta("hello world")
                text.printFallback("ignored")
                assertEquals(displayed.last(), recorder.conversation.turns.single().messages.last().text)
            }
            recorder.finish("interrupted")
            val snapshot = recorder.conversation
            recorder.assistant("遅延")
            assertEquals(snapshot, recorder.conversation)
        }
    }

    @Test fun `provider namespaces and conversation identity do not alias tab or turn`() {
        val sessions = SessionTabs()
        val print = sessions.open("same", transport = AgentTransport.PRINT)
        val acp = sessions.open("same", transport = AgentTransport.ACP)
        assertNotEquals(print.id, acp.id)
        assertNotEquals(print.id, print.conversationId)
        val reopened = sessions.open(conversationId = print.conversationId)
        assertEquals(print.id, reopened.id)
        sessions.updateComposer(acp.id, acp.mode, "", "prompt", 6)
        val turn = sessions.beginTurn(acp.id)!!
        assertTrue(sessions.bindChat(turn.token, "same"))
        assertNotEquals(acp.conversationId, turn.token.turnId)
    }

    @Test fun `tool categories never persist arbitrary command or provider strings`() {
        assertEquals("ツール", safeToolKind("token=secret"))
        assertEquals("実行中", safeToolStatus("token=secret"))
        assertEquals("コマンド", safeToolKind("shell"))
    }
}
