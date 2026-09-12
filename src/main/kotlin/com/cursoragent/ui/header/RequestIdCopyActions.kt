package com.cursoragent.ui.header

import com.cursoragent.service.AgentTransport
import com.cursoragent.session.SessionTabsSnapshot
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Inline native menu group: each opened menu owns a fixed selection/completion snapshot. */
internal class RequestIdCopyActions(
    private val snapshot: () -> SessionTabsSnapshot?,
    private val feedback: (String) -> Unit,
    private val copy: (String) -> Unit = { value ->
        val clipboard = CopyPasteManager.getInstance()
        clipboard.setContents(StringSelection(value))
        check(clipboard.getContents<String>(DataFlavor.stringFlavor) == value)
    },
) : DefaultActionGroup("Request IDをコピー", false), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val opened = snapshot()
        val requestId = opened?.selected?.requestId
        fun reason(): String? {
            val current = snapshot() ?: return "会話を開いてください"
            if (opened == null || current.selectedId != opened.selectedId || current.selectionRevision != opened.selectionRevision) {
                return "会話が変わりました。メニューを開き直してください"
            }
            val tab = current.selected
            return when {
                tab.transport == AgentTransport.ACP -> "ACPではRequest IDを取得できません"
                tab.run != null -> "応答の正常終了を待ってください"
                requestId == null -> "この会話では取得済みのRequest IDがありません。保存本文からは復元しません"
                tab.requestId !== requestId -> "応答が変わりました。メニューを開き直してください"
                else -> null
            }
        }
        return arrayOf(object : DumbAwareAction("Request IDをコピー", "最新の正常終了した互換CLI応答のRequest IDをコピーします。", null) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(e: AnActionEvent) {
                val unavailable = reason()
                e.presentation.isEnabled = unavailable == null
                e.presentation.description = unavailable ?: templatePresentation.description
                e.presentation.text = if (unavailable == null) "Request IDをコピー" else "Request IDをコピー（利用不可）"
            }
            override fun actionPerformed(e: AnActionEvent) {
                reason()?.let { feedback(it); return }
                val value = requestId?.value ?: return
                try {
                    copy(value)
                    feedback("Request IDをコピーしました")
                } catch (_: Exception) {
                    feedback("Request IDをコピーできませんでした。もう一度お試しください")
                }
            }
        })
    }
}
