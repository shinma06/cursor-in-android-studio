package com.cursoragent.notification

import com.cursoragent.PluginBrand
import com.cursoragent.settings.AgentSettingsState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

object AgentNotificationService {
    fun notifyTurnCompleted(project: Project, exitCode: Int, onOpen: () -> Unit = {}) {
        if (!AgentSettingsState.getInstance().notifyOnTurnComplete) return

        val type = if (exitCode == 0) NotificationType.INFORMATION else NotificationType.ERROR
        val content = if (exitCode == 0) "応答が完了しました。" else "応答に失敗しました。会話内のエラーを確認してください。"
        notify(project, if (exitCode == 0) "応答完了" else "応答失敗", content, type, onOpen)
    }

    fun notifyStopped(project: Project, onOpen: () -> Unit = {}) {
        if (AgentSettingsState.getInstance().notifyOnTurnComplete) {
            notify(project, "応答停止", "停止しました。適用済みの変更は自動で戻りません。", NotificationType.INFORMATION, onOpen)
        }
    }

    /** A turn calls this at most once, only while its tab is in the background. */
    fun notifyToolCall(project: Project, turnId: String, onOpen: () -> Unit = {}) {
        if (!AgentSettingsState.getInstance().notifyOnApprovalPending) return
        val notice = create(project, "ツール実行開始", "別の会話でツールの実行が始まりました。", NotificationType.INFORMATION, onOpen)
        project.getService(ToolActivityNotice::class.java).replace(turnId, notice)
        notice.notify(project)
    }

    fun clearToolCall(project: Project, turnId: String) {
        project.getService(ToolActivityNotice::class.java).clear(turnId)
    }

    fun notifyError(project: Project, message: String) {
        if (!AgentSettingsState.getInstance().notifyOnTurnComplete) return

        notify(project, "エラー", message.take(200), NotificationType.ERROR)
    }

    private fun notify(
        project: Project,
        title: String,
        content: String,
        type: NotificationType,
        onOpen: () -> Unit = {},
    ) {
        create(project, title, content, type, onOpen).notify(project)
    }

    private fun create(project: Project, title: String, content: String, type: NotificationType, onOpen: () -> Unit): Notification {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(PluginBrand.NOTIFICATION_GROUP_ID)
        return group.createNotification(title, content, type)
            .addAction(NotificationAction.createSimpleExpiring("会話を開く") {
                if (project.isDisposed) return@createSimpleExpiring
                onOpen()
                com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    .getToolWindow(PluginBrand.TOOL_WINDOW_ID)
                    ?.activate(null)
            })
    }
}

/** Only the latest activity notice across this project's tabs remains. Terminal notices stay distinct. */
@Service(Service.Level.PROJECT)
internal class ToolActivityNotice : Disposable {
    private var disposed = false
    private var owner: String? = null
    private var notification: Notification? = null

    @Synchronized
    fun replace(turnId: String, next: Notification) {
        if (disposed) { next.expire(); return }
        notification?.expire()
        owner = turnId
        notification = next
    }

    @Synchronized
    fun clear(turnId: String) {
        if (owner != turnId) return
        expireCurrent()
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        expireCurrent()
    }

    private fun expireCurrent() {
        notification?.expire()
        notification = null
        owner = null
    }
}
