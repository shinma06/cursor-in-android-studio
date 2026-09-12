package com.cursoragent.service

import com.cursoragent.acp.AcpSession
import com.cursoragent.settings.*
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class AgentSettingsBoundaryTest {
    private fun service(): AgentProcessService {
        val project = Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> "/synthetic-project"
                "isDisposed" -> false
                else -> error("Unexpected Project call: ${method.name}")
            }
        } as Project
        return AgentProcessService(project)
    }

    @Test
    fun `service keeps print settings and reports ACP restrictions without starting a run`() {
        val service = service()
        try {
            for (permission in PermissionMode.entries) for (sandbox in SandboxMode.entries) for (worktree in WorktreeMode.entries) {
                val settings = TurnSettings("synthetic", "", AgentMode.AGENT, permission, sandbox)
                assertNull(service.settingsUnavailableReason(AgentTransport.PRINT, settings, worktree))
                val reason = service.settingsUnavailableReason(AgentTransport.ACP, settings, worktree)
                val supported = permission == PermissionMode.ASK_EVERY_TIME && sandbox == SandboxMode.DEFAULT && worktree == WorktreeMode.DEFAULT
                assertEquals(supported, reason == null)
                if (!supported) assertTrue(reason!!.contains("設定を変更"))
            }
            service.tryRestore()!!.close()
        } finally {
            service.dispose()
        }
    }

    @Test
    fun `preparation retains the exact validated settings and workspace after defaults change`() {
        val service = service()
        val defaults = AgentSettingsState()
        val settings = TurnSettings("synthetic", "model-a", AgentMode.ASK, defaults.permissionMode, defaults.sandboxMode)
        val workspace = service.captureWorkspace(null, defaults.worktreeMode)
        assertNull(service.settingsUnavailableReason(AgentTransport.ACP, settings, workspace.mode))
        val turn = service.prepareTurn(workspace, settings) { object : AgentProcessListener {} }!!
        try {
            defaults.permissionMode = PermissionMode.RUN_EVERYTHING
            defaults.sandboxMode = SandboxMode.DISABLED
            defaults.worktreeMode = WorktreeMode.ISOLATED
            assertSame(settings, turn.settings)
            assertSame(workspace, turn.workspace)
            assertEquals(PermissionMode.ASK_EVERY_TIME, turn.settings.permission)
            assertEquals(SandboxMode.DEFAULT, turn.settings.sandbox)
            assertEquals(WorktreeMode.DEFAULT, turn.workspace.mode)
            assertEquals(listOf("--model", "model-a", "--mode", "ask"), turn.settings.arguments())
        } finally {
            turn.run.stop()
            turn.preparation.close()
            service.dispose()
        }
    }

    @Test
    fun `ACP execution rejects unsupported settings even without the early UI check`() {
        var launches = 0
        val errors = mutableListOf<String>()
        val gate = WorkspaceOperationGate()
        AcpSession({ _, _ -> launches++; error("Unsupported settings must not launch") }, gate::markUncertain).use { session ->
            val preparation = gate.tryPrepare()!!
            val run = AgentRun(object : AgentProcessListener {
                override fun onError(message: String) { errors.add(message) }
            })
            val settings = TurnSettings("synthetic", "", AgentMode.AGENT, PermissionMode.RUN_EVERYTHING, SandboxMode.DEFAULT)
            preparation.use {
                session.send("prompt", PreparedAgentTurn(run, TurnWorkspace("/synthetic-project", WorktreeMode.DEFAULT, null), preparation, settings))
            }
            assertEquals(0, launches)
            assertEquals(1, errors.size)
            assertTrue(errors.single().contains("設定を変更"))
            gate.tryRestore()!!.close()
        }
    }

    @Test
    fun `controller depends on service instead of ACP implementation`() {
        val source = Files.readString(Path.of("src/main/kotlin/com/cursoragent/ui/AgentUiController.kt"))
        assertFalse(source.contains("com.cursoragent.acp."))
    }
}
