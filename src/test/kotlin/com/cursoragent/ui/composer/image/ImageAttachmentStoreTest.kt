package com.cursoragent.ui.composer.image

import com.google.gson.Gson
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ImageAttachmentStoreTest {
    @TempDir lateinit var parent: Path
    private fun image() = ImageInput.clipboard(BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB))
    private fun roots() = Files.newDirectoryStream(parent).use { it.toList() }
    private fun images(root: Path) = Files.newDirectoryStream(root, "*.png").use { it.toList() }

    @Test
    fun `draft queue and in-flight leases keep their own snapshot until the last owner releases`() {
        ImageAttachmentStore(parent).use { store ->
            val draft = store.save(image())
            val queued = draft.retain()
            val flight = queued.retain()
            val expected = draft.bytes()
            draft.close()
            draft.close()
            assertArrayEquals(expected, queued.bytes())
            queued.close()
            assertArrayEquals(expected, flight.bytes())
            assertEquals(1, images(roots().single()).size)
            flight.close()
            assertTrue(images(roots().single()).isEmpty())
        }
        assertTrue(roots().isEmpty())
    }

    @Test
    fun `source mutation returned-buffer mutation and same-name imports cannot change snapshots`() {
        ImageAttachmentStore(parent).use { store ->
            val validated = image()
            val first = store.save(validated)
            val second = store.save(validated)
            first.bytes().fill(0)
            assertArrayEquals(validated.bytes(), first.bytes())
            first.close()
            assertArrayEquals(validated.bytes(), second.bytes())
            assertEquals(1, images(roots().single()).size)
        }
    }

    @Test
    fun `dispose invalidates surviving leases and tampered owned data is never sent`() {
        val store = ImageAttachmentStore(parent)
        val lease = store.save(image())
        val path = images(roots().single()).single()
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        Files.write(path, byteArrayOf(1, 2, 3))
        assertThrows(IllegalStateException::class.java) { lease.bytes() }
        store.close()
        assertThrows(IllegalStateException::class.java) { lease.bytes() }
        lease.close()
        assertTrue(roots().isEmpty())
    }

    @Test
    fun `recovery preserves a live owner and unknown data regardless of age`() {
        ImageAttachmentStore(parent).use { store ->
            val lease = store.save(image())
            val before = lease.bytes()
            assertFalse(ImageAttachmentStore.recoverStopped(parent).isEmpty())
            assertArrayEquals(before, lease.bytes())
        }
        val unknown = parent.resolve("cursor-agent-image-unknown")
        Files.createDirectory(unknown)
        Files.writeString(unknown.resolve("original.txt"), "do not delete")
        assertFalse(ImageAttachmentStore.recoverStopped(parent).isEmpty())
        assertTrue(Files.exists(unknown.resolve("original.txt")))
    }

    @Test
    fun `recovery reclaims only a stopped PID-start owner with recognized regular children`() {
        fun stopped(): Path {
            val owner = UUID.randomUUID().toString()
            val root = parent.resolve("cursor-agent-image-$owner")
            Files.createDirectory(root)
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
            // Same PID, different recorded start: this identifies a former owner, not this process.
            Files.writeString(root.resolve("owner.json"), Gson().toJson(mapOf("schema" to 1, "owner" to owner,
                "pid" to ProcessHandle.current().pid(), "started" to "1970-01-01T00:00:00Z")))
            Files.write(root.resolve("${UUID.randomUUID()}.png"), image().bytes())
            return root
        }
        val safe = stopped()
        assertTrue(ImageAttachmentStore.recoverStopped(parent).isEmpty())
        assertFalse(Files.exists(safe))
        val unknown = stopped()
        val original = parent.resolve("source.png")
        Files.write(original, image().bytes())
        Files.createSymbolicLink(unknown.resolve("${UUID.randomUUID()}.png"), original)
        assertFalse(ImageAttachmentStore.recoverStopped(parent).isEmpty())
        assertTrue(Files.exists(original))
        assertTrue(Files.exists(unknown.resolve("owner.json")))
    }
}
