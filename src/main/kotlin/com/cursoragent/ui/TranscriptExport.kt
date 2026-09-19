package com.cursoragent.ui

import com.cursoragent.PluginBrand
import com.cursoragent.history.Conversation
import com.cursoragent.history.writeConversationMarkdown
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import javax.swing.SwingUtilities

/** Selection and snapshot happen before the native dialog can pump events or switch tabs. */
internal class TranscriptExport(private val project: Project) {
    fun export(snapshot: Conversation?) {
        if (project.isDisposed) return
        if (snapshot == null || snapshot.turns.none { it.messages.isNotEmpty() }) {
            Messages.showInfoMessage(project, "書き出せる本文がありません。旧履歴の本文や未送信の下書きは含まれません。", "会話を書き出す")
            return
        }
        val destination = try {
            FileChooserFactory.getInstance()
                .createSaveFileDialog(FileSaverDescriptor("会話を書き出す", "選択時点の会話をMarkdownで保存します。", "md"), project)
                .save("conversation.md")?.file?.toPath() ?: return
        } catch (_: Exception) {
            Messages.showErrorDialog(project, "保存先を選べませんでした。もう一度お試しください。", "会話を書き出す")
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val saved = runCatching { writeConversationMarkdown(snapshot, destination) }.isSuccess
            SwingUtilities.invokeLater {
                if (project.isDisposed) return@invokeLater
                val notification = NotificationGroupManager.getInstance().getNotificationGroup(PluginBrand.NOTIFICATION_GROUP_ID)
                    .createNotification("会話を書き出す", if (saved) "Markdownを保存しました。" else "保存できませんでした。保存先の容量・権限を確認してください。", if (saved) NotificationType.INFORMATION else NotificationType.ERROR)
                if (saved) notification.addAction(NotificationAction.createSimpleExpiring("開く") {
                    if (!project.isDisposed) LocalFileSystem.getInstance().refreshAndFindFileByNioFile(destination)?.let {
                        FileEditorManager.getInstance(project).openFile(it, true)
                    }
                })
                notification.notify(project)
            }
        }
    }
}
