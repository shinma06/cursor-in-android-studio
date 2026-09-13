package com.cursoragent.ui.composer.context

import com.cursoragent.parser.TokenUsage
import com.cursoragent.session.SessionTabs
import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.updateCurrentTurnOnEdt
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContextUsageLifecycleTest {
    private val usage = TokenUsage(11696, 129, 6720, 0)

    @Test
    fun `terminal seals each outcome exactly once and preserves that response counters`() {
        for (outcome in listOf(UsagePhase.COMPLETED, UsagePhase.STOPPED, UsagePhase.FAILED)) {
            val state = ContextUsageState()
            assertEquals(UsagePhase.NOT_STARTED, state.phase)
            assertFalse(state.accept(0, usage))
            val ticket = state.begin("model-a")
            assertEquals(UsagePhase.RUNNING, state.phase)
            assertTrue(state.accept(ticket, usage))
            assertTrue(state.finish(ticket, outcome))
            assertFalse(state.accept(ticket, usage.copy(inputTokens = 999)))
            assertFalse(state.finish(ticket, UsagePhase.COMPLETED))
            assertFalse(state.stop())
            assertEquals(outcome, state.phase)
            assertEquals(usage, state.usage)
            assertEquals("model-a", state.model)
            val next = state.begin("model-b")
            assertNull(state.usage)
            assertEquals("model-b", state.model)
            assertFalse(state.finish(ticket, UsagePhase.FAILED))
            assertFalse(state.accept(ticket, usage))
            assertTrue(state.accept(next, TokenUsage(398, 63, 18528, 0)))
        }
    }

    @Test
    fun `stop and reset reject already queued counters and reset does not start a response`() {
        val state = ContextUsageState()
        val ticket = state.begin()
        assertTrue(state.stop())
        assertFalse(state.accept(ticket, usage))
        assertEquals(UsagePhase.STOPPING, state.phase)
        assertFalse(state.stop())
        assertTrue(state.finish(ticket, UsagePhase.FAILED))
        assertEquals(UsagePhase.FAILED, state.phase)
        state.clear()
        assertEquals(UsagePhase.NOT_STARTED, state.phase)
        assertEquals("", state.model)
        assertNull(state.usage)
        assertFalse(state.finish(ticket, UsagePhase.COMPLETED))
        assertFalse(state.accept(ticket, usage))
    }

    @Test
    fun `no report is distinct from explicit zero and a failed response retains received fields`() = SwingUtilities.invokeAndWait {
        val view = ContextUsageView()
        view.button.doClick(0)
        assertTrue(texts(view.panel).contains("まだ応答を開始していません"))
        val first = view.beginTurn("model-a")
        assertTrue(texts(view.panel).contains("応答を準備・実行中"))
        view.finish(first, UsagePhase.COMPLETED)
        assertTrue(texts(view.panel).containsAll(listOf("完了した応答", "この応答では情報未提供です")))
        assertFalse(texts(view.panel).contains("0"))
        view.update(first, usage)
        assertFalse(texts(view.panel).contains("11,696"))
        val second = view.beginTurn("model-b")
        view.update(second, TokenUsage(null, 0, null, 0))
        view.finish(second, UsagePhase.FAILED)
        val failed = texts(view.panel)
        assertTrue(failed.containsAll(listOf("失敗した応答", "送信時のモデル: model-b", "出力", "キャッシュ書き込み", "0")))
        assertFalse(failed.contains("入力"))
        view.button.doClick(0)
        view.button.doClick(0)
        assertEquals(failed, texts(view.panel))
        view.reset()
        assertTrue(texts(view.panel).contains("まだ応答を開始していません"))
        assertFalse(texts(view.panel).contains("0"))
    }

    @Test
    fun `tab visibility switch preserves own values and finished run rejects queued UI updates`() {
        val tabs = SessionTabs()
        val firstTab = tabs.snapshot().selectedId
        tabs.updateComposer(firstTab, AgentMode.AGENT, "model-a", "prompt", 0)
        val firstRun = tabs.beginTurn(firstTab)!!.token
        val state = ContextUsageState()
        val ticket = state.begin("model-a")
        SwingUtilities.invokeAndWait {
            val other = tabs.open()
            tabs.select(other.id)
            updateCurrentTurnOnEdt({ false }, { tabs.accepts(firstRun) }, { false }) { state.accept(ticket, usage) }
            assertEquals(usage, state.usage)
            val worker = Thread {
                updateCurrentTurnOnEdt({ false }, { tabs.accepts(firstRun) }, { false }) { state.accept(ticket, usage.copy(inputTokens = 999)) }
            }
            worker.start()
            worker.join()
            state.finish(ticket, UsagePhase.COMPLETED)
            tabs.finishTurn(firstRun)
        }
        SwingUtilities.invokeAndWait { assertEquals(usage, state.usage) }
    }

    @Test
    fun `queued callbacks cannot cross disposal replacement or stop even with a matching ticket`() {
        for (boundary in listOf("dispose", "replace", "stop")) {
            val state = ContextUsageState()
            val ticket = state.begin()
            var disposed = false
            var current = true
            var stopped = false
            SwingUtilities.invokeAndWait {
                val worker = Thread {
                    updateCurrentTurnOnEdt({ disposed }, { current }, { stopped }) { state.accept(ticket, usage) }
                }
                worker.start()
                worker.join()
                when (boundary) {
                    "dispose" -> disposed = true
                    "replace" -> current = false
                    else -> stopped = true
                }
            }
            SwingUtilities.invokeAndWait { assertNull(state.usage, boundary) }
        }
    }

    @Test
    fun `adopted research print fixtures match production parser fields without totals`() {
        val fixture = com.google.gson.JsonParser.parseString(
            java.nio.file.Files.readString(java.nio.file.Path.of("docs/research/issue-149-fixtures.json")),
        ).asJsonObject
        val examples = fixture.getAsJsonObject("synthetic").getAsJsonArray("print").map {
            it.asJsonObject["input"] to it.asJsonObject.getAsJsonObject("expected")
        } + fixture.getAsJsonObject("live_projection").getAsJsonArray("print").map {
            it.asJsonObject["usage"] to it.asJsonObject.getAsJsonObject("usage")
        }
        for ((input, expected) in examples) {
            val parsed = TokenUsage.parse(input)
            val actual = mapOf(
                "inputTokens" to parsed?.inputTokens, "outputTokens" to parsed?.outputTokens,
                "cacheReadTokens" to parsed?.cacheReadTokens, "cacheWriteTokens" to parsed?.cacheWriteTokens,
            ).filterValues { it != null }
            assertEquals(expected.entrySet().associate { it.key to it.value.asLong }, actual)
        }
    }

    @Test
    fun `long selected model stays within a narrow panel and is treated as plain text`() = SwingUtilities.invokeAndWait {
        val view = ContextUsageView()
        view.button.doClick(0)
        val model = "<html>" + "long-model-".repeat(30)
        val ticket = view.beginTurn(model)
        view.update(ticket, usage)
        view.finish(ticket, UsagePhase.COMPLETED)
        view.panel.setSize(260, view.panel.preferredSize.height)
        fun layout(c: Container) {
            c.doLayout()
            c.components.filterIsInstance<Container>().forEach(::layout)
        }
        layout(view.panel)
        fun labels(c: Container): List<JLabel> = c.components.flatMap {
            (if (it is JLabel) listOf(it) else emptyList()) + (if (it is Container) labels(it) else emptyList())
        }
        val label = labels(view.panel).single { it.text.startsWith("送信時のモデル:") }
        assertEquals(true, label.getClientProperty("html.disable"))
        assertEquals("送信時のモデル: $model", label.text)
        assertTrue(label.width > 0 && label.x + label.width <= label.parent.width)
        assertTrue(label.toolTipText.contains(model))
    }

    private fun texts(container: Container): List<String> = container.components.filter(Component::isVisible).flatMap {
        (if (it is JLabel) listOf(it.text) else emptyList()) + (if (it is Container) texts(it) else emptyList())
    }
}
