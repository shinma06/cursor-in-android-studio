package com.cursoragent.ui

import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.editor.ChatEditorPresentation
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.UUID
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities

class ChatEditorModelRequestTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun get(owner: Any, name: String): Any? = field(owner, name).get(owner)

    @Test
    fun `moving the same view retains its pending model request while real hiding cancels it`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("model request presentation").fixture
        fixture.setUp()
        val settings = AgentSettingsState.getInstance()
        val executable = settings.agentExecutablePath
        settings.agentExecutablePath = Path.of(fixture.project.basePath!!, "missing-model-view-" + UUID.randomUUID()).toString()
        var root: AgentToolWindowRootPanel? = null
        var host: JPanel? = null
        lateinit var destination: JPanel
        lateinit var controller: AgentUiController
        lateinit var composer: ComposerPanel
        lateinit var presentation: ChatEditorPresentation
        var cancelled = 0
        val cancel: () -> Unit = { cancelled++ }
        try {
            runInEdtAndWait {
                val panel = AgentToolWindowRootPanel(fixture.project) {}
                root = panel
                val sessions = get(panel, "sessions") as SessionTabs
                val view = requireNotNull((get(panel, "views") as Map<*, *>)[sessions.snapshot().selectedId])
                controller = get(view, "controller") as AgentUiController
                composer = get(view, "composer") as ComposerPanel
                presentation = get(view, "presentation") as ChatEditorPresentation
                val original = JPanel(BorderLayout()).apply { add(panel) }
                destination = JPanel(BorderLayout())
                host = JPanel().apply {
                    add(JRootPane().apply {
                        glassPane = com.intellij.openapi.wm.impl.IdeGlassPaneImpl(this, false)
                        contentPane.add(JPanel().apply { add(original); add(destination) })
                    })
                    addNotify()
                }
                assertTrue(composer.isShowing)
                field(controller, "modelChangeGeneration").set(controller, 7L)
                field(controller, "modelRequestGeneration").set(controller, 7L)
                field(controller, "cancelModelRequest").set(controller, cancel)
                composer.setModelConfigurationBusy(true)
                original.remove(panel)
                destination.add(panel)
                assertTrue(composer.isShowing)
            }
            SwingUtilities.invokeAndWait {}
            runInEdtAndWait {
                assertEquals(0, cancelled, "A transient hierarchy removal must not cancel the owned ACP request")
                assertEquals(7L, get(controller, "modelChangeGeneration"))
                assertEquals(7L, get(controller, "modelRequestGeneration"))
                assertSame(cancel, get(controller, "cancelModelRequest"))
                assertTrue(composer.modelConfigurationBusy)
                presentation.toggle()
                assertFalse(presentation.inEditor, "Explicit presentation changes wait for the model response")
                assertFalse(composer.canToggleEditorWithShortcut)
                assertEquals(0, cancelled)
                destination.remove(root)
                assertFalse(composer.isShowing)
            }
            SwingUtilities.invokeAndWait {}
            runInEdtAndWait {
                assertEquals(1, cancelled, "A view that remains hidden must still cancel its request")
                assertNull(get(controller, "cancelModelRequest"))
                assertTrue((get(controller, "modelChangeGeneration") as Long) > 7L)
            }
        } finally {
            runInEdtAndWait { root?.let(Disposer::dispose); host?.removeNotify() }
            settings.agentExecutablePath = executable
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
