package com.cursoragent.ui.composer.context

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Container
import java.lang.reflect.Proxy
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

class PromptContextPanelTest {
    @Test
    fun `many attachments stay bounded scroll to the last row and expose distinct replacement names`() = SwingUtilities.invokeAndWait {
        val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            check(method.name == "isDisposed")
            false
        } as Project
        val panel = PromptContextPanel(project)
        val emptyHeight = panel.preferredSize.height
        repeat(40) { index ->
            panel.addSelection(SelectionContext("file:///A$index.kt", "A$index.kt", 0, 1, 1, 1, "x", 1))
        }
        val scroll = panel.components.filterIsInstance<JScrollPane>().single()
        assertTrue(scroll.preferredSize.height <= JBUI.scale(120))
        val timeline = JPanel()
        val host = JPanel(BorderLayout()).apply {
            add(timeline, BorderLayout.CENTER)
            add(panel, BorderLayout.SOUTH)
            setSize(500, 600)
        }
        fun layout(container: Container) {
            container.doLayout()
            container.components.filterIsInstance<Container>().forEach(::layout)
        }
        layout(host)
        assertTrue(timeline.height > 0)
        val rows = scroll.viewport.view as JPanel
        val replacements = rows.components.map { row ->
            (row as Container).components.filterIsInstance<JPanel>().single().components
                .filterIsInstance<JButton>().single { it.text == "変更" }
        }
        assertEquals(40, replacements.map { it.accessibleContext.accessibleName }.distinct().size)
        replacements.forEachIndexed { index, button ->
            assertTrue(button.accessibleContext.accessibleName.contains("A$index.kt"))
        }
        val last = rows.components.last() as JPanel
        rows.scrollRectToVisible(last.bounds)
        assertTrue(scroll.viewport.viewPosition.y > 0)
        assertTrue(rows.visibleRect.intersects(last.bounds))
        panel.clearExplicit()
        assertFalse(scroll.isVisible)
        assertEquals(emptyHeight, panel.preferredSize.height)
    }
}
