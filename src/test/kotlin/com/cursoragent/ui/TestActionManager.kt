package com.cursoragent.ui

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.extensions.PluginId
import java.awt.Component
import java.awt.event.InputEvent

// No IDE application or GUI: these actions only read Presentation and their injected callbacks.
internal val unusedActionManager = object : ActionManager() {
    override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu = error("Unused")
    override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar = error("Unused")
    override fun getAction(id: String): AnAction? = error("Unused")
    override fun getId(action: AnAction): String? = error("Unused")
    override fun registerAction(id: String, action: AnAction) = error("Unused")
    override fun registerAction(id: String, action: AnAction, pluginId: PluginId?) = error("Unused")
    override fun unregisterAction(id: String) = error("Unused")
    override fun replaceAction(id: String, action: AnAction) = error("Unused")
    @Deprecated("Required by the IDE ActionManager test stub")
    override fun getActionIds(prefix: String): Array<String> = error("Unused")
    override fun getActionIdList(prefix: String): List<String> = error("Unused")
    override fun isGroup(id: String) = false
    override fun getActionOrStub(id: String): AnAction? = error("Unused")
    override fun addTimerListener(listener: TimerListener) = error("Unused")
    override fun removeTimerListener(listener: TimerListener) = error("Unused")
    override fun tryToExecute(action: AnAction, input: InputEvent?, component: Component?, place: String?, now: Boolean): com.intellij.openapi.util.ActionCallback = error("Unused")
    override fun getKeyboardShortcut(id: String): KeyboardShortcut? = error("Unused")
}
