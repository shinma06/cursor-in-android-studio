package com.cursoragent.ui.session

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class SessionTabStripTest {
    @Test
    fun `title limits count graphemes rather than UTF16 units`() {
        assertEquals("New Agent", SessionTabPresentation("a").displayTitle)
        assertEquals("New Agent", SessionTabPresentation("a", " ").fullTitle)
        assertEquals("あいうえおかきくけ", abbreviateSessionTitle("あいうえおかきくけ"))
        assertEquals("あいうえおかきくけ…", abbreviateSessionTitle("あいうえおかきくけこ"))
        val combined = "か\u3099"
        val family = "👨‍👩‍👧‍👦"
        assertEquals(combined.repeat(9) + "…", abbreviateSessionTitle(combined.repeat(10)))
        assertEquals(family.repeat(9) + "…", abbreviateSessionTitle(family.repeat(10)))
        assertEquals("🇯🇵".repeat(9) + "…", abbreviateSessionTitle("🇯🇵".repeat(10)))
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
