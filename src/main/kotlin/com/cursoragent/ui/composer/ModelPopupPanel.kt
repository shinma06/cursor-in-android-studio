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
import javax.swing.DefaultListCellRenderer
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
    lastManualId: String?,
    private val onSelect: (ModelOption) -> Unit,
    private val onClose: () -> Unit,
    private val onResize: () -> Unit,
    private val rowLabel: (ModelOption) -> String = { it.displayName() },
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
    val autoToggle = AutoToggle()
    private val listModel = DefaultListModel<ModelOption>()
    val modelList = JList(listModel)
    private val manualOptions = options.filterNot { it.id == "auto" }
    private val autoOption = options.find { it.id == "auto" }
    private var currentId = selectedId
    private var lastManual = manualOptions.find { it.id == lastManualId }
        ?: manualOptions.find { it.id == selectedId } ?: manualOptions.firstOrNull()
    private val description = JLabel("<html>品質と速度のバランスを考慮して<br>モデルを自動で選択します</html>")
    private val results = JPanel(BorderLayout())
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
        if (autoOption != null) {
            val autoRow = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
                background = AgentUiColors.userBubbleBackground
                border = JBUI.Borders.empty(5, 6)
                val text = JPanel(BorderLayout(0, JBUI.scale(3))).apply {
                    isOpaque = false
                    add(JLabel("Auto").apply { font = AgentUiMetrics.textFont() }, BorderLayout.NORTH)
                    description.font = AgentUiMetrics.textFont()
                    description.foreground = AgentUiColors.mutedText
                    add(description, BorderLayout.CENTER)
                }
                add(text, BorderLayout.CENTER)
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(autoToggle, BorderLayout.NORTH)
                }, BorderLayout.EAST)
            }
            top.add(autoRow, BorderLayout.CENTER)
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
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                    val option = value as ModelOption
                    val label = super.getListCellRendererComponent(list, rowLabel(option), index, selected, focus) as JLabel
                    label.border = JBUI.Borders.empty(5, 6)
                    return JPanel(BorderLayout()).apply {
                        background = label.background
                        add(label, BorderLayout.CENTER)
                        add(JLabel(if (option.id == currentId) "✓" else " ").apply {
                            foreground = label.foreground
                            border = JBUI.Borders.empty(0, 8)
                        }, BorderLayout.EAST)
                        toolTipText = "${option.label} — ${option.id}"
                    }
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
        add(results, BorderLayout.CENTER)
        autoToggle.addActionListener {
            val option = if (autoToggle.isSelected) autoOption else lastManual
            if (option != null) {
                currentId = option.id
                onSelect(option)
            }
            refresh()
        }
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
        val auto = currentId == "auto" && autoOption != null
        val query = searchField.text.trim()
        autoToggle.isSelected = auto
        autoToggle.isEnabled = !auto || manualOptions.isNotEmpty()
        description.isVisible = auto
        results.isVisible = !auto || query.isNotEmpty()
        val previous = modelList.selectedValue?.id
        listModel.clear()
        manualOptions.filter { matchesQuery(it, query) }
            .forEach(listModel::addElement)
        val index = (0 until listModel.size()).firstOrNull { listModel[it].id == (previous ?: currentId) }
        modelList.selectedIndex = index ?: if (listModel.isEmpty) -1 else 0
        results.removeAll()
        results.add(if (listModel.isEmpty) noResults else scrollPane, BorderLayout.CENTER)
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
        lastManual = option
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
        getAccessibleContext().accessibleName = "Auto model"
        toolTipText = "Automatically select a model"
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
