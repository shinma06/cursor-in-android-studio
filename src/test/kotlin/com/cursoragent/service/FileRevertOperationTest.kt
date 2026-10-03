package com.cursoragent.service

import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileRevertOperationTest {
    @TempDir
    lateinit var root: Path

    private fun target(mode: WorktreeMode = WorktreeMode.DEFAULT) = RestoreTarget.capture(root.toString(), mode)

    private class DiskAccess(override val path: Path, var unsaved: Boolean = false) : FileRevertOperation.FileAccess {
        var writes = 0
        override fun hasUnsavedChanges() = unsaved
        override fun read(): String = Files.readString(path)
        override fun write(content: String) {
            writes++
            Files.writeString(path, content)
        }
    }

    @Test
    fun `normal Revert restores content but an old card preserves later saved edits`() {
        val access = DiskAccess(Files.writeString(root.resolve("sample.txt"), "after").toRealPath())
        assertTrue(FileRevertOperation.restore(target(), target(), "sample.txt", "before", "after", access).restored)
        assertEquals("before", Files.readString(access.path))
        Files.writeString(access.path, "later user edit")
        assertEquals(
            RestorePolicy.STALE_EDIT,
            FileRevertOperation.restore(target(), target(), "sample.txt", "before", "after", access).rejectionReason,
        )
        assertEquals("later user edit", Files.readString(access.path))
        assertEquals(1, access.writes)
    }

    @Test
    fun `unsaved editor changes prevent writing even when disk still matches the card`() {
        val access = DiskAccess(Files.writeString(root.resolve("sample.txt"), "after").toRealPath(), unsaved = true)
        assertEquals(
            RestorePolicy.STALE_EDIT,
            FileRevertOperation.restore(target(), target(), "sample.txt", "before", "after", access).rejectionReason,
        )
        assertEquals("after", Files.readString(access.path))
        assertEquals(0, access.writes)
    }

    @Test
    fun `old isolated card never writes after returning to default even with matching content`() {
        val access = DiskAccess(Files.writeString(root.resolve("sample.txt"), "after").toRealPath())
        assertFalse(FileRevertOperation.restore(target(WorktreeMode.ISOLATED), target(), "sample.txt", "before", "after", access).restored)
        assertFalse(FileRevertOperation.restore(RestoreTarget.UNKNOWN, target(), "sample.txt", "before", "after", access).restored)
        assertEquals("after", Files.readString(access.path))
        assertEquals(0, access.writes)
    }

    @Test
    fun `file resolved outside root or retargeted after lookup cannot write`(@TempDir other: Path) {
        val outside = DiskAccess(Files.writeString(other.resolve("sample.txt"), "after").toRealPath())
        assertFalse(FileRevertOperation.restore(target(), target(), outside.path.toString(), "before", "after", outside).restored)
        val original = DiskAccess(Files.writeString(root.resolve("sample.txt"), "after").toRealPath())
        val link = Files.createSymbolicLink(root.resolve("link.txt"), original.path)
        Files.delete(link)
        Files.createSymbolicLink(link, outside.path)
        assertFalse(FileRevertOperation.restore(target(), target(), "link.txt", "before", "after", original).restored)
        assertEquals("after", Files.readString(outside.path))
        assertEquals(0, outside.writes)
        assertEquals(0, original.writes)
    }
}
