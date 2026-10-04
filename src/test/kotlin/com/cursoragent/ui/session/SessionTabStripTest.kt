package com.cursoragent.ui.session

import com.cursoragent.ui.AgentUiColors
import com.cursoragent.ui.timeline.ChatTimelinePanel
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.util.ui.JBUI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.util.regex.Pattern
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager

class SessionTabStripTest {
    @Test
    fun `title width includes ellipsis and preserves whole graphemes`() {
        val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            for (size in listOf(12, 18, 24)) {
                val metrics = graphics.getFontMetrics(Font(Font.DIALOG, Font.PLAIN, size))
                val limit = sessionTitleWidth(metrics)
                assertEquals("New Agent", SessionTabPresentation("a").fullTitle)
                assertEquals("New Agent", SessionTabPresentation("a", " ").fullTitle)
                assertEquals("あ".repeat(9), abbreviateSessionTitle("あ".repeat(9), metrics, limit))
                // Linux CI can use a different Japanese fallback font. Check exact-fit behavior
                // against the same text's measured width, not a cross-script width assumption.
                assertEquals("New Agent", abbreviateSessionTitle("New Agent", metrics, metrics.stringWidth("New Agent")))
                val narrow = abbreviateSessionTitle("i".repeat(100), metrics, limit)
                val wide = abbreviateSessionTitle("W".repeat(100), metrics, limit)
                assertTrue(narrow.length > wide.length)
                assertTrue(narrow.removeSuffix("…").length > 9)
                for (grapheme in listOf("あ", "か\u3099", "👨‍👩‍👧‍👦", "🇯🇵")) {
                    val title = grapheme.repeat(30)
                    val result = abbreviateSessionTitle(title, metrics, limit)
                    assertTrue(result.endsWith("…"))
                    assertTrue(metrics.stringWidth(result) <= limit, "$size: $result")
                    val prefix = result.removeSuffix("…")
                    assertTrue(Pattern.matches("(?:" + Pattern.quote(grapheme) + ")*", prefix))
                    assertTrue(metrics.stringWidth(prefix + grapheme + "…") > limit)
                }
            }
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `provider title tooltip shows full literal text without an HTML renderer`() = onEdt {
        val title = "<html><b>" + "日本語👨‍👩‍👧‍👦".repeat(20) + "</b></html>"
        val strip = SessionTabStrip()
        strip.setTabs(listOf(SessionTabPresentation("a", title)), "a")
        val fullText = strip.eventTarget.getToolTipText(event(strip.eventTarget, MouseEvent.MOUSE_MOVED, Point(40, 15)))
        assertEquals(title, fullText)
        val tip = strip.eventTarget.createToolTip()
        tip.tipText = fullText
        assertEquals(title, tip.tipText)
        assertNull(tip.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
        tip.updateUI()
        assertNull(tip.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
    }

    @Test
    fun `wide grapheme titles do not expand tab beyond the font width budget`() = onEdt {
        val strip = SessionTabStrip()
        strip.setTabs(listOf(SessionTabPresentation("a", "か\u3099".repeat(30))), "a")
        val metrics = strip.eventTarget.getFontMetrics(strip.eventTarget.font)
        assertTrue(strip.boundsFor("a")!!.width <= sessionTitleWidth(metrics) + JBUI.scale(66))
    }

    @Test
    fun `scrollbar overlays viewport bottom and keeps hover and wheel over bar`() = onEdt {
        val strip = fixture()
        strip.setSize(320, strip.preferredSize.height)
        layout(strip)
        val pane = strip.scrollPane
        val bar = pane.horizontalScrollBar
        val viewport = pane.viewport
        assertEquals(strip.boundsFor("a")!!.height, strip.preferredSize.height)
        assertTrue(viewport.bounds.contains(bar.bounds))
        assertEquals(viewport.y + viewport.height, bar.y + bar.height)
        assertEquals(JBUI.scale(3), bar.height)
        assertTrue(pane.getComponentZOrder(bar) < pane.getComponentZOrder(viewport))
        assertFalse(pane.isOptimizedDrawingEnabled)
        assertSame(bar, pane.getComponentAt(bar.x + bar.width / 2, bar.y + 1))
        mouse(strip.eventTarget, MouseEvent.MOUSE_MOVED, Point(50, 15))
        mouse(strip.eventTarget, MouseEvent.MOUSE_EXITED, Point(50, viewport.height - 1))
        mouse(bar, MouseEvent.MOUSE_ENTERED, Point(50, 1))
        assertTrue(strip.scrollbarRevealed)
        val before = bar.value
        val wheel = MouseWheelEvent(bar, MouseEvent.MOUSE_WHEEL, 0, 0, 50, 1, 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1)
        bar.dispatchEvent(wheel)
        assertTrue(wheel.isConsumed)
        assertTrue(bar.value > before)
        mouse(bar, MouseEvent.MOUSE_EXITED, Point(-20, -20))
        assertFalse(strip.scrollbarRevealed)
    }

    @Test
    fun `selected tab uses chat background independently of tab area viewport`() = onEdt {
        val strip = fixture()
        for (background in listOf(Color(0x181A1B), Color(0xF4F5F7))) {
            strip.scrollPane.viewport.background = background
            val image = BufferedImage(strip.width, strip.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                strip.paint(graphics)
                assertEquals(AgentUiColors.panelBackground.rgb, image.getRGB(4, 4))
                assertEquals(AgentUiColors.panelBackground.rgb, image.getRGB(4, strip.scrollPane.viewport.height - 2))
            } finally {
                graphics.dispose()
            }
        }
    }

    @Test
    fun `selected tab and empty or populated chat share native theme background`() = onEdt {
        val key = "ToolWindow.background"
        val previous = UIManager.get(key)
        fun layoutTree(container: Container) {
            container.doLayout()
            container.components.filterIsInstance<Container>().forEach(::layoutTree)
        }
        try {
            val strip = fixture()
            val root = JPanel(BorderLayout()).apply {
                background = AgentUiColors.panelBackground
                add(strip, BorderLayout.NORTH)
                setSize(320, 180)
            }
            // Invoke the installed SDK's actual recoloring rule, not a copy of its implementation.
            val recolor = InternalDecoratorImpl.Companion::class.java.methods.single {
                it.name.startsWith("setBackgroundRecursively")
            }
            for (color in listOf(Color(0x191A1C), Color(0xF4F5F7), Color(0x34495E))) {
                UIManager.put(key, color)
                recolor.invoke(InternalDecoratorImpl.Companion, root, JBUI.CurrentTheme.ToolWindow.background())
                for (createdAfterRecolor in listOf(false, true)) {
                    val timeline = ChatTimelinePanel()
                    val chat = JPanel(BorderLayout()).apply {
                        // Newly opened tab cards inherit the root's background without another SDK pass.
                        isOpaque = !createdAfterRecolor
                        add(timeline)
                    }
                    root.add(chat, BorderLayout.CENTER)
                    if (!createdAfterRecolor) {
                        recolor.invoke(InternalDecoratorImpl.Companion, root, JBUI.CurrentTheme.ToolWindow.background())
                    }
                    for (populated in listOf(false, true)) {
                        if (populated) timeline.showStatus("Ready")
                        layoutTree(root)
                        val image = BufferedImage(root.width, root.height, BufferedImage.TYPE_INT_ARGB)
                        val graphics = image.createGraphics()
                        try {
                            root.paint(graphics)
                            assertEquals(color.rgb, image.getRGB(4, strip.height + 4), "Actual chat background")
                            assertEquals(image.getRGB(4, strip.height + 4), image.getRGB(4, 4), "Selected tab background")
                            assertEquals(color.rgb, image.getRGB(4, strip.height - 2), "Selected tab lower edge")
                        } finally {
                            graphics.dispose()
                        }
                    }
                    root.remove(chat)
                }
            }
        } finally {
            if (previous == null) UIManager.getDefaults().remove(key) else UIManager.put(key, previous)
        }
    }

    @Test
    fun `fixed actions share tab area background and divide only at touching or clipped tabs`() = onEdt {
        val strip = SessionTabStrip()
        val tabs = listOf(SessionTabPresentation("a"), SessionTabPresentation("b"))
        val toolbar = JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(108), JBUI.scale(38))
        }
        strip.add(toolbar, BorderLayout.EAST)
        fun paint(): BufferedImage {
            layout(strip)
            return BufferedImage(strip.width, strip.height, BufferedImage.TYPE_INT_ARGB).also { image ->
                val g = image.createGraphics()
                try { strip.paint(g) } finally { g.dispose() }
            }
        }
        for (selected in listOf("a", "b")) {
            strip.setTabs(tabs, selected)
            val total = strip.boundsFor("b")!!.let { it.x + it.width }
            for (gap in listOf(40, 1, 0, -40, -100, 40)) {
                strip.setSize(total + toolbar.preferredSize.width + gap, strip.preferredSize.height)
                strip.scrollPane.viewport.viewPosition = Point(0, 0)
                val image = paint()
                val area = image.getRGB(toolbar.x + 3, 3)
                assertNotEquals(AgentUiColors.panelBackground.rgb, area, "Tab area differs from chat")
                val active = strip.boundsFor(selected)!!
                if (active.x + 4 < toolbar.x) {
                    assertEquals(AgentUiColors.panelBackground.rgb, image.getRGB(active.x + 4, 3))
                }
                assertEquals(if (gap <= 0) AgentUiColors.bubbleBorder.rgb else area,
                    image.getRGB(toolbar.x, 3), "Divider for $selected, gap=$gap")
                if (gap > 0) assertEquals(area, image.getRGB(toolbar.x - 1, 3), "Unused tab area")
                if (gap < 0) {
                    strip.scrollPane.horizontalScrollBar.value = Int.MAX_VALUE
                    val scrolled = paint()
                    assertEquals(AgentUiColors.bubbleBorder.rgb, scrolled.getRGB(toolbar.x, 3), "Scrolled to end")
                }
                assertEquals(JBUI.scale(108), toolbar.width)
                assertEquals(JBUI.scale(38), toolbar.height)
            }
        }
        // Removing tabs or hiding toolbar actions creates a gap and removes the divider again.
        strip.setTabs(listOf(tabs.first()), "a")
        var image = paint()
        assertEquals(image.getRGB(toolbar.x + 3, 3), image.getRGB(toolbar.x, 3))
        strip.setTabs(tabs, "a")
        val total = strip.boundsFor("b")!!.let { it.x + it.width }
        strip.setSize(total + toolbar.preferredSize.width, strip.preferredSize.height)
        image = paint()
        assertEquals(AgentUiColors.bubbleBorder.rgb, image.getRGB(toolbar.x, 3))
        toolbar.preferredSize = Dimension(JBUI.scale(56), toolbar.preferredSize.height)
        image = paint()
        assertEquals(image.getRGB(toolbar.x + 3, 3), image.getRGB(toolbar.x, 3))
        strip.setTabs(emptyList(), null)
        image = paint()
        assertEquals(image.getRGB(toolbar.x + 3, 3), image.getRGB(toolbar.x, 3))
    }

    @Test
    fun `leaving tabs through fixed actions hides the overlaid scrollbar`() = onEdt {
        val strip = fixture()
        val toolbar = JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(108), JBUI.scale(38))
        }
        strip.add(toolbar, BorderLayout.EAST)
        layout(strip)
        assertTrue(strip.scrollPane.horizontalScrollBar.isVisible)
        mouse(strip.eventTarget, MouseEvent.MOUSE_MOVED, Point(20, 15))
        assertTrue(strip.scrollbarRevealed)
        mouse(strip.eventTarget, MouseEvent.MOUSE_EXITED, Point(toolbar.x + 2, 15))
        mouse(toolbar, MouseEvent.MOUSE_ENTERED, Point(2, 15))
        assertFalse(strip.scrollbarRevealed)
        mouse(toolbar, MouseEvent.MOUSE_EXITED, Point(2, toolbar.height + 10))
        assertFalse(strip.scrollbarRevealed)
        mouse(strip.eventTarget, MouseEvent.MOUSE_ENTERED, Point(20, 15))
        assertTrue(strip.scrollbarRevealed)
    }

    @Test
    fun `hover exposes close on that tab and preserves active close and full tooltip`() = onEdt {
        val strip = fixture()
        assertTrue(strip.closeVisible("a"))
        assertFalse(strip.closeVisible("b"))
        val point = center(strip, "b")
        mouse(strip.eventTarget, MouseEvent.MOUSE_MOVED, point)
        assertTrue(strip.closeVisible("b"))
        assertTrue(strip.closeVisible("a"))
        assertTrue(strip.scrollbarRevealed)
        assertEquals("いろはにほへとちりぬるを", strip.eventTarget.getToolTipText(event(strip.eventTarget, MouseEvent.MOUSE_MOVED, point)))
        mouse(strip.eventTarget, MouseEvent.MOUSE_EXITED, Point(-1, -1))
        assertFalse(strip.closeVisible("b"))
        assertFalse(strip.scrollbarRevealed)
    }

    @Test
    fun `click close and drag dispatch only their own stable ID actions`() = onEdt {
        val strip = fixture()
        val actions = mutableListOf<String>()
        strip.onSelect = { actions.add("select:$it") }
        strip.onClose = { actions.add("close:$it") }
        strip.onMove = { id, index -> actions.add("move:$id:$index") }
        click(strip.eventTarget, center(strip, "b"))
        assertEquals(listOf("select:b"), actions)
        actions.clear()
        val close = strip.closeBoundsFor("a")!!
        click(strip.eventTarget, Point(close.x + 10, close.y + 10))
        assertEquals(listOf("close:a"), actions)
        actions.clear()
        mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "a"))
        val end = strip.boundsFor("c")!!.let { Point(it.x + it.width - 2, 15) }
        mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, end)
        mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, end)
        assertEquals(listOf("move:a:2"), actions)
    }

    @Test
    fun `controlled reorder uses release pointer rather than original press position`() = onEdt {
        val strip = fixture()
        strip.setSize(600, 40)
        layout(strip)
        strip.onMove = { _, _ ->
            strip.setTabs(listOf(SessionTabPresentation("b", "いろはにほへとちりぬるを"), SessionTabPresentation("c"), SessionTabPresentation("a")), "a")
        }
        mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "a"))
        val end = strip.boundsFor("c")!!.let { Point(it.x + it.width - 2, 15) }
        mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, end)
        mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, end)
        assertTrue(strip.closeVisible("a"))
        assertFalse(strip.closeVisible("b"))
        assertFalse(strip.closeVisible("c"))
    }

    @Test
    fun `close press dragged away cannot select close or move`() = onEdt {
        val strip = fixture()
        var calls = 0
        strip.onSelect = { calls++ }
        strip.onClose = { calls++ }
        strip.onMove = { _, _ -> calls++ }
        val close = strip.closeBoundsFor("a")!!
        mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, Point(close.x + 10, close.y + 10))
        mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, center(strip, "b"))
        mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, center(strip, "b"))
        assertEquals(0, calls)
    }

    @Test
    fun `removed drag source and escape cancel without stale callbacks`() = onEdt {
        val strip = fixture()
        var calls = 0
        strip.onSelect = { calls++ }
        strip.onMove = { _, _ -> calls++ }
        mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "a"))
        mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, center(strip, "b"))
        strip.eventTarget.actionMap.get("cancelDrag").actionPerformed(null)
        mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, center(strip, "b"))
        mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "a"))
        strip.setTabs(listOf(SessionTabPresentation("b")), "b")
        mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, Point(40, 15))
        assertEquals(0, calls)
    }

    @Test
    fun `hiding strip or ancestor cancels drag and stops edge timer`() = onEdt {
        for (hideAncestor in listOf(false, true)) {
            val strip = fixture()
            val parent = JPanel().apply { add(strip) }
            // Create an offscreen hierarchy without opening a desktop window.
            parent.addNotify()
            try {
                assertTrue(strip.isShowing)
                var calls = 0
                strip.onSelect = { calls++ }
                strip.onClose = { calls++ }
                strip.onMove = { _, _ -> calls++ }
                mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "a"))
                val end = center(strip, "c")
                mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, end)
                assertTrue(strip.dragAutoScrollRunning)
                if (hideAncestor) parent.isVisible = false else strip.isVisible = false
                assertFalse(strip.isShowing)
                assertFalse(strip.dragAutoScrollRunning)
                assertFalse(strip.scrollbarRevealed)
                if (hideAncestor) parent.isVisible = true else strip.isVisible = true
                mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, end)
                assertEquals(0, calls)
            } finally {
                parent.removeNotify()
            }
        }
    }

    @Test
    fun `keyboard reorder reveals unchanged selected ID in narrow viewport`() {
        lateinit var strip: SessionTabStrip
        var tabs = (0..7).map { SessionTabPresentation("tab-$it") }
        onEdt {
            strip = SessionTabStrip()
            strip.setTabs(tabs, "tab-0")
            strip.setSize(200, 40)
            layout(strip)
            strip.onMove = { id, index ->
                tabs = tabs.toMutableList().apply {
                    val source = removeAt(indexOfFirst { it.id == id })
                    add(index, source)
                }
                strip.setTabs(tabs, "tab-0")
                layout(strip)
            }
        }
        for (direction in listOf("moveRight", "moveLeft")) {
            repeat(7) {
                onEdt { strip.eventTarget.actionMap.get(direction).actionPerformed(null) }
                // A separate EDT turn lets setTabs' deferred reveal run first.
                onEdt {
                    assertTrue(strip.scrollPane.viewport.viewRect.contains(strip.boundsFor("tab-0")!!))
                }
            }
        }
    }

    @Test
    fun `keyboard selection close and reorder use controlled selection`() = onEdt {
        val strip = fixture()
        val actions = mutableListOf<String>()
        strip.onSelect = { actions.add("select:$it") }
        strip.onClose = { actions.add("close:$it") }
        strip.onMove = { id, index -> actions.add("move:$id:$index") }
        for (name in listOf("previous", "next", "last", "close", "moveLeft", "moveRight")) {
            strip.eventTarget.actionMap.get(name).actionPerformed(null)
        }
        assertEquals(listOf("select:b", "select:c", "close:a", "move:a:1"), actions)
    }

    @Test
    fun `narrow viewport keeps natural tab widths and scrolls horizontally`() = onEdt {
        val strip = fixture()
        assertTrue(strip.scrollPane.horizontalScrollBar.isVisible)
        val before = strip.boundsFor("a")!!.width
        strip.setSize(200, 40)
        layout(strip)
        assertEquals(before, strip.boundsFor("a")!!.width)
        strip.scrollPane.horizontalScrollBar.value = 100
        assertEquals(100, strip.scrollPane.viewport.viewPosition.x)
        assertFalse(strip.scrollPane.verticalScrollBar.isVisible)
    }

    @Test
    fun `scroll and replacement recompute hover under a stationary pointer`() = onEdt {
        val strip = fixture()
        strip.setSize(170, 40)
        layout(strip)
        val pointer = Point(140, 15)
        mouse(strip.eventTarget, MouseEvent.MOUSE_MOVED, pointer)
        assertTrue(strip.closeVisible("b"))
        val c = strip.boundsFor("c")!!
        strip.scrollPane.horizontalScrollBar.value = c.x - 130
        assertTrue(strip.closeVisible("c"))
        assertFalse(strip.closeVisible("b"))
        strip.setTabs(listOf(SessionTabPresentation("a"), SessionTabPresentation("d"), SessionTabPresentation("e")), "a")
        layout(strip)
        val underPointer = strip.eventTarget.getToolTipText(event(strip.eventTarget, MouseEvent.MOUSE_MOVED,
            Point(pointer.x + strip.scrollPane.viewport.viewPosition.x, pointer.y)))
        assertNotNull(underPointer)
        assertFalse(strip.closeVisible("b"))
        assertFalse(strip.closeVisible("c"))
    }

    @Test
    fun `invalid updates fail before changing the displayed selection`() = onEdt {
        val strip = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            strip.setTabs(listOf(SessionTabPresentation("x"), SessionTabPresentation("x")), "x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            strip.setTabs(listOf(SessionTabPresentation("x")), "missing")
        }
        assertTrue(strip.closeVisible("a"))
        assertNotNull(strip.boundsFor("a"))
        strip.setTabs(emptyList(), null)
        assertNull(strip.boundsFor("a"))
    }

    @Test
    fun `wrapped rows return to the same layout after widening and narrowing`() = onEdt {
        val tabs = (0 until 6).map { SessionTabPresentation("tab-$it") }
        val toolbarWidth = JBUI.scale(108)
        val strip = SessionTabStrip().apply {
            setTabs(tabs, "tab-0")
            add(JPanel().apply { preferredSize = Dimension(toolbarWidth, JBUI.scale(34)) }, BorderLayout.EAST)
        }
        val tabWidth = strip.boundsFor("tab-0")!!.width
        val tabHeight = strip.boundsFor("tab-0")!!.height
        val root = JPanel(BorderLayout()).apply {
            add(strip, BorderLayout.NORTH)
            add(JPanel(), BorderLayout.CENTER)
        }
        strip.setWrapTabs(true)
        val targetWidth = toolbarWidth + tabWidth * 2
        fun resize(width: Int, height: Int = tabHeight * 7): List<Rectangle> {
            root.setSize(width, height)
            // Give the parent and viewport repeated layout passes, as Swing does after resize.
            repeat(4) { root.doLayout(); layout(strip) }
            return tabs.map { strip.boundsFor(it.id)!! }
        }
        resize(targetWidth - 1)
        assertTrue(strip.scrollPane.verticalScrollBar.isVisible)
        val widened = resize(targetWidth)
        assertFalse(strip.scrollPane.verticalScrollBar.isVisible, "Two tabs per row fit without a scrollbar")
        assertEquals(tabHeight * 3, strip.height)
        resize(targetWidth + tabWidth)
        val narrowed = resize(targetWidth)
        assertEquals(widened, narrowed, "The same width must not depend on previous scrollbar visibility")
        assertFalse(strip.scrollPane.verticalScrollBar.isVisible)
        assertEquals(tabHeight * 3, strip.height)
        for (bounds in narrowed) assertTrue(strip.scrollPane.viewport.viewRect.contains(bounds))

        resize(targetWidth - 1)
        assertTrue(strip.scrollPane.verticalScrollBar.isVisible)
        strip.setTabs(tabs.take(3), "tab-0")
        repeat(4) { root.doLayout(); layout(strip) }
        assertFalse(strip.scrollPane.verticalScrollBar.isVisible, "Removing tabs must release unneeded scrollbar width")
        assertEquals(tabHeight * 3, strip.height)

        strip.setTabs(tabs, "tab-0")
        resize(targetWidth - 1)
        assertTrue(strip.scrollPane.verticalScrollBar.isVisible)
        val expanded = resize(targetWidth - 1, tabHeight * 14)
        assertFalse(strip.scrollPane.verticalScrollBar.isVisible, "Increasing parent height must release the scrollbar")
        assertEquals(tabHeight * 6, strip.height)
        for (bounds in expanded) assertTrue(strip.scrollPane.viewport.viewRect.contains(bounds))
    }

    @Test
    fun `fixed parent retains chat and composer while every wrapped row remains reachable`() {
        lateinit var strip: SessionTabStrip
        lateinit var root: JPanel
        lateinit var chat: JPanel
        lateinit var composer: JPanel
        val tabs = (0 until 20).map { SessionTabPresentation("tab-$it") }
        val actions = mutableListOf<String>()
        fun layoutRoot() {
            root.doLayout()
            layout(strip)
            chat.doLayout()
        }
        onEdt {
            strip = SessionTabStrip().apply {
                add(JPanel(BorderLayout()).apply {
                    add(JPanel().apply { preferredSize = Dimension(108, 34) }, BorderLayout.NORTH)
                }, BorderLayout.EAST)
                setTabs(tabs, "tab-0")
                setWrapTabs(true)
                onSelect = { setTabs(tabs, it) }
                onClose = { actions += "close:$it" }
                onMove = { id, index -> actions += "move:$id:$index" }
            }
            composer = JPanel().apply { preferredSize = Dimension(100, 100) }
            chat = JPanel(BorderLayout()).apply {
                add(JPanel(), BorderLayout.CENTER)
                add(composer, BorderLayout.SOUTH)
            }
            root = JPanel(BorderLayout()).apply {
                add(strip, BorderLayout.NORTH)
                add(chat, BorderLayout.CENTER)
                setSize(320, 500)
            }
            layoutRoot()
        }
        // Selection is deferred to the next EDT turn, as in the real tool window.
        for (height in listOf(500, 300, 700)) {
            onEdt {
                root.setSize(320, height)
                layoutRoot()
                assertTrue(strip.height <= height / 2)
                assertTrue(chat.height >= height / 2)
                assertEquals(100, composer.height)
                assertTrue(composer.y >= 0)
                assertTrue(strip.scrollPane.verticalScrollBar.isVisible)
                assertFalse(strip.scrollPane.horizontalScrollBar.isVisible)
            }
            for (tab in tabs) {
                onEdt { strip.setTabs(tabs, tab.id); layoutRoot() }
                onEdt {
                    assertTrue(strip.scrollPane.viewport.viewRect.contains(strip.boundsFor(tab.id)), tab.id)
                    assertTrue(strip.boundsFor(tab.id)!!.maxX <= strip.scrollPane.viewport.width)
                }
            }
        }
        onEdt {
            val pane = strip.scrollPane
            val bar = pane.verticalScrollBar
            val before = bar.value
            val wheel = MouseWheelEvent(strip.eventTarget, MouseEvent.MOUSE_WHEEL, 0, 0, 40, 10, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1)
            strip.eventTarget.dispatchEvent(wheel)
            assertTrue(wheel.isConsumed)
            assertTrue(bar.value < before)
            val point = Point(40, 10)
            mouse(strip.eventTarget, MouseEvent.MOUSE_MOVED,
                SwingUtilities.convertPoint(pane.viewport, point, strip.eventTarget))
            val hovered = tabs.single { strip.boundsFor(it.id)!!.contains(Point(point.x, point.y + bar.value)) }
            assertTrue(strip.closeVisible(hovered.id))
            strip.eventTarget.scrollRectToVisible(strip.boundsFor(hovered.id)!!)
            val close = strip.closeBoundsFor(hovered.id)!!
            click(strip.eventTarget, Point(close.x + close.width / 2, close.y + close.height / 2))
            strip.eventTarget.actionMap.get("close").actionPerformed(null)
            strip.eventTarget.actionMap.get("moveLeft").actionPerformed(null)
            assertEquals(listOf("close:${hovered.id}", "close:tab-19", "move:tab-19:18"), actions)
            strip.setWrapTabs(false)
            layoutRoot()
            assertEquals(strip.boundsFor("tab-0")!!.height, strip.height)
            assertEquals(0, pane.viewport.viewPosition.y)
            assertFalse(bar.isVisible)
            assertTrue(pane.horizontalScrollBar.isVisible)
        }
        onEdt { assertTrue(strip.scrollPane.viewport.viewRect.contains(strip.boundsFor("tab-19"))) }
    }

    @Test
    fun `wrapped drag scrolls beyond the viewport and releases against visible row IDs`() {
        lateinit var strip: SessionTabStrip
        val moves = mutableListOf<Pair<String, Int>>()
        onEdt {
            strip = SessionTabStrip().apply {
                setTabs((0 until 20).map { SessionTabPresentation("tab-$it") }, "tab-0")
                setWrapTabs(true)
                onMove = { id, index -> moves += id to index }
            }
            JPanel(BorderLayout()).apply {
                add(strip, BorderLayout.NORTH)
                add(JPanel(), BorderLayout.CENTER)
                setSize(200, 200)
                doLayout()
            }
            layout(strip)
        }
        onEdt {
            mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "tab-0"))
            mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, Point(40, strip.scrollPane.viewport.height - 2))
            assertTrue(strip.dragAutoScrollRunning)
        }
        try {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            var scrolled = false
            while (!scrolled && System.nanoTime() < deadline) {
                Thread.sleep(25)
                onEdt { scrolled = strip.scrollPane.viewport.viewPosition.y > 0 }
            }
            assertTrue(scrolled, "Dragging at the bottom must scroll wrapped rows")
            onEdt {
                strip.scrollPane.verticalScrollBar.value = Int.MAX_VALUE
                val last = strip.boundsFor("tab-19")!!
                val end = Point(last.x + last.width - 2, last.y + last.height / 2)
                assertTrue(strip.scrollPane.viewport.viewRect.contains(end))
                mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, end)
                mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, end)
                assertEquals(listOf("tab-0" to 19), moves)
                assertFalse(strip.dragAutoScrollRunning)
            }
        } finally {
            onEdt { strip.eventTarget.actionMap.get("cancelDrag").actionPerformed(null) }
        }
    }

    @Test
    fun `wrapped rows follow available width and tab additions without horizontal scrolling`() = onEdt {
        val strip = SessionTabStrip()
        val tabs = ('a'..'e').map { SessionTabPresentation(it.toString()) }
        strip.setTabs(tabs, "a")
        val tabWidth = strip.boundsFor("a")!!.width
        val tabHeight = strip.boundsFor("a")!!.height
        strip.setSize(tabWidth * 2, tabHeight)
        strip.setWrapTabs(true)
        strip.setSize(strip.width, strip.preferredSize.height)
        layout(strip)
        assertEquals(tabHeight * 3, strip.preferredSize.height)
        assertEquals(Point(0, tabHeight), strip.boundsFor("c")!!.location)
        assertEquals(Point(tabWidth, tabHeight), strip.boundsFor("d")!!.location)
        assertFalse(strip.scrollPane.horizontalScrollBar.isVisible)
        assertFalse(strip.scrollPane.verticalScrollBar.isVisible)
        for (tab in tabs) assertTrue(strip.scrollPane.viewport.bounds.contains(strip.boundsFor(tab.id)))

        strip.setSize(tabWidth * 3, strip.height)
        strip.setSize(strip.width, strip.preferredSize.height)
        layout(strip)
        assertEquals(tabHeight * 2, strip.preferredSize.height)
        assertEquals(Point(tabWidth * 2, 0), strip.boundsFor("c")!!.location)
        strip.setTabs(tabs.take(2), "a")
        assertEquals(tabHeight, strip.preferredSize.height)
        strip.setTabs(tabs, "a")
        assertEquals(tabHeight * 2, strip.preferredSize.height)

        strip.setSize(tabWidth - 10, strip.height)
        strip.setSize(strip.width, strip.preferredSize.height)
        layout(strip)
        assertEquals(tabHeight * tabs.size, strip.preferredSize.height)
        assertTrue(tabs.all { strip.boundsFor(it.id)!!.width == strip.width })
    }

    @Test
    fun `wrapping toggle resets scrolling and restores natural one row widths`() = onEdt {
        val strip = fixture()
        val naturalWidth = strip.boundsFor("b")!!.width
        strip.scrollPane.horizontalScrollBar.value = 100
        assertTrue(strip.scrollPane.viewport.viewPosition.x > 0)
        strip.setWrapTabs(true)
        strip.setSize(strip.width, strip.preferredSize.height)
        layout(strip)
        assertEquals(0, strip.scrollPane.viewport.viewPosition.x)
        assertTrue(strip.boundsFor("c")!!.y > 0)
        strip.setWrapTabs(false)
        strip.setSize(strip.width, strip.preferredSize.height)
        layout(strip)
        assertEquals(0, strip.boundsFor("c")!!.y)
        assertEquals(naturalWidth, strip.boundsFor("b")!!.width)
        assertTrue(strip.scrollPane.horizontalScrollBar.isVisible)
        assertTrue(strip.closeVisible("a"))
    }

    @Test
    fun `returning to one row reveals the selected tab after layout`() {
        lateinit var strip: SessionTabStrip
        onEdt {
            strip = wrappedFixture()
            strip.setTabs(('a'..'d').map { SessionTabPresentation(it.toString()) }, "d")
            strip.setWrapTabs(false)
            strip.setSize(strip.width, strip.preferredSize.height)
            layout(strip)
        }
        onEdt {
            assertTrue(strip.scrollPane.viewport.viewRect.contains(strip.boundsFor("d")))
            assertTrue(strip.closeVisible("d"))
        }
    }

    @Test
    fun `second row hit testing closing and keyboard actions retain tab IDs`() = onEdt {
        val strip = wrappedFixture()
        val actions = mutableListOf<String>()
        strip.onSelect = { actions += "select:$it" }
        strip.onClose = { actions += "close:$it" }
        strip.onMove = { id, index -> actions += "move:$id:$index" }
        click(strip.eventTarget, center(strip, "d"))
        mouse(strip.eventTarget, MouseEvent.MOUSE_MOVED, center(strip, "c"))
        val close = strip.closeBoundsFor("c")!!
        assertTrue(close.y > strip.boundsFor("a")!!.height)
        click(strip.eventTarget, Point(close.x + close.width / 2, close.y + close.height / 2))
        strip.setTabs(('a'..'d').map { SessionTabPresentation(it.toString()) }, "c")
        for (action in listOf("previous", "next", "close", "moveRight")) {
            strip.eventTarget.actionMap.get(action).actionPerformed(null)
        }
        assertEquals(listOf("select:d", "close:c", "select:b", "select:d", "close:c", "move:c:3"), actions)
    }

    @Test
    fun `dragging between rows uses both coordinates and wrapping changes cancel drag`() = onEdt {
        val strip = wrappedFixture()
        val moves = mutableListOf<Pair<String, Int>>()
        strip.onMove = { id, index -> moves += id to index }
        fun drag(id: String, destination: Point) {
            mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, id))
            mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, destination)
            assertFalse(strip.dragAutoScrollRunning)
            mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, destination)
        }
        val last = strip.boundsFor("d")!!
        drag("a", Point(last.x + last.width - 2, last.y + last.height / 2))
        drag("d", Point(2, strip.boundsFor("a")!!.height / 2))
        assertEquals(listOf("a" to 3, "d" to 0), moves)
        mouse(strip.eventTarget, MouseEvent.MOUSE_PRESSED, center(strip, "a"))
        mouse(strip.eventTarget, MouseEvent.MOUSE_DRAGGED, center(strip, "d"))
        strip.setWrapTabs(false)
        mouse(strip.eventTarget, MouseEvent.MOUSE_RELEASED, center(strip, "d"))
        assertEquals(2, moves.size)
        assertFalse(strip.dragAutoScrollRunning)
    }

    @Test
    fun `wrapped painting keeps the selected background in its row and reserves fixed actions`() = onEdt {
        val strip = wrappedFixture()
        val toolbar = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JPanel().apply { preferredSize = Dimension(80, 34) }, BorderLayout.NORTH)
        }
        strip.add(toolbar, BorderLayout.EAST)
        strip.setSize(strip.width + toolbar.preferredSize.width, strip.height)
        strip.setTabs(('a'..'d').map { SessionTabPresentation(it.toString()) }, "c")
        layout(strip)
        toolbar.doLayout()
        val image = BufferedImage(strip.width, strip.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            strip.paint(graphics)
            val selected = strip.boundsFor("c")!!
            assertEquals(AgentUiColors.panelBackground.rgb, image.getRGB(selected.x + 4, selected.y + 4))
            assertEquals(AgentUiColors.tabAreaBackground.rgb, image.getRGB(4, 4))
            assertTrue(('a'..'d').all { strip.boundsFor(it.toString())!!.maxX <= toolbar.x })
            assertEquals(0, toolbar.components.single().y)
            assertEquals(34, toolbar.components.single().height)
        } finally {
            graphics.dispose()
        }
    }

    private fun wrappedFixture(): SessionTabStrip = SessionTabStrip().apply {
        setTabs(('a'..'d').map { SessionTabPresentation(it.toString()) }, "a")
        setSize(boundsFor("a")!!.width * 2, preferredSize.height)
        setWrapTabs(true)
        setSize(width, preferredSize.height)
        layout(this)
    }

    private fun fixture(): SessionTabStrip = SessionTabStrip().apply {
        setTabs(listOf(SessionTabPresentation("a"), SessionTabPresentation("b", "いろはにほへとちりぬるを"), SessionTabPresentation("c")), "a")
        setSize(320, 40)
        layout(this)
    }

    private fun layout(strip: SessionTabStrip) {
        strip.doLayout()
        strip.scrollPane.doLayout()
        strip.scrollPane.viewport.doLayout()
    }

    private fun center(strip: SessionTabStrip, id: String): Point = strip.boundsFor(id)!!.let {
        Point(it.x + it.width / 2, it.y + it.height / 2)
    }

    private fun click(target: JComponent, point: Point) {
        mouse(target, MouseEvent.MOUSE_MOVED, point)
        mouse(target, MouseEvent.MOUSE_PRESSED, point)
        mouse(target, MouseEvent.MOUSE_RELEASED, point)
    }

    private fun event(target: JComponent, type: Int, point: Point) = MouseEvent(
        target, type, 0, 0, point.x, point.y, 0, 0, 1, false,
        if (type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_RELEASED) MouseEvent.BUTTON1 else MouseEvent.NOBUTTON,
    )

    private fun mouse(target: JComponent, type: Int, point: Point) = target.dispatchEvent(event(target, type, point))
    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)
}
