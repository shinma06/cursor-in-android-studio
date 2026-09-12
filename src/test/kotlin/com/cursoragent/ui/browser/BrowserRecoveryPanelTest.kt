package com.cursoragent.ui.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.xml.parsers.DocumentBuilderFactory

class BrowserRecoveryPanelTest {
    @Test
    fun `recovery without native browser opens only the action explicitly clicked`() {
        val actions = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            val panel = BrowserRecoveryPanel({ actions.add("settings") }, { actions.add("help") })
            assertTrue(actions.isEmpty())
            val buttons = panel.components.filterIsInstance<JPanel>().single().components.filterIsInstance<JButton>()
            buttons.first().doClick(0)
            assertEquals(listOf("settings"), actions)
            buttons.last().doClick(0)
            assertEquals(listOf("settings", "help"), actions)
        }
    }

    @Test
    fun `missing JCEF provider does not make chat or recovery registration optional`() {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val descriptor = javaClass.getResourceAsStream("/META-INF/plugin.xml").use { builder.parse(it) }
        val dependencies = descriptor.getElementsByTagName("depends")
        val jcef = (0 until dependencies.length).map { dependencies.item(it) }
            .single { it.textContent == "com.intellij.modules.jcef" }
        assertEquals("true", jcef.attributes.getNamedItem("optional").nodeValue)
        val optionalConfig = jcef.attributes.getNamedItem("config-file").nodeValue
        val optional = javaClass.getResourceAsStream("/META-INF/$optionalConfig").use { builder.parse(it) }
        assertEquals(0, optional.getElementsByTagName("toolWindow").length)
        val windows = descriptor.getElementsByTagName("toolWindow")
        val ids = (0 until windows.length).map { windows.item(it).attributes.getNamedItem("id").nodeValue }
        assertTrue(ids.containsAll(listOf("Cursor Agent", "Cursor Manual Browser")))
    }
}
