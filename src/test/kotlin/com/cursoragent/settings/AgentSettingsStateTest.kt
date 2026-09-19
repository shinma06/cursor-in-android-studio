package com.cursoragent.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.junit.jupiter.api.Test

/**
 * Guards the safety-critical default from requirements doc F-22/F-24: this must
 * never silently default to auto-approving file changes. If this test fails after
 * a change, that change needs a deliberate justification, not just a passing fix.
 */
class AgentSettingsStateTest {
    @Test
    fun `display settings restore defaults and validate persisted font sizes`() {
        val old = XmlSerializer.deserialize(Element("state"), AgentSettingsState::class.java)
        assertEquals(0, old.conversationFontSize)
        assertFalse(old.wrapCodeLines)
        for (size in listOf(Int.MIN_VALUE, -1, 0, 7, 8, 16, 36, 37, Int.MAX_VALUE)) {
            val xml = Element("state").addContent(Element("option").setAttribute("name", "conversationFontSize").setAttribute("value", size.toString()))
            val restored = XmlSerializer.deserialize(xml, AgentSettingsState::class.java)
            restored.wrapCodeLines = true
            val roundTrip = XmlSerializer.deserialize(XmlSerializer.serialize(restored), AgentSettingsState::class.java)
            val live = AgentSettingsState().apply { loadState(roundTrip) }
            assertEquals(if (size in 8..36) size else 0, live.conversationFontSize)
            assertTrue(live.wrapCodeLines)
            assertEquals(PermissionMode.ASK_EVERY_TIME, live.permissionMode)
        }
    }

    @Test
    fun `permission mode defaults to the safest option`() {
        assertEquals(PermissionMode.ASK_EVERY_TIME, AgentSettingsState().permissionMode)
    }

    @Test
    fun `force enabled cli arg is not sent by default`() {
        assertEquals(null, AgentSettingsState().permissionMode.cliArg)
    }

    @Test
    fun `sandbox and worktree default to off`() {
        val settings = AgentSettingsState()
        assertEquals(SandboxMode.DEFAULT, settings.sandboxMode)
        assertEquals(WorktreeMode.DEFAULT, settings.worktreeMode)
    }
    @Test
    fun `icon visibility round trips and old settings keep both icons visible`() {
        val oldXml = Element("state").addContent(
            Element("option").setAttribute("name", "permissionMode").setAttribute("value", "AUTO_REVIEW"),
        )
        val old = XmlSerializer.deserialize(oldXml, AgentSettingsState::class.java)
        assertTrue(old.showNewChatIcon)
        assertTrue(old.showHistoryIcon)
        assertEquals(PermissionMode.AUTO_REVIEW, old.permissionMode)
        old.showNewChatIcon = false
        old.showHistoryIcon = false
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(old), AgentSettingsState::class.java)
        val live = AgentSettingsState().apply { loadState(restored) }
        assertFalse(live.showNewChatIcon)
        assertFalse(live.showHistoryIcon)
        assertEquals(PermissionMode.AUTO_REVIEW, live.permissionMode)
    }

    @Test
    fun `old XML keeps Enter and both send modes survive load and round trip`() {
        val old = XmlSerializer.deserialize(Element("state"), AgentSettingsState::class.java)
        assertEquals(SendKeyMode.ENTER, old.sendKeyMode)
        for (mode in SendKeyMode.entries) {
            old.sendKeyMode = mode
            val restored = XmlSerializer.deserialize(XmlSerializer.serialize(old), AgentSettingsState::class.java)
            assertEquals(mode, AgentSettingsState().apply { loadState(restored) }.sendKeyMode)
        }
    }

}
