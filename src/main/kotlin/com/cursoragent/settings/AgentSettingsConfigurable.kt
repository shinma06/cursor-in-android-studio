package com.cursoragent.settings

import com.cursoragent.PluginBrand
import com.cursoragent.ui.ImmediateEditNotice
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AgentSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var notifyOnTurnCompleteBox: JBCheckBox? = null
    private var notifyOnApprovalPendingBox: JBCheckBox? = null

    override fun getDisplayName(): String = PluginBrand.NAME

    override fun createComponent(): JComponent {
        val settings = AgentSettingsState.getInstance()

        agentPathField = TextFieldWithBrowseButton().apply {
            text = settings.agentExecutablePath
            addBrowseFolderListener(
                "cursor-agentの実行ファイルを選択",
                "agentコマンドの実行ファイルを指定してください",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
        }

        notifyOnTurnCompleteBox = JBCheckBox("応答が完了したら通知する", settings.notifyOnTurnComplete)
        notifyOnApprovalPendingBox = JBCheckBox(
            "ツールの実行が始まったら通知する",
            settings.notifyOnApprovalPending,
        )

        panel = FormBuilder.createFormBuilder()
            .addComponent(ImmediateEditNotice())
            .addLabeledComponent("CLIの実行ファイル:", agentPathField!!)
            .addComponent(notifyOnTurnCompleteBox!!)
            .addComponent(notifyOnApprovalPendingBox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsState.getInstance()
        return agentPathField?.text != settings.agentExecutablePath ||
            notifyOnTurnCompleteBox?.isSelected != settings.notifyOnTurnComplete ||
            notifyOnApprovalPendingBox?.isSelected != settings.notifyOnApprovalPending
    }

    override fun apply() {
        val settings = AgentSettingsState.getInstance()
        settings.agentExecutablePath = agentPathField?.text.orEmpty()
        settings.notifyOnTurnComplete = notifyOnTurnCompleteBox?.isSelected == true
        settings.notifyOnApprovalPending = notifyOnApprovalPendingBox?.isSelected == true
    }

    override fun reset() {
        val settings = AgentSettingsState.getInstance()
        agentPathField?.text = settings.agentExecutablePath
        notifyOnTurnCompleteBox?.isSelected = settings.notifyOnTurnComplete
        notifyOnApprovalPendingBox?.isSelected = settings.notifyOnApprovalPending
    }
}
