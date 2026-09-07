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

        notifyOnTurnCompleteBox = JBCheckBox("応答が完了したら通知する", settings.notifyOnTurnComplete)
        notifyOnApprovalPendingBox = JBCheckBox(
            "ツールの実行が始まったら通知する",
            settings.notifyOnApprovalPending,
        )

        panel = FormBuilder.createFormBuilder()
            .addComponent(ImmediateEditNotice())
            .addLabeledComponent("CLIの実行ファイル:", agentPathPanel)
            .addComponent(agentPathDescription!!)
            .addComponent(notifyOnTurnCompleteBox!!)
            .addComponent(notifyOnApprovalPendingBox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        if (panel == null) return false
        val settings = AgentSettingsState.getInstance()
        return agentPathSelection?.configuredPath != settings.agentExecutablePath ||
            notifyOnTurnCompleteBox?.isSelected != settings.notifyOnTurnComplete ||
            notifyOnApprovalPendingBox?.isSelected != settings.notifyOnApprovalPending
    }

    override fun apply() {
        val selection = agentPathSelection ?: return
        val settings = AgentSettingsState.getInstance()
        settings.agentExecutablePath = selection.configuredPath
        settings.notifyOnTurnComplete = notifyOnTurnCompleteBox?.isSelected == true
        settings.notifyOnApprovalPending = notifyOnApprovalPendingBox?.isSelected == true
        selection.reset(settings.agentExecutablePath)
        showAgentPathSelection()
    }

    override fun reset() {
        val settings = AgentSettingsState.getInstance()
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
        panel = null
        agentPathField = null
        agentPathSelection = null
        agentPathDescription = null
        notifyOnTurnCompleteBox = null
        notifyOnApprovalPendingBox = null
    }
}
