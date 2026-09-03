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

@Service(Service.Level.APP)
@State(name = "CursorAgentSettings", storages = [Storage("cursor-agent-settings.xml")])
class AgentSettingsState : PersistentStateComponent<AgentSettingsState> {
    var agentExecutablePath: String = ""
    var selectedModel: String = ""
    var mode: AgentMode = AgentMode.AGENT
    var forceEnabled: Boolean = false

    override fun getState(): AgentSettingsState = this

    override fun loadState(state: AgentSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): AgentSettingsState =
            ApplicationManager.getApplication().getService(AgentSettingsState::class.java)
    }
}
