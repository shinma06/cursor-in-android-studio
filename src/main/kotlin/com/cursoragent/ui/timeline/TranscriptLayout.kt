package com.cursoragent.ui.timeline

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/** Top-aligned rows: spare viewport height never stretches a message. */
class TranscriptLayout(private val gap: Int) : LayoutManager {
    override fun addLayoutComponent(name: String?, comp: Component?) = Unit
    override fun removeLayoutComponent(comp: Component?) = Unit
    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, preferredLayoutSize(parent).height)
    override fun preferredLayoutSize(parent: Container): Dimension = measure(parent, false)
    override fun layoutContainer(parent: Container) { measure(parent, true) }

    private fun measure(parent: Container, place: Boolean): Dimension {
        val insets = parent.insets
        val width = (parent.width - insets.left - insets.right).coerceAtLeast(1)
        var y = insets.top
        val rows = parent.components.filter { it.isVisible }
        for (row in rows) {
            row.setSize(width, row.height.coerceAtLeast(1))
            layoutChildren(row)
            val height = row.preferredSize.height
            if (place) {
                row.setBounds(insets.left, y, width, height)
                layoutChildren(row)
            }
            y += height + gap
        }
        return Dimension(width + insets.left + insets.right, y + insets.bottom - if (rows.isEmpty()) 0 else gap)
    }

    private fun layoutChildren(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layoutChildren)
        }
    }
}
