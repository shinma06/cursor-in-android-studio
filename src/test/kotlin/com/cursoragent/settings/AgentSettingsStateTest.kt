package com.cursoragent.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the safety-critical default from requirements doc F-22/F-24: this must
 * never silently default to auto-approving file changes. If this test fails after
 * a change, that change needs a deliberate justification, not just a passing fix.
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
