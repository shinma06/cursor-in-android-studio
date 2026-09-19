package com.cursoragent.settings

import com.cursoragent.PluginBrand
import com.cursoragent.ui.ImmediateEditNotice
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

class AgentSettingsConfigurable : Configurable {
    private var fontSizeBox: javax.swing.JComboBox<String>? = null
    private var wrapCodeBox: JBCheckBox? = null
    private val fontSizes = listOf(0) + (8..36)
    private var sendKeyBox: javax.swing.JComboBox<SendKeyMode>? = null
    private var panel: JPanel? = null
    private var diagnosticsPanel: PluginDiagnosticsPanel? = null
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var agentPathSelection: AgentExecutablePathSelection? = null
    private var agentPathDescription: JBLabel? = null
    private var updatingAgentPath = false
    private var notifyOnTurnCompleteBox: JBCheckBox? = null
    private var notifyOnApprovalPendingBox: JBCheckBox? = null

    override fun getDisplayName(): String = PluginBrand.NAME

    override fun createComponent(): JComponent {
        val settings = AgentSettingsState.getInstance()
        agentPathSelection = AgentExecutablePathSelection().apply { reset(settings.agentExecutablePath) }
        agentPathDescription = JBLabel()

        agentPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "cursor-agentの実行ファイルを選択",
                "agentコマンドの実行ファイルを指定してください",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
            textField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) {
                    if (updatingAgentPath) return
                    agentPathSelection?.edit(text)
                    agentPathDescription?.text = agentPathSelection?.description.orEmpty()
                }
            })
        }
        showAgentPathSelection()
        val agentPathPanel = JPanel(BorderLayout()).apply {
            add(agentPathField!!, BorderLayout.CENTER)
            add(JButton("自動検出に戻す").apply {
                addActionListener {
                    agentPathSelection?.useAutomatic()
                    showAgentPathSelection()
                }
            }, BorderLayout.EAST)
        }

        fontSizeBox = javax.swing.JComboBox(fontSizes.map { if (it == 0) "標準（IDEに追従）" else "$it pt" }.toTypedArray()).apply {
            selectedIndex = fontSizes.indexOf(settings.conversationFontSize)
        }
        wrapCodeBox = JBCheckBox("コードの長い行を折り返す", settings.wrapCodeLines)
        sendKeyBox = javax.swing.JComboBox(SendKeyMode.entries.toTypedArray()).apply { selectedItem = settings.sendKeyMode }
        notifyOnTurnCompleteBox = JBCheckBox("応答の完了・失敗・停止を通知する", settings.notifyOnTurnComplete)
        notifyOnApprovalPendingBox = JBCheckBox(
            "別の会話のツール開始を通知する（各ターンに一度）",
            settings.notifyOnApprovalPending,
        )

        diagnosticsPanel = PluginDiagnosticsPanel()

        panel = FormBuilder.createFormBuilder()
            .addComponent(ImmediateEditNotice())
            .addLabeledComponent("CLIの実行ファイル:", agentPathPanel)
            .addComponent(agentPathDescription!!)
            .addLabeledComponent("メッセージの送信キー:", sendKeyBox!!)
            .addLabeledComponent("会話本文の文字サイズ:", fontSizeBox!!)
            .addComponent(wrapCodeBox!!)
            .addComponent(JBLabel("適用すると全会話へ反映します。標準はIDEの表示文字・拡大率に追従します。"))
            .addComponent(notifyOnTurnCompleteBox!!)
            .addComponent(notifyOnApprovalPendingBox!!)
            .addSeparator()
            .addComponent(diagnosticsPanel!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        if (panel == null) return false
        val settings = AgentSettingsState.getInstance()
        return fontSizes.getOrNull(fontSizeBox?.selectedIndex ?: -1) != settings.conversationFontSize ||
            wrapCodeBox?.isSelected != settings.wrapCodeLines ||
            sendKeyBox?.selectedItem != settings.sendKeyMode ||
            agentPathSelection?.configuredPath != settings.agentExecutablePath ||
            notifyOnTurnCompleteBox?.isSelected != settings.notifyOnTurnComplete ||
            notifyOnApprovalPendingBox?.isSelected != settings.notifyOnApprovalPending
    }

    override fun apply() {
        val selection = agentPathSelection ?: return
        val settings = AgentSettingsState.getInstance()
        settings.agentExecutablePath = selection.configuredPath
        settings.notifyOnTurnComplete = notifyOnTurnCompleteBox?.isSelected == true
        settings.notifyOnApprovalPending = notifyOnApprovalPendingBox?.isSelected == true
        val sendKey = sendKeyBox?.selectedItem as? SendKeyMode ?: SendKeyMode.ENTER
        if (settings.sendKeyMode != sendKey) {
            settings.sendKeyMode = sendKey
            ApplicationManager.getApplication().messageBus.syncPublisher(AgentSettingsState.SEND_KEY_CHANGED).run()
        }
        val fontSize = fontSizes.getOrNull(fontSizeBox?.selectedIndex ?: -1) ?: 0
        val wrapCode = wrapCodeBox?.isSelected == true
        if (settings.conversationFontSize != fontSize || settings.wrapCodeLines != wrapCode) {
            settings.conversationFontSize = fontSize
            settings.wrapCodeLines = wrapCode
            ApplicationManager.getApplication().messageBus.syncPublisher(AgentSettingsState.DISPLAY_CHANGED).run()
        }
        selection.reset(settings.agentExecutablePath)
        showAgentPathSelection()
        diagnosticsPanel?.refresh()
    }

    override fun reset() {
        val settings = AgentSettingsState.getInstance()
        agentPathSelection?.reset(settings.agentExecutablePath)
        showAgentPathSelection()
        fontSizeBox?.selectedIndex = fontSizes.indexOf(settings.conversationFontSize)
        wrapCodeBox?.isSelected = settings.wrapCodeLines
        sendKeyBox?.selectedItem = settings.sendKeyMode
        notifyOnTurnCompleteBox?.isSelected = settings.notifyOnTurnComplete
        notifyOnApprovalPendingBox?.isSelected = settings.notifyOnApprovalPending
        diagnosticsPanel?.refresh()
    }

    private fun showAgentPathSelection() {
        updatingAgentPath = true
        try {
            agentPathField?.text = agentPathSelection?.displayedPath.orEmpty()
            agentPathDescription?.text = agentPathSelection?.description.orEmpty()
        } finally {
            updatingAgentPath = false
        }
    }

    override fun disposeUIResources() {
        fontSizeBox = null
        wrapCodeBox = null
        sendKeyBox = null
        panel = null
        diagnosticsPanel = null
        agentPathField = null
        agentPathSelection = null
        agentPathDescription = null
        notifyOnTurnCompleteBox = null
        notifyOnApprovalPendingBox = null
    }
}
