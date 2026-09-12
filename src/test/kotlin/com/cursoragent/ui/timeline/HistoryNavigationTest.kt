package com.cursoragent.ui.timeline

import com.cursoragent.history.*
import com.cursoragent.service.AgentEvent
import com.cursoragent.ui.TurnAssistantText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class HistoryNavigationTest {
    @Test fun `live and restored body rows agree across print replacements ACP boundaries and tool status`() = SwingUtilities.invokeAndWait {
        for (acp in listOf(false, true)) {
            val timeline = ChatTimelinePanel()
            val recorder = ConversationRecorder(Conversation()) {}
            recorder.begin(newHistoryId(), "元入力")
            timeline.addUserMessage("元入力")
            val text = TurnAssistantText({ timeline.setAssistantText(it); recorder.assistant(it) }, { timeline.finalizeAssistantMessage(); recorder.newAssistant() })
            if (acp) text.acpDelta(AgentEvent.Text("最初", "a", true)) else text.printDelta("最初")
            timeline.showStatus("思考")
            timeline.addToolCallSummary(null, "ファイル: 完了")
            recorder.tool("tool", "ファイル: 完了")
            if (acp) text.acpDelta(AgentEvent.Text("最後😀", "b", true)) else text.printDelta("最初 最後😀")
            val snapshot = recorder.conversation
            val match = findConversationText(snapshot, "最後😀")!!
            assertTrue(timeline.scrollToHistoryMatch(snapshot, match.messageId, "最後😀"))
            val restored = ChatTimelinePanel()
            restored.restore(snapshot)
            assertTrue(restored.scrollToHistoryMatch(snapshot, match.messageId, "最後😀"))
            recorder.assistant("検索後の全文差替え")
            assertFalse(timeline.scrollToHistoryMatch(recorder.conversation, match.messageId, "最後😀"))
            assertFalse(timeline.scrollToHistoryMatch(snapshot, "deleted-message", "最後😀"))
            restored.isActiveTab = false
            assertFalse(restored.scrollToHistoryMatch(snapshot, match.messageId, "最後😀"))
        }
    }
}
