package com.cursoragent.ui.header

import com.cursoragent.settings.AgentSettingsConfigurable
import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.ui.composer.ComposerOptionsPanel
import com.cursoragent.ui.mcp.McpServersDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.ScrollPaneConstants

/** The fixed header owns its menu; the composer only supplies the current turn state. Calls are on EDT. */
internal class HeaderOptionsPopup(
    private val project: Project,
    private val button: JButton,
    private val onSummarize: () -> Unit,
) {
    private var running = false
    private var popup: JBPopup? = null
    private var optionsPanel: ComposerOptionsPanel? = null

    init {
        button.addActionListener { toggle() }
        button.addHierarchyListener { if (!button.isShowing) popup?.cancel() }
    }

    fun setRunning(value: Boolean) {
        running = value
        optionsPanel?.setRunning(value)
    }

    private fun toggle() {
        popup?.takeUnless { it.isDisposed }?.let {
            it.cancel()
            return
        }
        if (!button.isShowing) return
        val content = ComposerOptionsPanel(
            settings = AgentSettingsState.getInstance(),
            isRunning = running,
            onSummarize = { if (!running) onSummarize() },
            onMcp = { McpServersDialog(project).show() },
            onSettings = { ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentSettingsConfigurable::class.java) },
            onClose = { popup?.cancel() },
        )
        // A short display still exposes every action through scrolling without covering the trigger.
        val scroll = JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }
        val next = JBPopupFactory.getInstance().createComponentPopupBuilder(scroll, content.permissionChoice)
            .setFocusable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setCancelKeyEnabled(true)
            .createPopup()
        popup = next
        optionsPanel = content
        PopupUtil.setPopupToggleComponent(next, button)
        val cleanup = {
            if (popup === next) {
                popup = null
                optionsPanel = null
            }
        }
        Disposer.register(next, Disposable { cleanup() })
        next.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) = cleanup()
        })
        val bounds = headerPopupBounds(
            Rectangle(button.locationOnScreen, button.size), scroll.preferredSize,
            ScreenUtil.getScreenRectangle(button), JBUI.scale(4),
        )
        try {
            next.setSize(bounds.size)
            next.show(RelativePoint.fromScreen(bounds.location))
            // Keep the explicit downward anchor even if the IDE's initial fitting moved the window.
            next.setSize(bounds.size)
            next.setLocation(bounds.location)
        } catch (failure: Throwable) {
            next.cancel()
            throw failure
        }
    }
}

/** Right-align with the header action and constrain to the available area below it. */
internal fun headerPopupBounds(anchor: Rectangle, size: Dimension, screen: Rectangle, gap: Int): Rectangle {
    val width = size.width.coerceIn(1, screen.width.coerceAtLeast(1))
    val top = anchor.y + anchor.height + gap
    val height = size.height.coerceIn(1, (screen.y + screen.height - top).coerceAtLeast(1))
    val left = (anchor.x + anchor.width - width).coerceIn(screen.x, screen.x + screen.width - width)
    return Rectangle(left, top, width, height)
}
