package com.cursoragent.ui

import com.cursoragent.actions.AgentWindowCommand
import com.cursoragent.settings.AgentMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChatEntryTest {
    @Test
    fun `input reset prefers current empty chat then first visible empty without changing its mode`() {
        val current = ChatResetTab("current", false, true, true, AgentMode.PLAN)
        val other = ChatResetTab("other", true, false, false, AgentMode.ASK)
        assertEquals(ChatReset("other", false, AgentMode.ASK), chatReset(listOf(other, current), current.id))
        assertEquals(ChatReset("current", false, AgentMode.PLAN), chatReset(listOf(other, current.copy(empty = true)), current.id))
    }

    @Test
    fun `input reset copies a draft only after a conversation and keeps non-Agent mode only with new input`() {
        for (history in listOf(false, true)) for (draft in listOf(false, true)) for (mode in AgentMode.entries) {
            val current = ChatResetTab("current", false, history, draft, mode)
            assertEquals(ChatReset(null, history, if (history && draft) mode else AgentMode.AGENT), chatReset(listOf(current), current.id))
        }
    }

    private val current = ChatEntryTab("current", false, false, null)
    private val empty = ChatEntryTab("empty", true, false, null)

    @Test
    fun `open hides only with panel focus and preserves hidden explicit selections`() {
        assertEquals(ChatEntry.Hide, decide(AgentWindowCommand.OPEN_CHAT, focused = true))
        assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.OPEN_CHAT))
        val attached = current.copy(hasSelections = true)
        assertEquals(ChatEntry.Focus("current", false), decide(AgentWindowCommand.OPEN_CHAT, listOf(attached), visible = false))
        assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.OPEN_CHAT, listOf(attached)))
    }

    @Test
    fun `follow up always retains current chat and allows adding selection`() {
        for (focused in listOf(false, true)) for (visible in listOf(false, true)) {
            assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.FOLLOW_UP,
                listOf(current.copy(hasSelections = true), empty), focused, visible))
        }
    }

    @Test
    fun `new Agent reuses first empty Agent or creates without replacing current draft`() {
        assertEquals(ChatEntry.Focus("empty", true), decide(AgentWindowCommand.NEW_AGENT, listOf(current, empty)))
        assertEquals(ChatEntry.Focus(null, true), decide(AgentWindowCommand.NEW_AGENT))
        assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.NEW_AGENT,
            listOf(current.copy(emptyAgent = true, hasSelections = true), empty)))
    }

    @Test
    fun `new Agent hides focused empty tab only after 500 milliseconds since latest show`() {
        val shown = current.copy(emptyAgent = true, lastShownNanos = 1_000_000_000L)
        assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.NEW_AGENT, listOf(shown), true, now = 1_499_999_999L))
        assertEquals(ChatEntry.Hide, decide(AgentWindowCommand.NEW_AGENT, listOf(shown), true, now = 1_500_000_000L))
        assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.NEW_AGENT,
            listOf(shown.copy(lastShownNanos = 1_499_999_999L)), true, now = 1_500_000_000L))
        assertEquals(ChatEntry.Focus("current", true), decide(AgentWindowCommand.NEW_AGENT, listOf(shown), false, now = 2_000_000_000L))
        assertEquals(ChatEntry.Hide, decide(AgentWindowCommand.NEW_AGENT, listOf(current.copy(emptyAgent = true)), true))
        assertEquals(ChatEntry.Focus(null, true), decide(AgentWindowCommand.NEW_AGENT, focused = true))
    }

    private fun decide(command: AgentWindowCommand, tabs: List<ChatEntryTab> = listOf(current), focused: Boolean = false,
        visible: Boolean = true, now: Long = 1_000_000_000L) = chatEntry(command, tabs, "current", focused, visible, now)
}
