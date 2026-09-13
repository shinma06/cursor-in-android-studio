package com.cursoragent.ui.header

import com.cursoragent.service.AgentTransport
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.util.IconUtil
import java.awt.Component
import javax.swing.Icon

/** ToolWindow-local actions. Read the selected tab at update AND invocation, never capture a tab. */
internal class ToolWindowChatActions(
    settings: AgentSettingsState,
    private val available: () -> Boolean,
    private val running: () -> Boolean,
    transportState: () -> Pair<AgentTransport, Boolean>,
    onTransport: (AgentTransport) -> Unit,
    onSummarize: () -> Unit,
    onNewChat: () -> Unit,
    onHistory: (AnActionEvent) -> Unit,
    onMcp: () -> Unit,
    onSettings: () -> Unit,
    onEditNotice: () -> Unit,
    onOpenedChats: (AnActionEvent) -> Unit,
    onCloseAllChats: () -> Unit,
    onFeedback: (String) -> Unit,
    previewEnabled: () -> Boolean,
    onPreview: (Boolean) -> Unit,
    onEditorSettings: () -> Unit,
    onIconVisibilityChanged: () -> Unit,
    onBrowser: () -> Unit,
    onExport: () -> Unit = {},
) {
    val titleActions = listOf(
        action("新規チャット", "新しいタブでチャットを開始します。", AllIcons.General.Add, toolbarVisible = { settings.showNewChatIcon }) { onNewChat() },
        action("履歴", "このプロジェクトの過去のチャットを開きます。", AllIcons.Vcs.History, toolbarVisible = { settings.showHistoryIcon }, perform = onHistory),
    )

    val gearActions = DefaultActionGroup(titleActions + listOf(
        action("会話を書き出す…", "選択中の会話の現在までの本文をMarkdownへ保存します。") { onExport() },
        action("開いているチャット…", "このウィンドウの会話タブを検索して切り替えます。", perform = onOpenedChats),
        action("すべてのチャットを閉じる…", "会話本文・下書きの消失と実行停止を確認してから、チャットだけを閉じます。") { onCloseAllChats() },
        action("ブラウザーを開く…", "IDE内でURLを入力して手動で閲覧します。Agentによる操作・会話への共有は未接続です。") { onBrowser() },
        Separator.create("チャット設定"),
        choice("操作の確認", { settings.permissionMode }, { settings.permissionMode = it }, listOf(
            Option(PermissionMode.ASK_EVERY_TIME, "標準", "追加の自動承認を指定しません。ファイルの即時編集は防ぎません。"),
            Option(PermissionMode.AUTO_REVIEW, "AIによる確認", "操作を進めるかどうかの確認をAIに任せます。"),
            Option(PermissionMode.RUN_EVERYTHING, "すべて自動実行", "確認を省略して、ツールの操作を実行します。"),
        )),
        choice("実行範囲", { settings.sandboxMode }, { settings.sandboxMode = it }, listOf(
            Option(SandboxMode.DEFAULT, "CLIの既定", "実行範囲はCLIの既定設定に従います。"),
            Option(SandboxMode.ENABLED, "制限あり", "サンドボックス内で実行します。"),
            Option(SandboxMode.DISABLED, "制限なし", "サンドボックスを使わずに実行します。"),
        )),
        choice("作業場所", { settings.worktreeMode }, { settings.worktreeMode = it }, listOf(
            Option(WorktreeMode.DEFAULT, "このプロジェクト", "現在のプロジェクト内で作業します。"),
            Option(WorktreeMode.ISOLATED, "分離した作業コピー", "元のプロジェクトと分けて編集します。分離先のチェックポイント復元は未対応です。"),
        )),
        choice("接続方法", { transportState().first }, onTransport, listOf(
            Option(AgentTransport.PRINT, "互換CLI", "既存のCLI経路を使用します。"),
            Option(AgentTransport.ACP, "ACP", "新しい会話をACPで開始します。初期モデルは接続先の既定値です。会話開始後は切り替えません。"),
        ), enabled = { !running() && !transportState().second }),
        action("このセッションの内容を要約", "応答の完了後に、選択中の会話を要約する依頼を送信します。", enabled = { !running() }) { onSummarize() },
        action("MCPサーバー設定", "外部ツールとの接続を確認・管理します。") { onMcp() },
        action("設定", "CLIの実行ファイルや通知を設定します。") { onSettings() },
        action("ファイル編集について", "標準設定での即時編集と、適用後のRevertについて確認します。") { onEditNotice() },
        Separator.create(),
        DefaultActionGroup("フィードバック…", listOf(
            action("プラグインの不具合を報告（GitHub）…", "プラグインのIssue作成画面を開きます。会話やログは添付せず、投稿は利用者が行います。") {
                onFeedback("https://github.com/shinma06/cursor-in-android-studio/issues/new")
            },
            action("Cursor公式の報告案内…", "Cursor本体の不具合について、公式の報告案内ページを開きます。") {
                onFeedback("https://prod.cursor.com/help/troubleshooting/reporting-bugs")
            },
        )).apply { isPopup = true },
        DefaultActionGroup("ファイルエディター", listOf(
            toggle("プレビュータブを有効にする", "IDE全体のファイル用タブに適用します。会話タブの設定ではありません。", previewEnabled, onPreview),
            action("設定…", "IDE全体のファイルエディターのタブ設定を開きます。") { onEditorSettings() },
        )).apply { isPopup = true },
        DefaultActionGroup("上部アイコンの表示", listOf(
            toggle("新規チャット", "全プロジェクトのプラグイン最上段に新規チャットを表示します。", { settings.showNewChatIcon }) {
                settings.showNewChatIcon = it
                onIconVisibilityChanged()
            },
            toggle("履歴", "全プロジェクトのプラグイン最上段に履歴を表示します。", { settings.showHistoryIcon }) {
                settings.showHistoryIcon = it
                onIconVisibilityChanged()
            },
            action("既定に戻す", "新規チャットと履歴のアイコンを両方表示します。") {
                settings.showNewChatIcon = true
                settings.showHistoryIcon = true
                onIconVisibilityChanged()
            },
        )).apply { isPopup = true },
    ))

    private fun action(
        label: String,
        help: String,
        icon: Icon? = null,
        enabled: () -> Boolean = { true },
        toolbarVisible: () -> Boolean = { true },
        perform: (AnActionEvent) -> Unit,
    ) = object : DumbAwareAction(label, help, icon) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.icon = if (e.isFromActionToolbar) {
                icon?.let { compactHeaderIcon(it, e.getData(PlatformDataKeys.CONTEXT_COMPONENT)) }
            } else icon
            e.presentation.isEnabled = available() && enabled()
            e.presentation.isVisible = !e.isFromActionToolbar || toolbarVisible()
        }
        override fun actionPerformed(e: AnActionEvent) {
            if (available() && enabled()) perform(e)
        }
    }

    private fun toggle(label: String, help: String, selected: () -> Boolean, select: (Boolean) -> Unit) =
        object : DumbAwareToggleAction(label, help, null) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(e: AnActionEvent) = selected()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (available()) select(state)
            }
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.isEnabled = available()
            }
        }

    private data class Option<T>(val value: T, val label: String, val help: String)

    private fun <T> choice(
        caption: String,
        selected: () -> T,
        select: (T) -> Unit,
        options: List<Option<T>>,
        enabled: () -> Boolean = { true },
    ) = object : DefaultActionGroup(caption, options.map { option ->
        object : DumbAwareToggleAction(option.label, option.help, null) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(e: AnActionEvent) = selected() == option.value
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (state && available() && enabled()) select(option.value)
            }
            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.isEnabled = available() && enabled()
            }
        }
    }), DumbAware {
        init { isPopup = true }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.text = "$caption: ${options.first { it.value == selected() }.label}"
            e.presentation.description = if (caption == "接続方法") "接続方法は新しい会話の初回送信前に選べます。" else caption
            e.presentation.isEnabled = available() && enabled()
        }
    }
}

/** Keep native menu children without ActionGroupWrapper copying its vertical-ellipsis presentation. */
internal class ChatOptionsActionGroup(private val nativeMenu: ActionGroup) : ActionGroup("その他の操作", true), DumbAware {
    init {
        templatePresentation.icon = AllIcons.Actions.MoreHorizontal
        templatePresentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(e: AnActionEvent) {
        e.presentation.icon = if (e.isFromActionToolbar) {
            compactHeaderIcon(AllIcons.Actions.MoreHorizontal, e.getData(PlatformDataKeys.CONTEXT_COMPONENT))
        } else AllIcons.Actions.MoreHorizontal
    }
    override fun getChildren(e: AnActionEvent?) = nativeMenu.getChildren(e)
}

/** Shrink the glyph from 16 to 14 logical pixels without changing its ActionButton hit area. */
internal fun compactHeaderIcon(icon: Icon, component: Component? = null): Icon = IconUtil.scale(icon, component, 0.875f)
