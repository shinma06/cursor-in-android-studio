package com.cursoragent.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the default CLI settings from requirements doc F-22/F-24: neither
 * `--auto-review` nor `--force` is added by default. This does not guarantee
 * approval before file writes; print may edit immediately with no extra flag.
 * Changes to these defaults need deliberate justification.
 */
class AgentSettingsStateTest {
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
}
