package com.cursoragent.service

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.CheckpointRecord
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TurnWorkspaceTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `settings changed during preparation cannot change CLI checkpoint or card targets`() {
        for (mode in WorktreeMode.entries) {
            val settings = AgentSettingsState().apply { worktreeMode = mode }
            val turn = TurnWorkspace(root.toString(), settings.worktreeMode, null)
            settings.worktreeMode = if (mode == WorktreeMode.DEFAULT) WorktreeMode.ISOLATED else WorktreeMode.DEFAULT
            val record = CheckpointRecord(rootPath = turn.restoreTarget.rootPath, worktreeMode = turn.restoreTarget.worktreeMode?.name)
            val cardTarget = turn.restoreTarget
            assertEquals(mode, cardTarget.worktreeMode)
            assertEquals(record.restoreTarget(), cardTarget)
            assertEquals(mode == WorktreeMode.ISOLATED, "-w" in turn.arguments())
            assertEquals(cardTarget.rootPath, turn.arguments()[1])
            assertEquals(PermissionMode.ASK_EVERY_TIME, settings.permissionMode)
            assertFalse("--force" in turn.arguments())
            assertFalse("--auto-review" in turn.arguments())
        }
    }

    @Test
    fun `only a known matching default session allows restoration on resume`(@TempDir other: Path) {
        val known = RestoreTarget.capture(root.toString(), WorktreeMode.DEFAULT)
        val matching = TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, "chat-1", known)
        assertEquals(known, matching.restoreTarget)
        assertTrue(matching.arguments().takeLast(2) == listOf("--resume", "chat-1"))
        for (prior in listOf(null, RestoreTarget.UNKNOWN, known.copy(worktreeMode = WorktreeMode.ISOLATED), known.copy(rootPath = other.toString()))) {
            val resumed = TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, "chat-1", prior)
            assertEquals(RestoreTarget.UNKNOWN, resumed.restoreTarget)
            // Chat can continue, but an unverified target can never authorize a write.
            assertEquals(root.toRealPath().toString(), resumed.arguments()[1])
            assertNotNull(RestorePolicy.rejectionReason(resumed.restoreTarget, known))
        }
        assertEquals(known, TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, null).restoreTarget)
    }

    @Test
    fun `an isolated card remains isolated after a later default turn`() {
        val isolated = TurnWorkspace(root.toString(), WorktreeMode.ISOLATED, null)
        val card = isolated.restoreTarget
        val next = TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, "chat", card)
        assertEquals(WorktreeMode.ISOLATED, card.worktreeMode)
        assertEquals(RestoreTarget.UNKNOWN, next.restoreTarget)
        assertEquals(RestorePolicy.ISOLATED, RestorePolicy.rejectionReason(card, next.commandTarget))
    }

    @Test
    fun `missing root refuses command construction without inventing a path`() {
        val turn = TurnWorkspace(root.resolve("missing").toString(), WorktreeMode.DEFAULT, null)
        assertNull(turn.commandTarget.rootPath)
        assertThrows(IllegalArgumentException::class.java) { turn.arguments() }
    }

    @Test
    fun `real root used for command and restoration stays fixed after symlink retarget`(@TempDir other: Path) {
        val actual = Files.createDirectory(root.resolve("actual"))
        val link = Files.createSymbolicLink(root.resolve("workspace"), actual)
        val turn = TurnWorkspace(link.toString(), WorktreeMode.DEFAULT, null)
        val target = turn.restoreTarget
        Files.delete(link)
        Files.createSymbolicLink(link, other)
        assertEquals(actual.toRealPath().toString(), turn.arguments()[1])
        assertEquals(target, turn.restoreTarget)
    }
}
