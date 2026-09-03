package com.cursoragent.ui.mcp

import com.cursoragent.service.AgentProcessService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Read-only listing (F-70) + enable/disable (F-71) for `agent mcp` servers.
 * Shows `agent mcp list`'s raw output rather than a parsed table — its format was
 * never verified against a populated `.cursor/mcp.json` (see requirements doc §13,
 * no MCP servers were configured on the machine this was built on), only against
 * the empty-config message. Enable/disable just shells out to `agent mcp enable/
 * disable <id>`, which doesn't depend on being able to parse the list output.
 */
class McpServersDialog(private val project: Project) : DialogWrapper(project) {
    private val agentService = project.getService(AgentProcessService::class.java)
    private val outputArea = JBTextArea(12, 60).apply { isEditable = false; lineWrap = true }
    private val identifierField = JBTextField(20)
    private val statusLabel = JBLabel(" ")

    init {
        title = "MCP Servers"
        init()
        refresh()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = JBUI.size(560, 360)
        panel.add(JBScrollPane(outputArea), BorderLayout.CENTER)

        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JBLabel("Server id:"))
            add(identifierField)
            add(JButton("Enable").apply { addActionListener { toggle(enable = true) } })
            add(JButton("Disable").apply { addActionListener { toggle(enable = false) } })
        }

        panel.add(
            JPanel(BorderLayout()).apply {
                add(actionRow, BorderLayout.NORTH)
                add(statusLabel, BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )
        return panel
    }

    override fun createActions() = arrayOf(okAction)

    private fun refresh() {
        outputArea.text = "Loading…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val raw = agentService.listMcpServersRaw()
            SwingUtilities.invokeLater { outputArea.text = raw }
        }
    }

    private fun toggle(enable: Boolean) {
        val identifier = identifierField.text.trim()
        if (identifier.isEmpty()) return
        statusLabel.text = "Working…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val ok = agentService.setMcpServerEnabled(identifier, enable)
            SwingUtilities.invokeLater {
                statusLabel.text = if (ok) {
                    "${if (enable) "Enabled" else "Disabled"} $identifier"
                } else {
                    "Failed to ${if (enable) "enable" else "disable"} $identifier"
                }
                refresh()
            }
        }
    }
}
