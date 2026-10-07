package com.cursoragent.ui

import com.cursoragent.settings.AgentMode
import com.cursoragent.ui.composer.image.ImageAttachmentStore
import com.cursoragent.ui.composer.image.ImageInput
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class QueuedImageTest {
    @TempDir lateinit var parent: Path
    private fun image() = ImageInput.clipboard(BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB))
    private fun count() = Files.walk(parent).use { it.filter { path -> path.fileName.toString().endsWith(".png") }.count() }

    @Test
    fun `image-only session begins once with an explicit attachment and never invents prompt text`() {
        val sessions = com.cursoragent.session.SessionTabs()
        val tab = sessions.snapshot().selected
        assertNull(sessions.beginTurn(tab.id))
        val turn = sessions.beginTurn(tab.id, hasAttachment = true)!!
        assertEquals("", turn.prompt)
        assertNull(sessions.beginTurn(tab.id, hasAttachment = true))
        assertTrue(sessions.finishTurn(turn.token))
        assertNull(sessions.beginTurn(tab.id))
    }

    @Test
    fun `image-only queue transfers its snapshot to the turn and failed turn restores without automatic resend`() {
        ImageAttachmentStore(parent).use { store ->
            val draft = store.save(image())
            val expected = draft.bytes()
            val queue = PromptQueue("one")
            assertTrue(queue.add("", AgentMode.ASK, "model-a", image = draft.retain()))
            draft.close()
            val first = queue.next()!!
            queue.edit(first.id, "edited")
            queue.resume()
            val ticket = queue.ticket(1)!!
            lateinit var flight: QueuedPrompt
            assertTrue(queue.dispatch(ticket, 1, true) { flight = it; true })
            assertEquals(0, queue.size)
            assertArrayEquals(expected, flight.image!!.bytes())
            queue.restoreUnsent(flight)
            queue.restoreUnsent(flight)
            assertNull(queue.ticket(2))
            assertEquals(1, queue.size)
            assertEquals("edited", queue.snapshot().single().text)
            queue.resume()
            assertTrue(queue.dispatch(queue.ticket(2)!!, 2, true) { flight = it; true })
            flight.image!!.close() // Successful completion releases this turn's last owner.
            assertEquals(0, count())
        }
    }

    @Test
    fun `edit move discard and close preserve other images and never release a surviving owner`() {
        ImageAttachmentStore(parent).use { store ->
            val queue = PromptQueue("one")
            queue.add("", AgentMode.AGENT, "a", image = store.save(image()))
            queue.add("text", AgentMode.PLAN, "b", image = store.save(image()))
            val entries = queue.snapshot()
            val otherOwner = entries[1].image!!.retain()
            assertTrue(queue.edit(entries[0].id, ""))
            queue.move(entries[1].id, -1)
            queue.removeImage(entries[0].id)
            assertEquals(listOf(entries[1].id), queue.snapshot().map { it.id })
            assertEquals(1, count())
            queue.removeImage(entries[1].id)
            assertEquals("text", queue.snapshot().single().text)
            assertNull(queue.snapshot().single().image)
            queue.clear()
            assertArrayEquals(image().bytes(), otherOwner.bytes())
            otherOwner.close()
            assertEquals(0, count())
        }
    }
}
