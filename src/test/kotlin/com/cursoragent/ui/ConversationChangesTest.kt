package com.cursoragent.ui

import com.cursoragent.parser.FileEditDetails
import com.cursoragent.parser.ToolCallPayloadParser
import com.cursoragent.service.AgentTool
import com.cursoragent.service.AgentToolContent
import com.cursoragent.service.FileRevertOperation
import com.cursoragent.service.RestorePolicy
import com.cursoragent.service.RestoreTarget
import com.cursoragent.settings.WorktreeMode
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConversationChangesTest {
    @TempDir lateinit var root: Path
    private fun target() = RestoreTarget.capture(root.toString(), WorktreeMode.DEFAULT)
    private fun edit(before: String?, after: String?, path: String = "a.txt") = FileEditDetails(path, 0, 0, null, before, after)

    @Test
    fun `duplicate calls preserve edit order and stable turns separate reused provider IDs`() {
        val changes = ConversationChanges("conversation")
        changes.beginTurn("first")
        changes.print("first", "one", edit("A", "B", "dir/../a.txt"), target())
        changes.print("first", "two", edit("B", "C", Path.of(target().rootPath!!).resolve("a.txt").toString()), target())
        changes.print("first", "one", edit("A", "B"), target())
        val first = changes.snapshot()
        changes.beginTurn("second")
        changes.print("second", "one", edit("C", "D"), target())
        val snapshot = changes.snapshot()
        assertEquals("conversation", snapshot.conversationId)
        assertEquals(listOf("first", "second"), snapshot.turns)
        val file = snapshot.files().single()
        assertEquals(3, file.edits.size)
        assertEquals("A", file.first.before)
        assertEquals("D", file.last.after)
        assertNull(file.revertRejection)
        assertEquals("C", snapshot.files("first").single().last.after)
        assertEquals("C", first.files().single().last.after)
        assertNotEquals(first, snapshot) // An open list cannot retain restore authority after updates.
    }

    @Test
    fun `missing payloads gaps unknown and isolated roots cannot authorize aggregate Revert`() {
        val changes = ConversationChanges("conversation")
        changes.print("one", "a", edit("A", "B"), target())
        changes.print("two", "b", edit("user edit", "C"), target())
        assertNotNull(changes.snapshot().files().single().revertRejection)
        for ((before, after) in listOf(null to "after", "before" to null)) {
            val missing = ConversationChanges("missing")
            missing.print("one", "a", edit(before, after), target())
            assertFalse(missing.snapshot().files().single().canShowDiff)
            assertNotNull(missing.snapshot().files().single().revertRejection)
        }
        for (unknown in listOf(RestoreTarget.UNKNOWN, target().copy(worktreeMode = WorktreeMode.ISOLATED))) {
            val isolated = ConversationChanges("isolated")
            isolated.print("one", "a", edit("A", "B"), unknown)
            assertNotNull(isolated.snapshot().files().single().revertRejection)
        }
    }

    @Test
    fun `ACP updates replace typed diff without creating writable or duplicate entries`() {
        val changes = ConversationChanges("acp")
        val tool = AgentTool("call", status = "pending", content = listOf(AgentToolContent.Diff("a.txt", null, "A")))
        changes.acp("one", tool, target())
        changes.acp("one", tool.copy(status = "completed", content = listOf(AgentToolContent.Diff("a.txt", null, "B"))), target())
        val file = changes.snapshot().files().single()
        assertEquals(1, file.edits.size)
        assertEquals("", file.first.before)
        assertEquals("B", file.last.after)
        assertEquals("completed", file.last.status)
        assertTrue(file.canShowDiff)
        assertNotNull(file.revertRejection)
        changes.print("two", "print", edit("B", "C"), target())
        assertEquals(2, changes.snapshot().files().size) // Transport provenance cannot be mixed.
        changes.acp("one", tool.copy(content = emptyList()), target())
        assertEquals(1, changes.snapshot().files().size)
    }

    @Test
    fun `different roots and empty scopes remain separate without consulting the filesystem`(@TempDir other: Path) {
        val changes = ConversationChanges("empty")
        changes.beginTurn("no-edits")
        assertTrue(changes.snapshot().files().isEmpty())
        changes.print("one", "same", edit("A", "B"), target())
        changes.print("two", "same", edit("B", "C"), RestoreTarget.capture(other.toString(), WorktreeMode.DEFAULT))
        assertEquals(2, changes.snapshot().files().size)
        assertTrue(changes.snapshot().files("no-edits").isEmpty())
        changes.print("two", "blank", edit("A", "B", " "), target())
        assertEquals(2, changes.snapshot().files().size)
        assertTrue(ConversationChanges("empty").snapshot().edits.isEmpty()) // Reopening saved text does not restore payloads.
    }

    @Test
    fun `stale dialog snapshot is refused before any IDE filesystem access`() {
        val project = java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(com.intellij.openapi.project.Project::class.java),
        ) { _, method, _ -> error("Stale state must stop before Project access: ${method.name}") } as com.intellij.openapi.project.Project
        val result = DiffViewerHelper.revertFileContentResult(project, "a.txt", "before", "after", target(), isCurrent = { false })
        assertFalse(result.restored)
        assertTrue(result.rejectionReason!!.contains("開き直して"))
    }

    @Test
    fun `real completed fixture reaches aggregate and existing disk restore refuses stale unsaved missing and moved roots`(@TempDir other: Path) {
        val line = javaClass.getResource("/stream-json-fixtures/02_edit_completed.jsonl")!!.readText().trim()
        val parsed = ToolCallPayloadParser.parse(JsonParser.parseString(line).asJsonObject)!!
        val changes = ConversationChanges("fixture")
        changes.print("one", parsed.callId, parsed.fileEdit!!.copy(path = "a.txt"), target())
        changes.print("two", "second", edit("world\n", ""), target())
        val file = changes.snapshot().files().single()
        assertEquals("hello\n", file.first.before)
        assertEquals("", file.last.after) // Empty after is valid content, not a missing payload.
        assertNull(file.revertRejection)
        val path = Files.writeString(root.resolve("a.txt"), "").toRealPath()
        var unsaved = false
        var writes = 0
        val access = object : FileRevertOperation.FileAccess {
            override val path = path
            override fun hasUnsavedChanges() = unsaved
            override fun read() = Files.readString(path)
            override fun write(content: String) { writes++; Files.writeString(path, content) }
        }
        fun restore(current: RestoreTarget = target()) = FileRevertOperation.restore(
            file.first.target, current, file.last.path, file.first.before!!, file.last.after!!, access,
        )
        unsaved = true
        assertEquals(RestorePolicy.STALE_EDIT, restore().rejectionReason)
        unsaved = false
        assertFalse(restore(RestoreTarget.capture(other.toString(), WorktreeMode.DEFAULT)).restored)
        assertEquals(0, writes)
        assertTrue(restore().restored)
        assertEquals("hello\n", Files.readString(path))
        assertEquals(RestorePolicy.STALE_EDIT, restore().rejectionReason)
        Files.delete(path)
        assertFalse(restore().restored) // Deleted files are never recreated from a historical record.
        assertFalse(Files.exists(path))
        assertEquals(1, writes)
    }
}
