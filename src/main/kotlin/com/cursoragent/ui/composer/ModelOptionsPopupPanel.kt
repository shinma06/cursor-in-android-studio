package com.cursoragent.ui.composer

import com.cursoragent.service.ModelOption
import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.SwingConstants

/** The option card stays in place while a searchable model card opens beside it. */
internal class ModelOptionsPopupPanel(
    private val models: List<ModelOption>,
    selectedId: String,
    lastManualId: String?,
    private val onSelect: (ModelOption) -> Unit,
    private val onClose: () -> Unit,
    private val onResize: () -> Unit,
    private val onShowModels: (ModelPopupPanel, Boolean) -> Unit = { _, _ -> },
    private val onHideModels: () -> Unit = {},
    private val onModelsResize: () -> Unit = onResize,
) : JPanel(BorderLayout()) {
    internal val families = modelFamilies(models)
    internal var currentId = selectedId
        private set
    private var lastManual = lastManualId
    private val remembered = mutableMapOf<String, String>()
    internal val optionControls = linkedMapOf<ModelAxis, JComponent>()
    internal var picker: ModelPopupPanel? = null
        private set
    internal var choiceList: JList<ModelVariant>? = null
        private set
    internal lateinit var modelRow: SelectorButton
        private set
    internal var focusTarget: JComponent = this
        private set

    init {
        background = AgentUiColors.panelBackground
        border = JBUI.Borders.empty(5)
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "close")
        actionMap.put("close", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = onClose()
        })
        // Keep the variant that was active before this popup opened, including Auto's fallback.
        val initialManual = currentId.takeUnless { it == "auto" } ?: lastManual
        families.find { family -> family.variants.any { it.option.id == initialManual } }?.let {
            remembered[it.id] = initialManual!!
        }
        showOptions()
    }

    private fun family(): ModelFamily? = families.find { it.variants.any { v -> v.option.id == currentId } }
    private fun variant(): ModelVariant? = family()?.variants?.find { it.option.id == currentId }

    internal fun showOptions() {
        val family = family()
        val current = variant()
        hideModels()
        picker = null
        choiceList = null
        optionControls.clear()
        val rows = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        if (currentId == "auto") {
            rows.add(JLabel("<html>品質と速度のバランスを重視し、<br>多くのタスクに適したモデルを選びます</html>").apply {
                font = AgentUiMetrics.textFont()
                border = JBUI.Borders.empty(8, 7, 12, 7)
                alignmentX = Component.LEFT_ALIGNMENT
            })
        }
        for (axis in ModelAxis.entries) {
            if (family == null || current == null) break
            val choices = family.choices(current, axis)
            if (choices.size < 2) continue
            val control = if (axis == ModelAxis.THINKING || axis == ModelAxis.FAST) {
                val toggle = AutoToggle().apply {
                    isSelected = current.value(axis) == "true"
                    toolTipText = axis.help
                    getAccessibleContext().accessibleName = axis.caption
                    addActionListener {
                        selectOption(axis, (!current.value(axis).toBoolean()).toString())
                    }
                }
                rows.add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(3, 7)
                    alignmentX = Component.LEFT_ALIGNMENT
                    preferredSize = JBUI.size(300, 32)
                    add(JLabel(axis.caption).apply {
                        font = AgentUiMetrics.textFont()
                        toolTipText = axis.help
                        labelFor = toggle
                    }, BorderLayout.CENTER)
                    add(toggle, BorderLayout.EAST)
                })
                toggle
            } else {
                menuRow(axis.caption, optionValueLabel(axis, current.value(axis)), axis.help) { showChoices(axis) }
                    .also(rows::add)
            }
            control.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) = hideModels()
            })
            optionControls[axis] = control
        }
        if (optionControls.isNotEmpty() || currentId == "auto") {
            rows.add(JSeparator().apply { alignmentX = Component.LEFT_ALIGNMENT })
        }
        modelRow = menuRow("Model", family?.name ?: "Auto", "ホバーまたはクリックでモデル一覧を開きます。") { showModels() }
        modelRow.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) = showModels(requestFocus = false)
        })
        modelRow.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "models")
        modelRow.actionMap.put("models", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = showModels()
        })
        rows.add(modelRow)
        val initial = optionControls.values.firstOrNull() ?: modelRow
        showPage(rows, initial)
    }

    /** Explicit choices always resolve to one of the IDs returned by the CLI. */
    internal fun selectOption(axis: ModelAxis, value: String?) {
        val current = variant() ?: return
        val target = family()?.choices(current, axis)?.firstOrNull { it.value(axis) == value } ?: return
        select(target.option)
        showOptions()
    }

    private fun select(option: ModelOption) {
        currentId = option.id
        if (option.id != "auto") {
            lastManual = option.id
            family()?.let { remembered[it.id] = option.id }
        }
        onSelect(option)
    }

    internal fun showModels(requestFocus: Boolean = true) {
        picker?.let {
            if (requestFocus) it.searchField.requestFocusInWindow()
            return
        }
        val representatives = families.map { family ->
            family.variants.firstOrNull { it.option.id == currentId }
                ?: family.variants.firstOrNull { it.option.id == remembered[family.id] || it.option.id == lastManual }
                ?: family.variants.first()
        }.map { it.option }
        val byId = representatives.mapIndexed { index, option -> option.id to families[index] }.toMap()
        val options = models.filter { it.id == "auto" } + representatives
        val selected = if (currentId == "auto") "auto" else representatives.find { it.id == currentId }?.id.orEmpty()
        val page = ModelPopupPanel(options, selected, { select(it); showOptions() }, { hideModels() },
            onResize = onModelsResize,
            rowLabel = { byId[it.id]?.name ?: it.displayName() },
            rowDetail = { option ->
                val f = byId[option.id]
                val v = f?.variants?.find { it.option.id == option.id }
                if (f != null && v != null) f.selectionLabel(v).removePrefix(f.name).trim() else ""
            },
            matchesQuery = { option, query -> byId[option.id]?.matches(query) ?: option.label.contains(query, true) },
        )
        picker = page
        onShowModels(page, requestFocus)
        page.modelList.ensureIndexIsVisible(page.modelList.selectedIndex)
    }

    internal fun hideModels() {
        val wasOpen = picker != null
        picker = null
        onHideModels()
        if (wasOpen && isShowing) modelRow.requestFocusInWindow()
    }

    private fun showChoices(axis: ModelAxis) {
        hideModels()
        val current = variant() ?: return
        val choices = family()?.choices(current, axis).orEmpty()
        val list = JList(choices.toTypedArray()).apply {
            background = AgentUiColors.panelBackground
            foreground = this@ModelOptionsPopupPanel.foreground
            font = AgentUiMetrics.textFont()
            selectionBackground = AgentUiColors.userBubbleBackground
            selectionForeground = foreground
            fixedCellHeight = JBUI.scale(28)
            selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
            setSelectedValue(current, true)
            cellRenderer = javax.swing.ListCellRenderer { _, value, _, highlighted, _ ->
                JPanel(BorderLayout()).apply {
                    background = if (highlighted) AgentUiColors.userBubbleBackground else AgentUiColors.panelBackground
                    border = JBUI.Borders.empty(4, 8)
                    add(JLabel(optionValueLabel(axis, value.value(axis))).apply { font = AgentUiMetrics.textFont() }, BorderLayout.CENTER)
                    add(JLabel(if (value.option.id == currentId) "✓" else ""), BorderLayout.EAST)
                }
            }
            getAccessibleContext().accessibleName = axis.caption
        }
        val choose = { list.selectedValue?.let { selectOption(axis, it.value(axis)) }; Unit }
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return
                val index = list.locationToIndex(e.point)
                if (index >= 0 && list.getCellBounds(index, index).contains(e.point)) choose()
            }
        })
        list.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "select")
        list.actionMap.put("select", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = choose()
        })
        list.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "back")
        list.actionMap.put("back", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = showOptions()
        })
        choiceList = list
        val scroll = JScrollPane(list).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = JBUI.size(300, minOf(choices.size, 8) * 28)
        }
        showPage(withBack(axis.caption, scroll) { showOptions() }, list)
    }

    private fun withBack(title: String, content: JComponent, back: () -> Unit) = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(menuRow("‹  $title", "", "モデルオプションに戻ります。", back), BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }

    private fun showPage(page: JComponent, focus: JComponent) {
        removeAll()
        add(page, BorderLayout.CENTER)
        focusTarget = focus
        revalidate()
        repaint()
        onResize()
        if (isShowing) focus.requestFocusInWindow()
    }

    private fun menuRow(caption: String, value: String, help: String, action: () -> Unit): SelectorButton =
        SelectorButton().apply {
            // Child labels share the button's full row hit area and keyboard action.
            layout = BorderLayout(JBUI.scale(14), 0)
            text = ""
            preferredSize = JBUI.size(300, 32)
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(32))
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 7)
            add(JLabel(caption).apply { font = AgentUiMetrics.textFont() }, BorderLayout.WEST)
            add(JLabel(if (value.isEmpty()) "" else "$value  ›", SwingConstants.RIGHT).apply {
                font = AgentUiMetrics.textFont()
                foreground = AgentUiColors.mutedText
            }, BorderLayout.CENTER)
            toolTipText = help
            getAccessibleContext().accessibleName = "$caption: $value"
            addActionListener { action() }
        }
}
