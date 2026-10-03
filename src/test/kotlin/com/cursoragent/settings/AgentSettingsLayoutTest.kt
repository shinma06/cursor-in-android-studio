package com.cursoragent.settings

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JViewport
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSettingsLayoutTest {
    @Test
    fun `settings keep path and diagnostic actions inside a narrow viewport`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("settings layout").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val settings = AgentSettingsState.getInstance()
                val originalPath = settings.agentExecutablePath
                val configurable = AgentSettingsConfigurable()
                try {
                    settings.agentExecutablePath = "/disposable/" + "long-directory/".repeat(12) + "agent"
                    val form = configurable.createComponent()
                    val viewport = JViewport().apply {
                        setSize(620, 600)
                        view = form
                    }
                    repeat(4) {
                        viewport.doLayout()
                        layoutTree(form)
                    }
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
