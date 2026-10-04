package com.cursoragent.ui.composer

import com.cursoragent.service.AgentEvent
import com.cursoragent.service.ModelOption
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JPanel

/** Native selectors use only this immutable advertisement; each choice is confirmed by the provider. */
internal class AcpModelOptionsPanel(
    state: AgentEvent.Configuration,
    onSelect: (String, String) -> Unit,
) : JPanel(GridBagLayout()) {
    internal val selectors = linkedMapOf<String, ComboBox<ModelOption>>()

    init {
        border = JBUI.Borders.empty(8)
        val rows = listOf(Triple("model", "Model", state.model) to state.models) +
            state.parameters.map { Triple(it.id, it.name, it.currentValue) to it.options }
        rows.forEachIndexed { index, (option, values) ->
            val (id, name, current) = option
            val label = JLabel(name).apply { putClientProperty("html.disable", true) }
            val selector = ComboBox(values.toTypedArray()).apply {
                renderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, row: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                        super.getListCellRendererComponent(list, (value as? ModelOption)?.label.orEmpty(), row, selected, focus)
                        putClientProperty("html.disable", true)
                        return this
                    }
                }
                selectedItem = values.firstOrNull { it.id == current }
                accessibleContext.accessibleName = name
                isEnabled = values.size > 1
                addActionListener {
                    (selectedItem as? ModelOption)?.takeIf { it.id != current }?.let { onSelect(id, it.id) }
                }
            }
            selectors[id] = selector
            label.labelFor = selector
            add(label, GridBagConstraints().apply { gridx = 0; gridy = index; anchor = GridBagConstraints.WEST; insets = JBUI.insets(3, 0, 3, 8) })
            add(selector, GridBagConstraints().apply { gridx = 1; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(3, 0) })
        }
    }
}
