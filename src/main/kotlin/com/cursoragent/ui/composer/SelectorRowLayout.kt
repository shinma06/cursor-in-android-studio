package com.cursoragent.ui.composer

import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/** Keep free row space outside the model button. At narrow widths only the model label clips. */
internal class SelectorRowLayout : LayoutManager {
    override fun addLayoutComponent(name: String?, comp: Component?) = Unit
    override fun removeLayoutComponent(comp: Component?) = Unit
    override fun preferredLayoutSize(parent: Container): Dimension = Dimension(
        parent.components.sumOf { it.preferredSize.width } + JBUI.scale(6),
        parent.components.maxOfOrNull { it.preferredSize.height } ?: 0,
    )
    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, preferredLayoutSize(parent).height)
    override fun layoutContainer(parent: Container) {
        var x = 0
        for (component in parent.components) {
            val size = component.preferredSize
            val width = minOf(size.width, (parent.width - x).coerceAtLeast(0))
            val height = minOf(size.height, parent.height)
            component.setBounds(x, (parent.height - height) / 2, width, height)
            x += width + JBUI.scale(6)
        }
    }
}
