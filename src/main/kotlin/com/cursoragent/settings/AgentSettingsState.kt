package com.cursoragent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

enum class AgentMode(val cliValue: String?) {
    ASK("ask"),
    AGENT(null),
    PLAN("plan"),
}

/**
 * The CLI's real approval model is 3-way, not a single force on/off switch (F-22
 * was redesigned into this per requirements doc §6.3, based on `agent --help` and
 * the CLI changelog): default confirms every tool call, `--auto-review` lets a
 * server-side classifier auto-run calls it judges safe and still asks about the
 * rest, and `--force`/`--yolo` runs everything unattended.
 */
enum class PermissionMode(val cliArg: String?, val label: String) {
    ASK_EVERY_TIME(null, "Ask Every Time"),
    AUTO_REVIEW("--auto-review", "Auto-review"),
    RUN_EVERYTHING("--force", "Run Everything (auto-approve)"),
}

@Service(Service.Level.APP)
@State(name = "CursorAgentSettings", storages = [Storage("cursor-agent-settings.xml")])
class AgentSettingsState : PersistentStateComponent<AgentSettingsState> {
    var agentExecutablePath: String = ""
    var selectedModel: String = ""
    var mode: AgentMode = AgentMode.AGENT
    // Defaults to the safest option -- must not silently default to auto-approving
    // file changes (this was the explicit safety requirement behind F-22).
    var permissionMode: PermissionMode = PermissionMode.ASK_EVERY_TIME

    override fun getState(): AgentSettingsState = this

    override fun loadState(state: AgentSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): AgentSettingsState =
            ApplicationManager.getApplication().getService(AgentSettingsState::class.java)
    }
}
