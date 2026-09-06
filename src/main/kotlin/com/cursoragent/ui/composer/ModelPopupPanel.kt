package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal fun ModelOption.displayName(): String = label
    .replace(" (current, default)", "").replace(" (default)", "").replace(" (current)", "")

/** Popup contents are independent of the IDE popup so selection and filtering can be verified on EDT. */
internal class ModelPopupPanel(
    private val options: List<ModelOption>,
    selectedId: String,
    private val onSelect: (ModelOption) -> Unit,
    private val onClose: () -> Unit,
    private val onResize: () -> Unit,
    private val rowLabel: (ModelOption) -> String = { it.displayName() },
    private val rowDetail: (ModelOption) -> String = { "" },
    private val matchesQuery: (ModelOption, String) -> Boolean = { option, query ->
        option.label.contains(query, true) || option.id.contains(query, true)
    },
) : JPanel(BorderLayout()) {
    val searchField = object : JTextField() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (text.isEmpty()) {
                g.color = AgentUiColors.mutedText
                g.font = font
                g.drawString("Search models", insets.left, (height - g.fontMetrics.height) / 2 + g.fontMetrics.ascent)
            }
        }
    }
    private val listModel = DefaultListModel<ModelOption>()
    val modelList = JList(listModel)
    private var currentId = selectedId
    private val resultCards = java.awt.CardLayout()
    private val results = JPanel(resultCards)
    private val noResults = JLabel("一致するモデルがありません").apply { border = JBUI.Borders.empty(12) }
    private val scrollPane = JScrollPane(modelList)

    init {
        background = AgentUiColors.panelBackground
        border = JBUI.Borders.empty(5)
        searchField.apply {
            font = AgentUiMetrics.textFont()
            background = this@ModelPopupPanel.background
            border = JBUI.Borders.empty(5, 6, 8, 6)
            preferredSize = JBUI.size(300, 28)
            getAccessibleContext().accessibleName = "Search models"
        }
        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(searchField, BorderLayout.NORTH)
        }
        add(top, BorderLayout.NORTH)
        modelList.apply {
            font = AgentUiMetrics.textFont()
            background = this@ModelPopupPanel.background
            selectionBackground = AgentUiColors.userBubbleBackground
            selectionForeground = foreground
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = JBUI.scale(28)
            getAccessibleContext().accessibleName = "Models"
            cellRenderer = object : javax.swing.ListCellRenderer<ModelOption> {
                private val nameLabel = JLabel()
                private val detail = JLabel()
                private val check = JLabel().apply { border = JBUI.Borders.empty(0, 8) }
                private val row = JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(4, 6)
                    add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                        isOpaque = false
                        add(nameLabel)
                        add(detail)
                    }, BorderLayout.CENTER)
                    add(check, BorderLayout.EAST)
                }

                override fun getListCellRendererComponent(list: JList<out ModelOption>, option: ModelOption, index: Int,
                    selected: Boolean, focus: Boolean): java.awt.Component {
                    // Return one renderer tree: CellRendererPane retains components used for measurement.
                    row.background = if (selected) list.selectionBackground else list.background
                    nameLabel.text = rowLabel(option)
                    nameLabel.font = list.font
                    nameLabel.foreground = list.foreground
                    detail.text = rowDetail(option)
                    detail.font = list.font
                    detail.foreground = AgentUiColors.mutedText
                    check.text = if (option.id == currentId) "✓" else " "
                    check.font = list.font
                    check.foreground = list.foreground
                    row.toolTipText = "${option.label} — ${option.id}"
                    row.border = if (option.id == "auto")
                        BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AgentUiColors.bubbleBorder), JBUI.Borders.empty(4, 6))
                    else JBUI.Borders.empty(4, 6)
                    return row
                }
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return
                    val index = locationToIndex(e.point)
                    if (index >= 0 && getCellBounds(index, index).contains(e.point)) {
                        selectedIndex = index
                        chooseHighlighted()
                    }
                }
            })
            // JList only asks renderer tooltips when registered with the tooltip manager.
            toolTipText = "Models"
        }
        scrollPane.apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, AgentUiColors.bubbleBorder)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            viewport.background = background
        }
        results.isOpaque = false
        results.add(scrollPane, "models")
        results.add(noResults, "empty")
        add(results, BorderLayout.CENTER)
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refresh()
            override fun removeUpdate(e: DocumentEvent) = refresh()
            override fun changedUpdate(e: DocumentEvent) = refresh()
        })
        bind(searchField, "DOWN", "next") { moveSelection(1) }
        bind(searchField, "UP", "previous") { moveSelection(-1) }
        bind(searchField, "ENTER", "choose") { chooseHighlighted() }
        bind(modelList, "ENTER", "choose") { chooseHighlighted() }
        bind(this, "ESCAPE", "cancel", JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, onClose)
        refresh()
    }

    private fun refresh() {
        val previous = modelList.selectedValue?.id
        val query = searchField.text.trim()
        listModel.clear()
        listModel.addAll(options.filter { matchesQuery(it, query) })
        val index = (0 until listModel.size()).firstOrNull { listModel[it].id == (previous ?: currentId) }
        modelList.selectedIndex = index ?: if (listModel.isEmpty) -1 else 0
        resultCards.show(results, if (listModel.isEmpty) "empty" else "models")
        scrollPane.preferredSize = Dimension(JBUI.scale(300), minOf(listModel.size(), 8) * JBUI.scale(28) + JBUI.scale(2))
        revalidate()
        repaint()
        onResize()
    }

    internal fun moveSelection(delta: Int) {
        if (!results.isVisible || listModel.isEmpty) return
        modelList.selectedIndex = (modelList.selectedIndex + delta).coerceIn(0, listModel.size() - 1)
        modelList.ensureIndexIsVisible(modelList.selectedIndex)
    }

    internal fun chooseHighlighted() {
        if (!results.isVisible) return
        val option = modelList.selectedValue ?: return
        currentId = option.id
        onSelect(option)
        onClose()
    }

    private fun bind(component: JComponent, key: String, name: String, condition: Int = JComponent.WHEN_FOCUSED, action: () -> Unit) {
        component.getInputMap(condition).put(KeyStroke.getKeyStroke(key), name)
        component.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = action()
        })
    }
}

internal class AutoToggle : JToggleButton() {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        preferredSize = JBUI.size(34, 22)
        getAccessibleContext().accessibleName = "モデルオプション"
    }

    override fun paintComponent(g: Graphics) {
        val copy = g.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val h = JBUI.scale(20)
            val y = (height - h) / 2
            copy.color = if (isSelected) JBColor(Color(0x288750), Color(0x3CA36C)) else AgentUiColors.bubbleBorder
            copy.fillRoundRect(0, y, width, h, h, h)
            copy.color = Color.WHITE
            val diameter = h - JBUI.scale(4)
            val x = if (isSelected) width - diameter - JBUI.scale(2) else JBUI.scale(2)
            copy.fillOval(x, y + JBUI.scale(2), diameter, diameter)
            if (hasFocus()) {
                copy.color = foreground
                copy.drawRoundRect(0, y, width - 1, h, h, h)
            }
        } finally {
            copy.dispose()
        }
    }
}
