package com.cursoragent.settings

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

    override fun getDisplayName(): String = "Cursor Agent"

    override fun createComponent(): JComponent {
        val settings = AgentSettingsState.getInstance()

        agentPathField = TextFieldWithBrowseButton().apply {
            text = settings.agentExecutablePath
            addBrowseFolderListener(
                "Select cursor-agent executable",
                "Path to the `agent` CLI executable",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
        }

        notifyOnTurnCompleteBox = JBCheckBox("Notify when a turn completes", settings.notifyOnTurnComplete)
        notifyOnApprovalPendingBox = JBCheckBox(
            "Notify when a tool call starts",
            settings.notifyOnApprovalPending,
        )

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Agent executable path:", agentPathField!!)
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
