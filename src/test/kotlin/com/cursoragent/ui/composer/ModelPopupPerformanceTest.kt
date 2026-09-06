package com.cursoragent.ui.composer

import com.cursoragent.service.ModelListParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Container
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.CellRendererPane
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

class ModelPopupPerformanceTest {
    private fun panel(): ModelPopupPanel {
        val options = ModelListParser.parse(javaClass.getResource("/model-list-2026-09-06.txt")!!.readText())
        return ModelPopupPanel(options, options.first { it.id != "auto" }.id, {}, {}, {})
    }

    @Test
    fun `repeated search and layout keep the renderer component tree bounded`() = SwingUtilities.invokeAndWait {
        val panel = panel()
        // Preferred-size measurement uses the same BasicListUI renderer pane as a popup pack.
        panel.modelList.preferredSize
        val rendererPane = panel.modelList.components.filterIsInstance<CellRendererPane>().single()
        val initialCount = rendererPane.componentCount
        repeat(50) {
            panel.searchField.text = "claude"
            panel.modelList.preferredSize
            panel.searchField.text = ""
            panel.modelList.preferredSize
        }
        val finalCount = rendererPane.componentCount
        println("Renderer components: initial=$initialCount, after 50 search/clear cycles=$finalCount")
        assertTrue(finalCount <= 1, "Renderer children must not accumulate: $initialCount -> $finalCount")
    }

    @Test
    fun `search does not detach the list including no match and clear transitions`() = SwingUtilities.invokeAndWait {
        val panel = panel()
        val scroll = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, panel.modelList) as JScrollPane
        val results = scroll.parent as Container
        var removals = 0
        results.addContainerListener(object : ContainerAdapter() {
            override fun componentRemoved(e: ContainerEvent) {
                if (e.child === scroll) removals++
            }
        })
        repeat(20) {
            panel.searchField.text = "claude"
            panel.searchField.text = "no-such-model-xyz"
            assertEquals(0, panel.modelList.model.size)
            panel.searchField.text = ""
            assertTrue(panel.modelList.model.size > 0)
        }
        println("Scroll pane removals after 20 search/no-match/clear cycles: $removals")
        assertEquals(0, removals, "Search must not repeatedly detach the renderer subtree")
    }
}
