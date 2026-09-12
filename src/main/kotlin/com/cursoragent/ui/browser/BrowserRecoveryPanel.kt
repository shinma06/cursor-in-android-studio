package com.cursoragent.ui.browser

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel

/** Browser-free recovery UI: opening this panel never installs or enables a plugin. */
internal class BrowserRecoveryPanel(onSettings: () -> Unit, onHelp: () -> Unit) : JPanel(BorderLayout(0, 8)) {
    val settingsButton = JButton("プラグイン設定を開く").apply { addActionListener { onSettings() } }

    init {
        border = JBUI.Borders.empty(8, 0)
        add(JBScrollPane(JBTextArea(
            """
            内蔵ブラウザーには、このIDE・OS・CPUに対応したJCEFの実行環境が必要です。チャットは引き続き利用できます。

            1. プラグイン設定の「Marketplace」で「Web Browser (JCEF)」（提供元: JetBrains）を検索し、対応版をインストールしてください。導入済みなら「Installed」で有効か確認してください。
            2. 導入・有効化後はIDEの案内に従って再起動し、ブラウザーを開き直してください。
            3. 対応版が見つからない場合は、公式ページでIDE・OS・CPUの互換性を確認してください。対応版を導入できない環境では内蔵ブラウザーを利用できません。

            導入済みでも起動できない場合は、公式ページの互換性とIDEのログを確認してください。導入だけでの復旧は保証されません。設定を自動変更することはありません。
            """.trimIndent(),
        ).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            accessibleContext.accessibleName = "内蔵ブラウザーの導入・回復手順"
        }), BorderLayout.CENTER)
        add(JPanel(GridLayout(0, 1, 0, 6)).apply {
            add(settingsButton)
            add(JButton("公式配布ページを外部ブラウザーで開く").apply { addActionListener { onHelp() } })
        }, BorderLayout.SOUTH)
    }
}
