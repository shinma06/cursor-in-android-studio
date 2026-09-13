package com.cursoragent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.messages.Topic

enum class AgentMode(val cliValue: String?) {
    ASK("ask"),
    AGENT(null),
    PLAN("plan"),
}

/**
 * Current print transport three-way CLI flag mapping (F-22/F-24). These labels do not guarantee
 * interactive approval in a headless process: live verification found immediate
 * edits even with no permission flag. The plugin offers post-edit Revert only.
 */
enum class PermissionMode(val cliArg: String?, val label: String) {
    ASK_EVERY_TIME(null, "Ask Every Time"),
    AUTO_REVIEW("--auto-review", "Auto-review"),
    RUN_EVERYTHING("--force", "Run Everything (auto-approve)"),
}

enum class SandboxMode(val cliValue: String?, val label: String) {
    DEFAULT(null, "Sandbox: CLI default"),
    ENABLED("enabled", "Sandbox: enabled"),
    DISABLED("disabled", "Sandbox: disabled"),
}

enum class WorktreeMode(val useIsolatedWorktree: Boolean, val label: String) {
    DEFAULT(false, "Worktree: off"),
    ISOLATED(true, "Worktree: isolated (-w)"),
}

enum class SendKeyMode {
    ENTER,
    MODIFIER_ENTER;

    fun keyLabel(isMac: Boolean): String = when (this) {
        ENTER -> "Enter"
        MODIFIER_ENTER -> if (isMac) "Cmd+Enter" else "Ctrl+Enter"
    }

    override fun toString(): String = when (this) {
        ENTER -> "Enterで送信 / Shift+Enterで改行"
        MODIFIER_ENTER -> "${keyLabel(com.intellij.openapi.util.SystemInfo.isMac)}で送信 / Enterで改行"
    }
}

@Service(Service.Level.APP)
@State(name = "CursorAgentSettings", storages = [Storage("cursor-agent-settings.xml")])
class AgentSettingsState : PersistentStateComponent<AgentSettingsState> {
    var agentExecutablePath: String = ""
    var selectedModel: String = ""
    var mode: AgentMode = AgentMode.AGENT
    // Preserve the no-extra-approval-flag default. This does not prevent immediate
    // headless CLI edits; never silently opt users into --auto-review or --force.
    var permissionMode: PermissionMode = PermissionMode.ASK_EVERY_TIME
    var sandboxMode: SandboxMode = SandboxMode.DEFAULT
    var worktreeMode: WorktreeMode = WorktreeMode.DEFAULT
    var showNewChatIcon: Boolean = true
    var showHistoryIcon: Boolean = true
    var sendKeyMode: SendKeyMode = SendKeyMode.ENTER
    var notifyOnTurnComplete: Boolean = true
    var notifyOnApprovalPending: Boolean = true

    override fun getState(): AgentSettingsState = this

    override fun loadState(state: AgentSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @Topic.AppLevel
        val SEND_KEY_CHANGED: Topic<Runnable> = Topic.create("Cursor Agent send key changed", Runnable::class.java)

        fun getInstance(): AgentSettingsState =
            ApplicationManager.getApplication().getService(AgentSettingsState::class.java)
    }
}
