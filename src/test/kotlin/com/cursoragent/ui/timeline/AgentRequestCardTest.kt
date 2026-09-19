package com.cursoragent.ui.timeline

import com.cursoragent.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

class AgentRequestCardTest {
    @Test
    fun `permission target missing disables allow and cancellation disables all late controls`() {
        val replies = mutableListOf<AgentAnswer>()
        val request = AgentInputRequest(AgentInput.Permission(AgentTool("id"), listOf(
            PermissionOption("a", "Allow", "allow_once"), PermissionOption("r", "Reject", "reject_once"),
        )), { answer -> replies.add(answer); answer })
        lateinit var card: AgentRequestCard
        SwingUtilities.invokeAndWait {
            card = AgentRequestCard(request)
            val buttons = card.components.filterIsInstance<JButton>()
            assertFalse(buttons.first { it.text.startsWith("今回だけ許可") }.isEnabled)
            buttons.first { it.text.startsWith("今回は拒否") }.doClick(0)
        }
        SwingUtilities.invokeAndWait {
            assertTrue(card.components.filterIsInstance<AbstractButton>().none { it.isEnabled })
            card.components.filterIsInstance<JButton>().forEach { it.doClick(0) }
        }
        assertEquals(listOf(AgentAnswer.Permission("r")), replies)
    }

    @Test
    fun `question selection enables one exact answer and stopped plan cannot accept`() {
        val replies = mutableListOf<AgentAnswer>()
        val request = AgentInputRequest(AgentInput.Questions(null, listOf(
            Question("q", "質問", listOf(QuestionOption("a", "はい"), QuestionOption("b", "いいえ")), false),
        )), { answer -> replies.add(answer); answer })
        SwingUtilities.invokeAndWait {
            val card = AgentRequestCard(request)
            val send = card.components.filterIsInstance<JButton>().first { it.text == "回答を送信" }
            assertFalse(send.isEnabled)
            card.components.filterIsInstance<JRadioButton>().last().doClick(0)
            assertTrue(send.isEnabled)
            send.doClick(0)
        }
        assertEquals(listOf(AgentAnswer.Questions(mapOf("q" to listOf("b")))), replies)
        val plan = AgentInputRequest(AgentInput.Plan("Plan", null, "内容"), { answer -> replies.add(answer); answer })
        plan.answer(AgentAnswer.Cancel)
        SwingUtilities.invokeAndWait {
            val card = AgentRequestCard(plan)
            assertTrue(card.components.filterIsInstance<AbstractButton>().none { it.isEnabled })
        }
        assertEquals(AgentAnswer.Cancel, replies.last())
    }
    @Test
    fun `effective cancelled answer is shown even when allow was clicked before stop`() {
        val request = AgentInputRequest(AgentInput.Permission(AgentTool("id", command = "synthetic"), listOf(
            PermissionOption("a", "Allow", "allow_once"),
        ))) { AgentAnswer.Cancel }
        lateinit var card: AgentRequestCard
        SwingUtilities.invokeAndWait {
            card = AgentRequestCard(request)
            card.components.filterIsInstance<JButton>().first { it.text.startsWith("今回だけ許可") }.doClick(0)
        }
        assertEquals(AgentAnswer.Cancel, request.resolved.join())
        SwingUtilities.invokeAndWait {
            val text = card.components.filterIsInstance<javax.swing.JTextArea>().last().text
            assertEquals("取り消す回答を送信しました", text)
        }
    }

    @Test
    fun `always permission scope is plain text and one opaque reply is reported as a reply`() {
        for (kind in listOf("allow_always", "reject_always")) {
            val replies = mutableListOf<AgentAnswer>()
            val id = "opaque:$kind"
            val request = AgentInputRequest(AgentInput.Permission(AgentTool("tool", command = "synthetic"),
                listOf(PermissionOption(id, "<html>external label</html>", kind), PermissionOption("unknown", "Unknown", "future"))),
                { answer -> replies.add(answer); answer })
            lateinit var card: AgentRequestCard
            SwingUtilities.invokeAndWait {
                card = AgentRequestCard(request)
                val texts = card.components.filterIsInstance<javax.swing.JTextArea>().map { it.text }
                assertTrue(texts.any { it.contains("適用範囲・有効期間は接続先") })
                val buttons = card.components.filterIsInstance<JButton>()
                assertFalse(buttons.first { it.text.startsWith("未対応") }.isEnabled)
                val button = buttons.first { it.text.startsWith("今後も") }
                assertEquals(true, button.getClientProperty("html.disable"))
                button.doClick(0)
                button.doClick(0)
            }
            SwingUtilities.invokeAndWait {
                assertTrue(card.components.filterIsInstance<javax.swing.JTextArea>().last().text.contains("回答を送信しました"))
                assertTrue(card.components.filterIsInstance<javax.swing.JTextArea>().last().text.contains("適用範囲は接続先"))
                assertTrue(card.components.filterIsInstance<AbstractButton>().none { it.isEnabled })
            }
            assertEquals(listOf(AgentAnswer.Permission(id)), replies)
        }
    }
}
