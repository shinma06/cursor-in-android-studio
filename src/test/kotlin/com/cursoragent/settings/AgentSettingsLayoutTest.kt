package com.cursoragent.settings

import com.intellij.openapi.options.ex.ConfigurableCardPanel
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSettingsLayoutTest {
    @Test
    fun `settings keep text readable and actions inside a narrow viewport`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("settings layout").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val settings = AgentSettingsState.getInstance()
                val originalPath = settings.agentExecutablePath
                val configurable = AgentSettingsConfigurable()
                try {
                    settings.agentExecutablePath = "/disposable/" + "long-directory/".repeat(12) + "agent"
                    val host = ConfigurableCardPanel.createConfigurableComponent(configurable) as JScrollPane
                    host.setSize(620, 600)
                    repeat(4) { layoutTree(host) }
                    val viewport = host.viewport
                    val form = viewport.view
                    assertEquals(viewport.width, form.width, "The Settings form must follow its viewport width")
                    val actions = descendants(form).filterIsInstance<JButton>().filter {
                        it.text in setOf("自動検出に戻す", "表示を更新", "表示中の診断情報をコピー")
                    }.toList()
                    assertEquals(3, actions.size)
                    for (action in actions) {
                        val bounds = SwingUtilities.convertRectangle(action.parent, action.bounds, form)
                        assertTrue(bounds.x >= 0 && bounds.width > 0 && bounds.x + bounds.width <= viewport.width,
                            "${action.text} must remain reachable: $bounds, viewport=${viewport.size}")
                    }
                    val details = descendants(form).filterIsInstance<JTextArea>()
                        .single { it.accessibleContext.accessibleName == "ビルド・CLI診断情報" }
                    assertTrue(details.parent.height >= details.getFontMetrics(details.font).height * 3,
                        "Diagnostics must show several readable lines: ${details.parent.size}")
                    for (choice in descendants(form).filterIsInstance<JComboBox<*>>()) {
                        assertTrue(choice.width >= choice.preferredSize.width,
                            "The current setting must be readable: ${choice.size}, preferred=${choice.preferredSize}")
                    }
                } finally {
                    configurable.disposeUIResources()
                    settings.agentExecutablePath = originalPath
                }
            }
        } finally {
            runInEdtAndWait { fixture.tearDown() }
        }
    }

    private fun layoutTree(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach(::layoutTree)
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) for (child in component.components) yieldAll(descendants(child))
    }
}
