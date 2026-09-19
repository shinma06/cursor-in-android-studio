package com.cursoragent.settings

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JPanel

internal class PluginDiagnosticsPanel(
    private val readDiagnostics: () -> String = ::readPluginDiagnostics,
    private val copy: (String) -> Unit = { CopyPasteManager.getInstance().setContents(StringSelection(it)) },
) : JPanel(BorderLayout(0, 6)) {
    private val details = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 9
        accessibleContext.accessibleName = "ビルド・CLI診断情報"
    }

    init {
        add(JBTextArea(
            "適用済みの設定を表示します。変更後は適用してください。CLIは実行しません。\n" +
                "コピーには下記のローカルパスが含まれます。共有前に内容を確認してください。自動送信はしません。",
        ).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
        }, BorderLayout.NORTH)
        add(JBScrollPane(details), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JButton("表示を更新").apply { addActionListener { refresh() } })
            add(JButton("表示中の診断情報をコピー").apply { addActionListener { copy(details.text) } })
        }, BorderLayout.SOUTH)
        refresh()
    }

    fun refresh() {
        details.text = readDiagnostics()
        details.caretPosition = 0
    }
}
