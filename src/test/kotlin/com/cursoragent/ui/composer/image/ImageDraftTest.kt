package com.cursoragent.ui.composer.image

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ImageDraftTest {
    @TempDir lateinit var parent: Path
    private class Tasks : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.remove().run() }
    }
    private fun image() = ImageInput.clipboard(BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB))
    private fun count() = Files.walk(parent).use { paths -> paths.filter { it.fileName.toString().endsWith(".png") }.count() }

    @Test
    fun `removal model replacement and disposal reject a delayed import and release only its snapshot`() {
        for (action in listOf("clear", "invalidate", "close")) ImageAttachmentStore(parent).use { store ->
            val worker = Tasks()
            val ui = Tasks()
            val draft = ImageDraft(worker, { ui.execute(it) }, { store }, {})
            assertTrue(draft.import(::image))
            assertTrue(draft.importing)
            assertEquals(0, count())
            worker.drain()
            assertEquals(1, count())
            when (action) {
                "clear" -> draft.clear()
                "invalidate" -> draft.invalidateImport()
                "close" -> draft.close()
            }
            ui.drain()
            worker.drain()
            assertNull(draft.attachment)
            assertNull(draft.preview)
            assertEquals(0, count())
            draft.close()
        }
    }

    @Test
    fun `image-only and pending drafts require explicit discard until removed`() {
        ImageAttachmentStore(parent).use { store ->
            val worker = Tasks()
            val ui = Tasks()
            val draft = ImageDraft(worker, { ui.execute(it) }, { store }, {})
            assertFalse(draft.hasUnsent)
            draft.import(::image)
            assertTrue(draft.hasUnsent) // Closing before worker/delivery must ask, too.
            worker.drain()
            assertTrue(draft.hasUnsent)
            ui.drain()
            assertTrue(draft.hasUnsent)
            draft.clear() // Explicit removal allows closing the otherwise empty draft.
            assertFalse(draft.hasUnsent)
            worker.drain()
            assertEquals(0, count())
            draft.close()
        }
    }

    @Test
    fun `pending and attached images reject replacement while an independent queue lease survives removal`() {
        ImageAttachmentStore(parent).use { store ->
            val worker = Tasks()
            val ui = Tasks()
            val draft = ImageDraft(worker, { ui.execute(it) }, { store }, {})
            draft.import(::image)
            assertFalse(draft.import { error("must not read a second source") })
            worker.drain()
            ui.drain()
            val expected = draft.attachment!!.bytes()
            assertEquals(20, draft.preview!!.width)
            assertFalse(draft.import { error("must not replace") })
            assertNotNull(draft.error)
            val queue = draft.retain()!!
            draft.clear()
            worker.drain()
            assertArrayEquals(expected, queue.bytes())
            assertEquals(1, count())
            queue.close()
            assertEquals(0, count())
            draft.close()
        }
    }

    @Test
    fun `old delivery cannot overwrite a new draft and raw source failures cannot expose private paths`() {
        ImageAttachmentStore(parent).use { store ->
            val worker = Tasks()
            val ui = Tasks()
            val draft = ImageDraft(worker, { ui.execute(it) }, { store }, {})
            draft.import(::image)
            worker.drain()
            draft.clear()
            draft.import { throw IllegalArgumentException("/private/original/secret.png") }
            worker.drain()
            ui.drain()
            worker.drain()
            assertNull(draft.attachment)
            assertFalse(draft.importing)
            assertNotNull(draft.error)
            assertFalse(draft.error!!.contains("private"))
            assertEquals(0, count())
            draft.import { throw ImageInputException("画像は1枚までです。") }
            worker.drain()
            ui.drain()
            assertEquals("画像は1枚までです。", draft.error)
            draft.close()
        }
    }

    @Test
    fun `changed tab connection or model rejects pending bytes before they can attach to the new scope`() {
        ImageAttachmentStore(parent).use { store ->
            val worker = Tasks()
            val ui = Tasks()
            var scope = "old-model-and-tab"
            val draft = ImageDraft(worker, { ui.execute(it) }, { store }, {}, scope = { scope })
            draft.import(::image)
            worker.drain()
            scope = "new-model-or-tab"
            ui.drain()
            worker.drain()
            assertNull(draft.attachment)
            assertFalse(draft.importing)
            assertNotNull(draft.error)
            assertEquals(0, count())
            draft.close()
        }
    }

    @Test
    fun `native text transfers are untouched and multi-file transfers cannot fall back to an image flavor`() {
        assertFalse(ImageTransfer.accepts(StringSelection("native text").transferDataFlavors))
        val transfer = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in transferDataFlavors
            override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                DataFlavor.javaFileListFlavor -> listOf(parent.resolve("a.png").toFile(), parent.resolve("b.png").toFile())
                else -> error("must not silently take an alternate image")
            }
        }
        assertTrue(ImageTransfer.accepts(transfer.transferDataFlavors))
        assertThrows(ImageInputException::class.java) { ImageTransfer.read(transfer) }
    }
}
