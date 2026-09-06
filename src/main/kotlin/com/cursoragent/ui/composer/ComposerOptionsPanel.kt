package com.cursoragent.ui.composer

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.ImmediateEditNotice
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

/** Advanced options stay behind the overflow trigger; opening this panel never mutates settings. */
internal class ComposerOptionsPanel(
    settings: AgentSettingsState,
    isRunning: Boolean,
    onSummarize: () -> Unit,
    onMcp: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) : JPanel() {
    val permissionChoice = SettingsChoice(
        "操作の確認", settings.permissionMode,
        listOf(
            SettingsOption(PermissionMode.ASK_EVERY_TIME, "標準", "追加の自動承認を指定しません。\nファイルの即時編集は防ぎません。"),
            SettingsOption(PermissionMode.AUTO_REVIEW, "AIによる確認", "操作を進めるかどうかの確認を\nAIに任せます。"),
            SettingsOption(PermissionMode.RUN_EVERYTHING, "すべて自動実行", "確認を省略して、\nツールの操作を実行します。"),
        ),
    ) { settings.permissionMode = it }
    val sandboxChoice = SettingsChoice(
        "実行範囲", settings.sandboxMode,
        listOf(
            SettingsOption(SandboxMode.DEFAULT, "CLIの既定", "実行範囲はCLIの既定設定に従います。"),
            SettingsOption(SandboxMode.ENABLED, "制限あり", "サンドボックス内で実行します。"),
            SettingsOption(SandboxMode.DISABLED, "制限なし", "サンドボックスを使わずに実行します。"),
        ),
    ) { settings.sandboxMode = it }
    val worktreeChoice = SettingsChoice(
        "作業場所", settings.worktreeMode,
        listOf(
            SettingsOption(WorktreeMode.DEFAULT, "このプロジェクト", "現在のプロジェクト内で作業します。"),
            SettingsOption(WorktreeMode.ISOLATED, "分離した作業コピー", "元のプロジェクトと分けて編集します。\n分離先のチェックポイント復元は未対応です。"),
        ),
    ) { settings.worktreeMode = it }

    private val summarizeButton = action("このセッションの内容を要約", "会話の内容を要約する依頼を送信します。") {
        onClose()
        onSummarize()
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = AgentUiColors.panelBackground
        border = JBUI.Borders.empty(8)
        add(JLabel("チャット設定").apply {
            font = font.deriveFont(java.awt.Font.BOLD)
            border = JBUI.Borders.empty(4, 8, 8, 8)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        for (choice in listOf(permissionChoice, sandboxChoice, worktreeChoice)) {
            add(JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
                isOpaque = false
                border = JBUI.Borders.empty(3, 8)
                alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(38))
                add(JLabel(choice.caption).apply { labelFor = choice }, BorderLayout.WEST)
                add(choice.apply { horizontalAlignment = SwingConstants.RIGHT }, BorderLayout.CENTER)
            })
        }
        separator()
        add(summarizeButton)
        setRunning(isRunning)
        add(action("MCPサーバー設定", "外部ツールとの接続を確認・管理します。") {
            onClose()
            onMcp()
        })
        add(action("設定", "CLIの実行ファイルや通知を設定します。") {
            onClose()
            onSettings()
        })
        separator()
        add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(6, 8, 4, 8)
            add(JLabel("ファイル編集について").apply { foreground = AgentUiColors.mutedText }, BorderLayout.NORTH)
            add(ImmediateEditNotice().apply {
                foreground = AgentUiColors.mutedText
                font = font.deriveFont(font.size2D - 1f)
            }, BorderLayout.CENTER)
        })
    }

    override fun getPreferredSize(): Dimension = super.getPreferredSize().let {
        Dimension(maxOf(JBUI.scale(350), it.width), it.height)
    }

    private fun separator() {
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(6, 0)
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(13))
            add(JSeparator())
        })
    }

    fun setRunning(running: Boolean) {
        summarizeButton.isEnabled = !running
        summarizeButton.toolTipText = if (running) "応答の完了後に会話を要約できます。" else "会話の内容を要約する依頼を送信します。"
    }

    private fun action(label: String, help: String, callback: () -> Unit) = SelectorButton().apply {
        text = label
        toolTipText = help
        getAccessibleContext().accessibleName = label
        horizontalAlignment = SwingConstants.LEFT
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
        preferredSize = JBUI.size(330, 32)
        addActionListener { callback() }
    }
}

internal data class SettingsOption<T>(val value: T, val label: String, val explanation: String)

internal class SettingsChoice<T>(
    val caption: String,
    initial: T,
    private val options: List<SettingsOption<T>>,
    private val onSelect: (T) -> Unit,
) : SelectorButton() {
    private var selected = options.first { it.value == initial }

    init {
        showsChevron = true
        refreshLabel()
        addActionListener {
            JBPopupFactory.getInstance().createPopupChooserBuilder(options)
                .setSelectedValue(selected, true)
                .setRenderer { _, option, _, highlighted, _ ->
                    JPanel(BorderLayout(0, JBUI.scale(3))).apply {
                        background = if (highlighted) AgentUiColors.userBubbleBackground else AgentUiColors.panelBackground
                        border = JBUI.Borders.empty(7, 10)
                        getAccessibleContext().accessibleName = "$caption: ${option.label}。${option.explanation}"
                        add(JPanel(BorderLayout()).apply {
                            isOpaque = false
                            add(JLabel(option.label), BorderLayout.CENTER)
                            add(JLabel(if (option == selected) "✓" else ""), BorderLayout.EAST)
                        }, BorderLayout.NORTH)
                        add(JLabel("<html>${option.explanation.replace("\n", "<br>")}</html>").apply {
                            foreground = AgentUiColors.mutedText
                            font = font.deriveFont(font.size2D - 1f)
                        }, BorderLayout.CENTER)
                    }
                }
                .setItemChosenCallback { select(it.value) }
                .createPopup()
                .showUnderneathOf(this)
        }
    }

    internal fun select(value: T) {
        selected = options.first { it.value == value }
        onSelect(value)
        refreshLabel()
    }

    private fun refreshLabel() {
        text = selected.label
        toolTipText = selected.explanation.replace('\n', ' ')
        getAccessibleContext().accessibleName = "$caption: ${selected.label}"
        revalidate()
        repaint()
    }
}
