package com.cursoragent.ui.composer.mention

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView

private const val MAX_TERMINAL_CHARS = 8_000
private val LOG = logger<TerminalOutputReader>()

/**
 * Reads recent terminal output for `@terminal` mentions. Terminal APIs require EDT
 * access; callers on a background thread should use [readRecentOutputBlocking].
 */
object TerminalOutputReader {
    fun readRecentOutputBlocking(project: Project): String? {
        if (ApplicationManager.getApplication().isDispatchThread) {
            return readRecentOutput(project)
        }

        val holder = arrayOf<String?>(null)
        ApplicationManager.getApplication().invokeAndWait {
            holder[0] = readRecentOutput(project)
        }
        return holder[0]
    }

    private fun readRecentOutput(project: Project): String? {
        return try {
            val tabsManager = TerminalToolWindowTabsManager.getInstance(project)
            val tab = tabsManager.tabs.lastOrNull() ?: return null
            val view = tab.view
            extractTailText(view)
        } catch (e: Exception) {
            LOG.info("Could not read terminal output for @terminal mention", e)
            null
        } catch (e: LinkageError) {
            // The Terminal plugin (an optional dependency, see plugin.xml) is disabled or
            // absent, so referencing its classes threw NoClassDefFoundError/LinkageError.
            LOG.info("Terminal plugin unavailable for @terminal mention", e)
            null
        }
    }

    private fun extractTailText(view: TerminalView): String? {
        val model = view.outputModels.regular
        if (model.textLength == 0) return null

        val end = model.endOffset
        val desiredStart = end.minus(MAX_TERMINAL_CHARS.toLong())
        val start = if (desiredStart.toAbsolute() < model.startOffset.toAbsolute()) {
            model.startOffset
        } else {
            desiredStart
        }
        return model.getText(start, end).toString().trim().takeIf { it.isNotEmpty() }
    }
}
