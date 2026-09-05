package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.SwingUtilities

class ComposerOptionsPanelTest {
    @Test
    fun `opening settings preserves saved policies and does not invoke any actions`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState().apply {
            permissionMode = PermissionMode.AUTO_REVIEW
            sandboxMode = SandboxMode.ENABLED
            worktreeMode = WorktreeMode.ISOLATED
        }
        val unexpected = { fail<Unit>("Opening the panel must not run actions") }
        val panel = ComposerOptionsPanel(settings, false, unexpected, unexpected, unexpected, unexpected)
        assertEquals(PermissionMode.AUTO_REVIEW, settings.permissionMode)
        assertEquals(SandboxMode.ENABLED, settings.sandboxMode)
        assertEquals(WorktreeMode.ISOLATED, settings.worktreeMode)
        assertEquals("操作の確認: AIによる確認", panel.permissionChoice.getAccessibleContext().accessibleName)
        assertEquals("作業場所: 分離した作業コピー", panel.worktreeChoice.getAccessibleContext().accessibleName)
    }

    @Test
    fun `choosing a setting changes only that setting and retains CLI mappings`() = SwingUtilities.invokeAndWait {
        val settings = AgentSettingsState()
        val panel = ComposerOptionsPanel(settings, false, {}, {}, {}, {})
        assertEquals(PermissionMode.ASK_EVERY_TIME, settings.permissionMode)
        assertNull(settings.permissionMode.cliArg)
        panel.sandboxChoice.select(SandboxMode.ENABLED)
        assertEquals(SandboxMode.ENABLED, settings.sandboxMode)
        assertEquals(PermissionMode.ASK_EVERY_TIME, settings.permissionMode)
        assertEquals(WorktreeMode.DEFAULT, settings.worktreeMode)
        panel.permissionChoice.select(PermissionMode.AUTO_REVIEW)
        assertEquals("--auto-review", settings.permissionMode.cliArg)
        assertEquals(SandboxMode.ENABLED, settings.sandboxMode)
    }

    @Test
    fun `summarize is disabled during a turn and settings action closes popup before opening dialog`() = SwingUtilities.invokeAndWait {
        val calls = mutableListOf<String>()
        val panel = ComposerOptionsPanel(AgentSettingsState(), true,
            { calls.add("summarize") }, {}, { calls.add("settings") }, { calls.add("close") })
        val buttons = panel.components.filterIsInstance<JButton>()
        val summarize = buttons.first { it.text == "会話を要約" }
        assertFalse(summarize.isEnabled)
        summarize.doClick()
        assertTrue(calls.isEmpty())
        buttons.first { it.text == "プラグイン設定…" }.doClick()
        assertEquals(listOf("close", "settings"), calls)
    }
}
