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
    fun `settings values use spare row width and actions have explicit Japanese labels`() = SwingUtilities.invokeAndWait {
        val panel = ComposerOptionsPanel(AgentSettingsState(), false, {}, {}, {}, {})
        fun layout(container: java.awt.Container) {
            container.doLayout()
            container.components.filterIsInstance<java.awt.Container>().forEach(::layout)
        }
        for (width in listOf(350, 420)) {
            panel.setSize(width, panel.preferredSize.height)
            layout(panel)
            for (choice in listOf(panel.permissionChoice, panel.sandboxChoice, panel.worktreeChoice)) {
                assertTrue(choice.width > choice.preferredSize.width + 20, choice.text)
            }
        }
        val labels = panel.components.filterIsInstance<JButton>().map { it.text }
        assertTrue("このセッションの内容を要約" in labels)
        assertTrue("MCPサーバー設定" in labels)
    }

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
        val summarize = buttons.first { it.text == "このセッションの内容を要約" }
        assertFalse(summarize.isEnabled)
        summarize.doClick()
        assertTrue(calls.isEmpty())
        panel.setRunning(false)
        assertTrue(summarize.isEnabled)
        summarize.doClick()
        assertEquals(listOf("close", "summarize"), calls)
        calls.clear()
        buttons.first { it.text == "プラグイン設定…" }.doClick()
        assertEquals(listOf("close", "settings"), calls)
    }
}
