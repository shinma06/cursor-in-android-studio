package com.cursoragent.notification

import com.cursoragent.settings.AgentSettingsState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object AgentNotificationService {
    private const val GROUP_ID = "Cursor Agent"

    fun notifyTurnCompleted(project: Project, exitCode: Int) {
        if (!AgentSettingsState.getInstance().notifyOnTurnComplete) return

        val type = if (exitCode == 0) NotificationType.INFORMATION else NotificationType.WARNING
        val content = if (exitCode == 0) {
            "Agent turn finished successfully."
        } else {
            "Agent turn finished with exit code $exitCode."
        }
        notify(project, "Turn complete", content, type)
    }

    fun notifyToolCall(project: Project, toolName: String) {
        if (!AgentSettingsState.getInstance().notifyOnApprovalPending) return

        // The headless CLI applies file edits immediately even without --force (see
        // CLAUDE.md's "Verified CLI behavior") — there is no approval step to wait for,
        // so this is purely an activity notice, not an approval prompt.
        notify(
            project,
            "Agent tool call",
            "Running: $toolName",
            NotificationType.INFORMATION,
        )
    }

    fun notifyError(project: Project, message: String) {
        if (!AgentSettingsState.getInstance().notifyOnTurnComplete) return

        notify(project, "Agent error", message.take(200), NotificationType.ERROR)
    }

    private fun notify(
        project: Project,
        title: String,
        content: String,
        type: NotificationType,
    ) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
        group.createNotification(title, content, type)
            .addAction(NotificationAction.createSimpleExpiring("Open Cursor Agent") {
                com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    .getToolWindow("Cursor Agent")
                    ?.activate(null)
            })
            .notify(project)
    }
}
