package com.cursoragent.settings

import com.cursoragent.PluginBrand
import com.cursoragent.ui.ImmediateEditNotice
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
    private var panel: JPanel? = null
    private var defaultModelPanel: DefaultModelSettingsPanel? = null
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

        defaultModelPanel = DefaultModelSettingsPanel(settings)
        notifyOnTurnCompleteBox = JBCheckBox("応答が完了したら通知する", settings.notifyOnTurnComplete)
        notifyOnApprovalPendingBox = JBCheckBox(
            "ツールの実行が始まったら通知する",
            settings.notifyOnApprovalPending,
        )

        panel = FormBuilder.createFormBuilder()
            .addComponent(ImmediateEditNotice())
            .addLabeledComponent("CLIの実行ファイル:", agentPathPanel)
            .addComponent(agentPathDescription!!)
            .addLabeledComponent("新規会話の既定モデル（互換CLI）:", defaultModelPanel!!)
            .addComponent(JBLabel("適用後に作る互換CLI会話だけに使います。既存・復元会話とACPには適用しません。"))
            .addComponent(JBLabel("一覧は適用済みのCLI設定で取得します。CLIを変更した場合は適用して設定を開き直してください。"))
            .addComponent(notifyOnTurnCompleteBox!!)
            .addComponent(notifyOnApprovalPendingBox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        if (panel == null) return false
        val settings = AgentSettingsState.getInstance()
        return defaultModelPanel?.isModified(settings) == true ||
            agentPathSelection?.configuredPath != settings.agentExecutablePath ||
            notifyOnTurnCompleteBox?.isSelected != settings.notifyOnTurnComplete ||
            notifyOnApprovalPendingBox?.isSelected != settings.notifyOnApprovalPending
    }

    override fun apply() {
        val selection = agentPathSelection ?: return
        val settings = AgentSettingsState.getInstance()
        defaultModelPanel?.applyTo(settings)
        settings.agentExecutablePath = selection.configuredPath
        settings.notifyOnTurnComplete = notifyOnTurnCompleteBox?.isSelected == true
        settings.notifyOnApprovalPending = notifyOnApprovalPendingBox?.isSelected == true
        selection.reset(settings.agentExecutablePath)
        showAgentPathSelection()
    }

    override fun reset() {
        val settings = AgentSettingsState.getInstance()
        defaultModelPanel?.reset(settings)
        agentPathSelection?.reset(settings.agentExecutablePath)
        showAgentPathSelection()
        notifyOnTurnCompleteBox?.isSelected = settings.notifyOnTurnComplete
        notifyOnApprovalPendingBox?.isSelected = settings.notifyOnApprovalPending
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
        defaultModelPanel?.dispose()
        defaultModelPanel = null
        panel = null
        agentPathField = null
        agentPathSelection = null
        agentPathDescription = null
        notifyOnTurnCompleteBox = null
        notifyOnApprovalPendingBox = null
    }
}
