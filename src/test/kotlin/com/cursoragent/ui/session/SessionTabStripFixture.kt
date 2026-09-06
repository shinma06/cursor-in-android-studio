package com.cursoragent.ui.session

import com.cursoragent.ui.AgentUiColors
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Color
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants

/** Manual GUI fixture only. Never launched by the unit suite or installed in the plugin. */
object SessionTabStripFixture {
    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeAndWait {
            val tabs = (1..20).map { index ->
                SessionTabPresentation("fixture-$index", when (index) {
                    1 -> "New Agent"
                    2 -> "あいうえおかきくけこ"
                    3 -> "か\u3099".repeat(10)
                    4 -> "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"
                    5 -> "WWWWWWWWWWWWWWWWWWWW"
                    6 -> "👨‍👩‍👧‍👦".repeat(10)
                    else -> "セッション $index"
                })
            }.toMutableList()
            var nextId = 21
            var selected = tabs.first().id
            val strip = SessionTabStrip()
            val identity = System.getProperty("session.fixture.identity").orEmpty().trim()
            val identitySuffix = if (identity.isEmpty()) "" else " / $identity"
            val frame = JFrame("SESSION-TABS-UI — component fixture$identitySuffix")
            val status = JLabel()
            fun render() {
                strip.setTabs(tabs, selected)
                status.text = "選択: $selected / 順序: ${tabs.joinToString { it.id.removePrefix("fixture-") }}"
            }
            strip.onSelect = { selected = it; render() }
            strip.onClose = { id ->
                val index = tabs.indexOfFirst { it.id == id }
                if (index >= 0) {
                    tabs.removeAt(index)
                    if (tabs.isEmpty()) tabs.add(SessionTabPresentation("fixture-${nextId++}"))
                    if (selected == id) selected = tabs[index.coerceAtMost(tabs.lastIndex)].id
                    render()
                }
            }
            strip.onMove = { id, index ->
                val source = tabs.indexOfFirst { it.id == id }
                if (source >= 0 && index in tabs.indices) {
                    tabs.add(index, tabs.removeAt(source))
                    render()
                }
            }
            val add = JButton("+").apply {
                toolTipText = "検証用タブを追加"
                addActionListener {
                    val tab = SessionTabPresentation("fixture-${nextId++}")
                    tabs.add(tab)
                    selected = tab.id
                    render()
                }
            }
            val header = JPanel(BorderLayout()).apply {
                add(strip, BorderLayout.CENTER)
                add(add, BorderLayout.EAST)
            }
            val content = JPanel(BorderLayout()).apply {
                background = AgentUiColors.panelBackground
                add(header, BorderLayout.NORTH)
                add(JLabel("SESSION-TABS-UI fixture / CLI送信なし / 製品接続の検証対象外"), BorderLayout.CENTER)
                add(status, BorderLayout.SOUTH)
            }
            val controls = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                fun addControl(label: String, action: () -> Unit) {
                    add(JButton(label).apply { addActionListener { action() } })
                }
                fun theme(dark: Boolean) {
                    JBColor.setDark(dark)
                    UIManager.put("Label.foreground", if (dark) Color(0xDDDDDD) else Color(0x222222))
                    UIManager.put("Panel.background", if (dark) Color(0x222222) else Color(0xEEEEEE))
                    // Preserve the strip's custom scrollbar UI while changing fixture colors.
                    fun recolor(container: Container) {
                        if (container is JPanel) container.background = AgentUiColors.panelBackground
                        container.components.forEach { child ->
                            if (child is JLabel) child.foreground = UIManager.getColor("Label.foreground")
                            if (child is Container) recolor(child)
                        }
                    }
                    recolor(content)
                    frame.repaint()
                }
                addControl("明") { theme(false) }
                addControl("暗") { theme(true) }
                addControl("狭幅") { frame.setSize(320, 320) }
                addControl("広幅") { frame.setSize(900, 320) }
            }
            val footer = JPanel(BorderLayout()).apply {
                add(controls, BorderLayout.NORTH)
                add(status, BorderLayout.SOUTH)
            }
            content.add(footer, BorderLayout.SOUTH)
            render()
            frame.apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                contentPane = content
                minimumSize = Dimension(240, 180)
                setSize(760, 320)
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
    }
}
