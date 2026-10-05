package com.cursoragent.ui.composer.context

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.view.TerminalTextSelection
import com.intellij.terminal.frontend.view.TerminalTextSelectionModel
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelsSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class TerminalCopyContextTest {
    @Test
    fun `native Terminal copy decorates only that action and freezes selection before later output`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("terminal clipboard").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val project = fixture.project
                val lifetime = Disposer.newDisposable()
                val clipboard = MemoryClipboard()
                ApplicationManager.getApplication().replaceService(CopyPasteManager::class.java, clipboard, lifetime)
                val source = "build started\n日本語 log"
                var output = source
                val selection = TerminalTextSelection.of(TerminalOffset.ZERO, TerminalOffset.of(source.length.toLong()))
                val model = proxy<TerminalOutputModel> { method, args -> when (method.name) {
                    "getStartOffset" -> TerminalOffset.ZERO
                    "getEndOffset" -> TerminalOffset.of(output.length.toLong())
                    "getText" -> output.substring((args!![0] as TerminalOffset).toAbsolute().toInt(), (args[1] as TerminalOffset).toAbsolute().toInt())
                    "getLineByOffset" -> TerminalLineIndex.of(output.take((args!![0] as TerminalOffset).toAbsolute().toInt()).count { it == '\n' }.toLong())
                    else -> error(method.name)
                } }
                val selectionModel = proxy<TerminalTextSelectionModel> { method, _ ->
                    check(method.name == "getSelection"); selection
                }
                val models = proxy<TerminalOutputModelsSet> { method, _ ->
                    check(method.name == "getActive"); MutableStateFlow(model)
                }
                val title = TerminalTitle()
                val view = proxy<TerminalView> { method, _ -> when (method.name) {
                    "getTextSelectionModel" -> selectionModel
                    "getOutputModels" -> models
                    "getTitle" -> title
                    else -> error(method.name)
                } }
                val editor = EditorFactory.getInstance().createEditor(EditorFactory.getInstance().createDocument(source), project)
                try {
                    editor.selectionModel.setSelection(0, source.length)
                    val action = requireNotNull(ActionManager.getInstance().getAction("Terminal.CopySelectedText"))
                    fun event(includeView: Boolean = true) = AnActionEvent(SimpleDataContext.builder()
                        .add(CommonDataKeys.PROJECT, project)
                        .add(TerminalActionUtil.EDITOR_KEY, editor)
                        .apply { if (includeView) add(TerminalView.DATA_KEY, view) }
                        .build(), action.templatePresentation.clone(), "test", ActionUiKind.NONE, null, 0, ActionManager.getInstance())
                    val listener = TerminalCopyContextListener()
                    clipboard.setContents(StringSelection("previous"))
                    val copiedEvent = event()
                    listener.beforeActionPerformed(action, copiedEvent)
                    action.actionPerformed(copiedEvent)
                    assertEquals(source, clipboard.contents!!.getTransferData(DataFlavor.stringFlavor))
                    output += "\nlater output"
                    listener.afterActionPerformed(action, copiedEvent, AnActionResult.PERFORMED)
                    val terminal = requireNotNull(clipboardContext(clipboard.contents!!, project)).terminal!!
                    assertEquals(source, terminal.text)
                    assertEquals(1L to 2L, terminal.startLine to terminal.endLine)
                    assertEquals(source, clipboard.contents!!.getTransferData(DataFlavor.stringFlavor))
                    val draft = PromptContextDraft().apply { add(terminal) }
                    val queued = draft.snapshot()
                    draft.clearExplicit()
                    assertEquals(listOf(terminal), queued.terminals, "Terminal closure or later output cannot replace copied context")
                    val builder = com.cursoragent.ui.PromptContextBuilder(project, com.cursoragent.ui.composer.mention.MentionResolver(project))
                    val sent = builder.assemble(builder.buildEdtContext("explain", queued.copy(automaticEnabled = false)), "explain")
                    assertTrue(sent.contains(source))
                    assertFalse(sent.contains("later output"))
                    assertTrue(sent.endsWith("\n\nexplain"))

                    val unavailable = event(false)
                    listener.beforeActionPerformed(action, unavailable)
                    action.actionPerformed(unavailable)
                    listener.afterActionPerformed(action, unavailable, AnActionResult.PERFORMED)
                    assertNull(clipboardContext(clipboard.contents!!, project), "no Terminal source means ordinary copied text")

                    val unchanged = clipboard.contents
                    val ignored = event()
                    listener.beforeActionPerformed(action, ignored)
                    listener.afterActionPerformed(action, ignored, AnActionResult.IGNORED)
                    assertSame(unchanged, clipboard.contents)
                    val replaced = event()
                    listener.beforeActionPerformed(action, replaced)
                    val otherCopy = StringSelection("another\nclipboard")
                    clipboard.setContents(otherCopy)
                    listener.afterActionPerformed(action, replaced, AnActionResult.PERFORMED)
                    assertSame(otherCopy, clipboard.contents, "a changed clipboard must not acquire the old Terminal's source")
                } finally {
                    EditorFactory.getInstance().releaseEditor(editor)
                    Disposer.dispose(lifetime)
                }
            }
        } finally { runInEdtAndWait { fixture.tearDown() } }
    }

    private inline fun <reified T> proxy(noinline call: (Method, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args -> when (method.name) {
            "equals" -> instance === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(instance)
            "toString" -> T::class.java.simpleName
            else -> call(method, args)
        } } as T
}
