package com.cursoragent.ui.session

import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.AgentUiMetrics
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.ScrollPaneLayout
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicScrollBarUI

/**
 * Controlled Swing tab strip. Call setTabs on the EDT after handling ID-based callbacks.
 * No CLI, persisted settings, global event listener or desktop-wide drag registration.
 */
class SessionTabStrip : JPanel(BorderLayout()) {
    var onSelect: (String) -> Unit = {}
    var onClose: (String) -> Unit = {}
    /** Destination is the final index after removing the dragged tab. */
    var onMove: (String, Int) -> Unit = { _, _ -> }

    private var tabs: List<SessionTabPresentation> = emptyList()
    private var selectedId: String? = null
    private var hoveredId: String? = null
    private var areaHovered = false
    private var pointerInViewport: Point? = null
    private var pressedId: String? = null
    private var pressedClose = false
    private var pressPoint = Point()
    private var dragging = false
    private var dragViewportPoint = Point()
    private var dropIndex = 0
    private var wrapTabs = false
    private val tabHeight get() = JBUI.scale(34)
    private val edgeWidth get() = JBUI.scale(24)
    private val canvas = TabCanvas()
    internal val scrollPane = object : JScrollPane(canvas) {
        // The scrollbar overlaps the viewport, so repaint must include both siblings.
        override fun isOptimizedDrawingEnabled() = false
    }.apply {
        layout = object : ScrollPaneLayout() {
            override fun layoutContainer(parent: Container) {
                super.layoutContainer(parent)
                if (hsb.isVisible) {
                    val bounds = viewport.bounds
                    bounds.height += hsb.height
                    viewport.bounds = bounds
                    hsb.setLocation(bounds.x, bounds.y + bounds.height - hsb.height)
                }
            }
        }
        setComponentZOrder(horizontalScrollBar, 0)
        border = JBUI.Borders.empty()
        viewportBorder = JBUI.Borders.empty()
        viewport.background = AgentUiColors.tabAreaBackground
        // Blitting viewport pixels would also move the overlapping scrollbar's old pixels.
        viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBar.unitIncrement = JBUI.scale(28)
        horizontalScrollBar.preferredSize = Dimension(0, JBUI.scale(3))
        horizontalScrollBar.setUI(object : BasicScrollBarUI() {
            override fun createDecreaseButton(orientation: Int) = invisibleArrow()
            override fun createIncreaseButton(orientation: Int) = invisibleArrow()
            override fun paintTrack(g: Graphics, c: JComponent, bounds: Rectangle) {
                // Transparent track leaves the selected tab joined to the chat background.
            }
            override fun paintThumb(g: Graphics, c: JComponent, bounds: Rectangle) {
                if (!areaHovered || !scrollbar.isEnabled) return
                val color = AgentUiColors.mutedText
                g.color = Color(color.red, color.green, color.blue, 90)
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
            }
            private fun invisibleArrow() = JButton().apply {
                preferredSize = Dimension(0, 0)
                minimumSize = preferredSize
                maximumSize = preferredSize
                isFocusable = false
            }
        })
        horizontalScrollBar.isOpaque = false
        isOpaque = false
    }
    private val edgeTimer = Timer(40) { scrollDragEdge() }

    init {
        isOpaque = true
        background = AgentUiColors.tabAreaBackground
        minimumSize = Dimension(0, tabHeight)
        add(scrollPane)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (wrapTabs) {
                    cancelDrag()
                    refreshHoveredTab()
                    revalidate()
                    revealSelectedTab()
                }
            }
        })
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !isShowing) {
                cancelDrag()
                hoveredId = null
                pointerInViewport = null
                areaHovered = false
                scrollPane.horizontalScrollBar.repaint()
            }
        }
        scrollPane.viewport.addChangeListener { refreshHoveredTab() }
        val areaListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = updateAreaHover(e)
            override fun mouseExited(e: MouseEvent) = updateAreaHover(e)
        }
        listOf(this, scrollPane, scrollPane.viewport, scrollPane.horizontalScrollBar).forEach {
            it.addMouseListener(areaListener)
        }
        // Scroll wrapped rows vertically, retaining the one-row horizontal wheel behavior.
        val wheelListener = java.awt.event.MouseWheelListener { e ->
            val bar = if (wrapTabs) scrollPane.verticalScrollBar else scrollPane.horizontalScrollBar
            if (bar.isVisible) {
                bar.value += (e.preciseWheelRotation * bar.unitIncrement * e.scrollAmount).toInt()
                e.consume()
            }
        }
        canvas.addMouseWheelListener(wheelListener)
        scrollPane.horizontalScrollBar.addMouseWheelListener(wheelListener)
    }

    fun setWrapTabs(enabled: Boolean) {
        if (wrapTabs == enabled) return
        wrapTabs = enabled
        cancelDrag()
        scrollPane.horizontalScrollBarPolicy = if (enabled) {
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        } else JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBarPolicy = if (enabled) {
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        } else JScrollPane.VERTICAL_SCROLLBAR_NEVER
        scrollPane.viewport.viewPosition = Point()
        refreshHoveredTab()
        canvas.revalidate()
        revalidate()
        repaint()
        revealSelectedTab()
    }

    fun setTabs(tabs: List<SessionTabPresentation>, selectedId: String?) {
        require(tabs.map { it.id }.distinct().size == tabs.size) { "Duplicate tab ID" }
        require(tabs.none { it.id.isBlank() }) { "Blank tab ID" }
        require((tabs.isEmpty() && selectedId == null) || tabs.any { it.id == selectedId }) { "Unknown selected tab" }
        val previousSelectedId = this.selectedId
        val previousSelectedBounds = boundsFor(previousSelectedId)
        this.tabs = tabs.toList()
        this.selectedId = selectedId
        val revealSelection = previousSelectedId != selectedId || previousSelectedBounds != boundsFor(selectedId)
        if (pressedId != null && tabs.none { it.id == pressedId }) cancelDrag()
        refreshHoveredTab()
        canvas.getAccessibleContext().accessibleName = tabs.firstOrNull { it.id == selectedId }?.let {
            "セッションタブ: ${it.fullTitle}"
        } ?: "セッションタブ"
        canvas.revalidate()
        revalidate()
        canvas.repaint()
        if (revealSelection) revealSelectedTab()
    }

    private fun revealSelectedTab() = SwingUtilities.invokeLater {
        boundsFor(selectedId)?.let(canvas::scrollRectToVisible)
    }

    internal fun boundsFor(id: String?): Rectangle? = tabBounds().firstOrNull { it.first.id == id }?.second
    internal fun closeBoundsFor(id: String): Rectangle? = boundsFor(id)?.let(::closeBounds)
    internal val eventTarget: JComponent get() = canvas
    internal fun closeVisible(id: String): Boolean = id == selectedId || id == hoveredId
    internal val scrollbarRevealed: Boolean get() = areaHovered
    internal val dragAutoScrollRunning: Boolean get() = edgeTimer.isRunning

    private fun tabBounds(): List<Pair<SessionTabPresentation, Rectangle>> {
        val metrics = canvas.getFontMetrics(canvas.font)
        val fullWidth = (width - (toolbar()?.preferredSize?.width ?: 0)).coerceAtLeast(1)
        fun arrange(availableWidth: Int): List<Pair<SessionTabPresentation, Rectangle>> {
            var x = 0
            var y = 0
            return tabs.map { tab ->
                val title = abbreviateSessionTitle(tab.fullTitle, metrics, sessionTitleWidth(metrics))
                val naturalWidth = (metrics.stringWidth(title) + JBUI.scale(66)).coerceAtLeast(JBUI.scale(112))
                val tabWidth = if (wrapTabs) naturalWidth.coerceAtMost(availableWidth) else naturalWidth
                if (wrapTabs && x > 0 && x + tabWidth > availableWidth) {
                    x = 0
                    y += tabHeight
                }
                (tab to Rectangle(x, y, tabWidth, tabHeight)).also { x += tabWidth }
            }
        }
        val withoutScrollbar = arrange(fullWidth)
        val rowsHeight = withoutScrollbar.lastOrNull()?.second?.let { it.y + it.height } ?: tabHeight
        // Decide from the full width first: current scrollbar visibility depends on these same rows.
        return if (wrapTabs && rowsHeight > (wrappedHeightLimit() ?: Int.MAX_VALUE)) {
            arrange((fullWidth - scrollPane.verticalScrollBar.preferredSize.width).coerceAtLeast(1))
        } else withoutScrollbar
    }

    private fun closeBounds(bounds: Rectangle) = Rectangle(
        bounds.x + bounds.width - JBUI.scale(28), bounds.y + JBUI.scale(7), JBUI.scale(22), JBUI.scale(22),
    )

    private fun hit(point: Point): SessionTabPresentation? = tabBounds().firstOrNull { it.second.contains(point) }?.first

    private fun updateAreaHover(e: MouseEvent) {
        val point = SwingUtilities.convertPoint(e.component, e.point, scrollPane)
        areaHovered = scrollPane.contains(point)
        scrollPane.horizontalScrollBar.repaint()
    }

    private fun refreshHoveredTab() {
        hoveredId = pointerInViewport?.let { point ->
            val position = scrollPane.viewport.viewPosition
            hit(Point(point.x + position.x, point.y + position.y))?.id
        }
        canvas.repaint()
        repaint() // The viewport position also controls the fixed toolbar divider.
    }

    private fun cancelDrag() {
        edgeTimer.stop()
        pressedId = null
        pressedClose = false
        dragging = false
        canvas.repaint()
    }

    private fun updateDropTarget() {
        val position = scrollPane.viewport.viewPosition
        val point = Point(dragViewportPoint.x + position.x, dragViewportPoint.y + position.y)
        dropIndex = tabBounds().filter { it.first.id != pressedId }.count { (_, bounds) ->
            if (wrapTabs) {
                point.y >= bounds.y + bounds.height || point.y >= bounds.y && point.x > bounds.centerX
            } else point.x > bounds.centerX
        }
        canvas.repaint()
    }

    private fun scrollDragEdge() {
        if (!dragging) return
        val bar = if (wrapTabs) scrollPane.verticalScrollBar else scrollPane.horizontalScrollBar
        val pointer = if (wrapTabs) dragViewportPoint.y else dragViewportPoint.x
        val extent = if (wrapTabs) scrollPane.viewport.height else scrollPane.viewport.width
        val direction = when {
            pointer < edgeWidth -> -1
            pointer > extent - edgeWidth -> 1
            else -> 0
        }
        bar.value += direction * JBUI.scale(12)
        updateDropTarget()
    }

    private fun toolbar(): java.awt.Component? =
        (layout as BorderLayout).getLayoutComponent(BorderLayout.EAST)?.takeIf { it.isVisible }

    private fun wrappedHeightLimit(): Int? = parent?.takeIf {
        (it.layout as? BorderLayout)?.getLayoutComponent(BorderLayout.NORTH) === this
    }?.let { it.height - it.insets.top - it.insets.bottom }?.takeIf { it > 0 }?.div(2)

    override fun getPreferredSize(): Dimension {
        val toolbar = toolbar()?.preferredSize
        val rowsHeight = tabBounds().lastOrNull()?.second?.let { it.y + it.height } ?: tabHeight
        val naturalHeight = maxOf(rowsHeight, toolbar?.height ?: 0)
        // Leave at least half of the tool window for the chat and composer; scroll the remaining rows.
        val height = if (wrapTabs) minOf(naturalHeight, wrappedHeightLimit() ?: naturalHeight) else naturalHeight
        return Dimension(JBUI.scale(320) + (toolbar?.width ?: 0), height)
    }

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        val toolbar = (layout as BorderLayout).getLayoutComponent(BorderLayout.EAST) ?: return
        val viewport = scrollPane.viewport
        val tabEnd = tabBounds().filter { !wrapTabs || it.second.y == 0 }.lastOrNull()?.second?.let { it.x + it.width } ?: return
        if (toolbar.isVisible && viewport.width > 0 && tabEnd >= viewport.viewPosition.x + viewport.width) {
            val copy = g.create()
            try {
                copy.color = AgentUiColors.bubbleBorder
                copy.drawLine(toolbar.x, 0, toolbar.x, (if (wrapTabs) tabHeight else height) - 1)
            } finally {
                copy.dispose()
            }
        }
    }

    override fun removeNotify() {
        cancelDrag()
        hoveredId = null
        pointerInViewport = null
        areaHovered = false
        super.removeNotify()
    }

    private inner class TabCanvas : JPanel(), Scrollable {
        init {
            isOpaque = false
            isFocusable = true
            font = AgentUiMetrics.textFont()
            toolTipText = "セッションタブ"
            val mouse = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) = hover(e)
                override fun mouseMoved(e: MouseEvent) = hover(e)
                override fun mouseExited(e: MouseEvent) {
                    pointerInViewport = null
                    hoveredId = null
                    updateAreaHover(e)
                    repaint()
                }
                override fun mousePressed(e: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return
                    hover(e)
                    requestFocusInWindow()
                    pressedId = hit(e.point)?.id
                    pressedClose = pressedId?.let { closeVisible(it) && closeBoundsFor(it)?.contains(e.point) == true } == true
                    pressPoint = e.point
                }
                override fun mouseDragged(e: MouseEvent) {
                    hover(e)
                    if (pressedId == null || pressedClose) return
                    if (!dragging && pressPoint.distance(e.point) < JBUI.scale(5)) return
                    dragging = true
                    dragViewportPoint = SwingUtilities.convertPoint(this@TabCanvas, e.point, scrollPane.viewport)
                    updateDropTarget()
                    if ((!wrapTabs || scrollPane.verticalScrollBar.isVisible) && !edgeTimer.isRunning) edgeTimer.start()
                }
                override fun mouseReleased(e: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return
                    hover(e)
                    val id = pressedId
                    val wasDragging = dragging
                    val wasClose = pressedClose
                    val destination = dropIndex
                    cancelDrag()
                    if (id == null) return
                    when {
                        wasDragging -> if (tabs.indexOfFirst { it.id == id } != destination) onMove(id, destination)
                        hit(e.point)?.id != id -> Unit
                        wasClose -> if (closeBoundsFor(id)?.contains(e.point) == true) onClose(id)
                        else -> onSelect(id)
                    }
                }
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
            bind("LEFT", "previous") { chooseOffset(-1) }
            bind("RIGHT", "next") { chooseOffset(1) }
            bind("HOME", "first") { tabs.firstOrNull()?.let { onSelect(it.id) } }
            bind("END", "last") { tabs.lastOrNull()?.let { onSelect(it.id) } }
            bind("DELETE", "close") { selectedId?.let(onClose) }
            bind("alt shift LEFT", "moveLeft") { moveOffset(-1) }
            bind("alt shift RIGHT", "moveRight") { moveOffset(1) }
            bind("ESCAPE", "cancelDrag") { cancelDrag() }
        }

        private fun hover(e: MouseEvent) {
            pointerInViewport = SwingUtilities.convertPoint(this, e.point, scrollPane.viewport)
                .takeIf { scrollPane.viewport.contains(it) }
            refreshHoveredTab()
            updateAreaHover(e)
            repaint()
        }

        private fun bind(key: String, name: String, action: () -> Unit) {
            inputMap.put(KeyStroke.getKeyStroke(key), name)
            actionMap.put(name, object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) = action()
            })
        }

        private fun chooseOffset(offset: Int) {
            val index = tabs.indexOfFirst { it.id == selectedId }
            tabs.getOrNull(index + offset)?.let { onSelect(it.id) }
        }

        private fun moveOffset(offset: Int) {
            val index = tabs.indexOfFirst { it.id == selectedId }
            if (index >= 0 && index + offset in tabs.indices) onMove(tabs[index].id, index + offset)
        }

        override fun createToolTip(): javax.swing.JToolTip = super.createToolTip().apply {
            putClientProperty("html.disable", true)
        }

        override fun getToolTipText(e: MouseEvent): String? = hit(e.point)?.let { tab ->
            if (closeVisible(tab.id) && closeBoundsFor(tab.id)?.contains(e.point) == true) {
                "${tab.fullTitle}を閉じる"
            } else tab.fullTitle
        }

        override fun getPreferredSize(): Dimension {
            val bounds = tabBounds().map { it.second }
            return Dimension(bounds.maxOfOrNull { it.x + it.width } ?: 0, bounds.lastOrNull()?.let { it.y + it.height } ?: tabHeight)
        }
        override fun getPreferredScrollableViewportSize() = preferredSize
        override fun getScrollableTracksViewportWidth() = wrapTabs
        override fun getScrollableTracksViewportHeight() = !wrapTabs
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = JBUI.scale(28)
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) =
            (if (orientation == javax.swing.SwingConstants.VERTICAL) r.height else r.width).coerceAtLeast(1)

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val copy = g.create() as Graphics2D
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                for ((tab, bounds) in tabBounds()) {
                    if (!copy.clipBounds.intersects(bounds)) continue
                    val active = tab.id == selectedId
                    val paintedHeight = if (wrapTabs) bounds.height else height
                    val clip = copy.clip
                    copy.clipRect(bounds.x, bounds.y, bounds.width, paintedHeight)
                    copy.color = if (active) AgentUiColors.panelBackground else AgentUiColors.tabAreaBackground
                    copy.fillRect(bounds.x, bounds.y, bounds.width, paintedHeight)
                    copy.color = AgentUiColors.bubbleBorder
                    copy.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + paintedHeight)
                    if (!active) copy.drawLine(bounds.x, bounds.y + paintedHeight - 1, bounds.x + bounds.width, bounds.y + paintedHeight - 1)
                    copy.color = if (active || tab.id == hoveredId) {
                        UIManager.getColor("Label.foreground") ?: AgentUiColors.mutedText
                    } else AgentUiColors.mutedText
                    val iconX = bounds.x + JBUI.scale(12)
                    val iconY = bounds.y + (tabHeight - JBUI.scale(14)) / 2
                    copy.drawRoundRect(iconX, iconY, JBUI.scale(14), JBUI.scale(11), JBUI.scale(3), JBUI.scale(3))
                    copy.drawLine(iconX, iconY + JBUI.scale(9), iconX, iconY + JBUI.scale(15))
                    copy.drawLine(iconX, iconY + JBUI.scale(15), iconX + JBUI.scale(4), iconY + JBUI.scale(11))
                    val metrics = copy.fontMetrics
                    val title = abbreviateSessionTitle(tab.fullTitle, metrics,
                        minOf(sessionTitleWidth(metrics), bounds.width - JBUI.scale(66)))
                    copy.drawString(title, bounds.x + JBUI.scale(35), bounds.y + (tabHeight - metrics.height) / 2 + metrics.ascent)
                    if (closeVisible(tab.id)) {
                        val close = closeBounds(bounds)
                        val inset = JBUI.scale(7)
                        copy.drawLine(close.x + inset, close.y + inset, close.x + close.width - inset, close.y + close.height - inset)
                        copy.drawLine(close.x + close.width - inset, close.y + inset, close.x + inset, close.y + close.height - inset)
                    }
                    copy.clip = clip
                }
                if (dragging) {
                    val others = tabBounds().filter { it.first.id != pressedId }
                    val next = others.getOrNull(dropIndex)?.second
                    val last = others.lastOrNull()?.second
                    val x = next?.x ?: last?.let { it.x + it.width } ?: 0
                    val y = next?.y ?: last?.y ?: 0
                    copy.color = UIManager.getColor("Label.foreground") ?: AgentUiColors.mutedText
                    copy.fillRect(x.coerceAtMost(width - JBUI.scale(2)), y, JBUI.scale(2), if (wrapTabs) tabHeight else height)
                }
            } finally {
                copy.dispose()
            }
        }
    }
}
