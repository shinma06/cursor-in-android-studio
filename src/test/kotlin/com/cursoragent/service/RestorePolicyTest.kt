package com.cursoragent.service

import com.cursoragent.settings.CheckpointHistoryState
import com.cursoragent.settings.CheckpointRecord
import com.cursoragent.settings.WorktreeMode
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RestorePolicyTest {
    @TempDir
    lateinit var root: Path

    private fun target(mode: WorktreeMode = WorktreeMode.DEFAULT) = RestoreTarget.capture(root.toString(), mode)

    @Test
    fun `default allows the same real root and isolated rejects in either direction`() {
        val normal = target()
        val isolated = target(WorktreeMode.ISOLATED)
        assertNull(RestorePolicy.rejectionReason(normal, normal))
        for ((created, current) in listOf(normal to isolated, isolated to normal, isolated to isolated)) {
            assertEquals(RestorePolicy.ISOLATED, RestorePolicy.rejectionReason(created, current))
        }
        // Switching away and back cannot turn an isolated card into a default card.
        assertEquals(RestorePolicy.ISOLATED, RestorePolicy.rejectionReason(isolated, target()))
    }

    @Test
    fun `legacy history loads without assuming a mode or root`() {
        val history = CheckpointHistoryState()
        history.loadState(CheckpointHistoryState.State().apply {
            records.add(CheckpointRecord(id = "legacy", gitSha = "abc"))
            records.add(CheckpointRecord(id = "unknown-mode", rootPath = root.toString(), worktreeMode = "FUTURE"))
        })
        for (record in history.state.records) {
            assertEquals(RestorePolicy.UNKNOWN_TARGET, RestorePolicy.rejectionReason(record.restoreTarget(), target()))
        }
        assertEquals(2, history.recordsForChat(null).size)
    }

    @Test
    fun `old persisted XML stays unknown and new XML round trips provenance`() {
        val legacy = XmlSerializer.deserialize(
            JDOMUtil.load("""<CheckpointRecord><option name="id" value="old"/><option name="gitSha" value="abc"/></CheckpointRecord>"""),
            CheckpointRecord::class.java,
        )
        assertEquals(RestoreTarget.UNKNOWN, legacy.restoreTarget())
        val original = CheckpointRecord(id = "new", rootPath = target().rootPath, worktreeMode = "DEFAULT")
        val reloaded = XmlSerializer.deserialize(XmlSerializer.serialize(original), CheckpointRecord::class.java)
        assertEquals(target(), reloaded.restoreTarget())
    }

    @Test
    fun `new history retains creation root and mode across load`() {
        val normal = target()
        val history = CheckpointHistoryState()
        history.loadState(CheckpointHistoryState.State().apply {
            records.add(CheckpointRecord(id = "new", rootPath = normal.rootPath, worktreeMode = "DEFAULT"))
        })
        assertEquals(normal, history.findRecord("new")!!.restoreTarget())
    }

    @Test
    fun `unknown missing relative and changed roots fail closed`(@TempDir other: Path) {
        val normal = target()
        assertNotNull(RestorePolicy.rejectionReason(RestoreTarget.UNKNOWN, normal))
        assertNotNull(RestorePolicy.rejectionReason(normal, RestoreTarget.UNKNOWN))
        assertNotNull(RestorePolicy.rejectionReason(normal, RestoreTarget.capture("relative", WorktreeMode.DEFAULT)))
        assertNotNull(RestorePolicy.rejectionReason(normal, RestoreTarget.capture(null, WorktreeMode.DEFAULT)))
        assertEquals(RestorePolicy.CHANGED_TARGET, RestorePolicy.rejectionReason(normal, RestoreTarget.capture(other.toString(), WorktreeMode.DEFAULT)))
    }

    @Test
    fun `relative and absolute file paths resolve within recorded root only`(@TempDir other: Path) {
        val file = Files.writeString(root.resolve("sample.txt"), "content").toRealPath()
        val outside = Files.writeString(other.resolve("sample.txt"), "content")
        assertEquals(file, RestorePolicy.resolveFile(target(), target(), "sample.txt"))
        assertEquals(file, RestorePolicy.resolveFile(target(), target(), file.toString()))
        assertNull(RestorePolicy.resolveFile(target(), target(), outside.toString()))
        assertNull(RestorePolicy.resolveFile(target(), target(), root.relativize(outside).toString()))
        assertNull(RestorePolicy.resolveFile(target(), target(), "missing.txt"))
        assertNull(RestorePolicy.resolveFile(target(), target(), ""))
        assertNull(RestorePolicy.resolveFile(target(), target(), "\u0000"))
        val metadata = Files.createDirectory(root.resolve(".git")).resolve("config")
        Files.writeString(metadata, "git metadata")
        assertNull(RestorePolicy.resolveFile(target(), target(), ".git/config"))
    }

    @Test
    fun `symlink path cannot escape root and replaced root is rejected`(@TempDir other: Path) {
        val outside = Files.writeString(other.resolve("outside.txt"), "keep")
        Files.createSymbolicLink(root.resolve("escape"), outside)
        assertNull(RestorePolicy.resolveFile(target(), target(), "escape"))
        val original = Files.createDirectory(root.resolve("original"))
        val captured = RestoreTarget.capture(original.toString(), WorktreeMode.DEFAULT)
        Files.delete(original)
        Files.createSymbolicLink(original, other)
        assertEquals(RestorePolicy.MISSING_ROOT, RestorePolicy.rejectionReason(captured, captured))
        assertEquals("keep", Files.readString(outside))
    }
}
