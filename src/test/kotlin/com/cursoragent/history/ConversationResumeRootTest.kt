package com.cursoragent.history

import com.cursoragent.service.AgentTransport
import com.cursoragent.service.TurnWorkspace
import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConversationResumeRootTest {
    @TempDir lateinit var directory: Path

    @Test fun `project opened through a symlink resumes only its original real directory`() {
        val root = Files.createDirectory(directory.resolve("real-project")).toRealPath()
        val link = directory.resolve("project-link")
        Files.createSymbolicLink(link, root)
        val saved = Conversation(providerId = "saved-provider", root = root.toString(), worktreeMode = WorktreeMode.DEFAULT)
        val workspace = TurnWorkspace(link.toString(), WorktreeMode.DEFAULT, saved.providerId)
        assertTrue(saved.canResume(link.toString(), WorktreeMode.DEFAULT))
        assertTrue(saved.canResume(workspace.commandTarget.rootPath, workspace.mode))
        assertEquals(root.toString(), workspace.arguments()[1])
        // A target captured before replacement must be rechecked immediately before dispatch too.
        Files.delete(root)
        val other = Files.createDirectory(directory.resolve("redirected")).toRealPath()
        Files.createSymbolicLink(root, other)
        assertFalse(saved.canResume(workspace.commandTarget.rootPath, workspace.mode))
    }

    @Test fun `saved provider must not resume after root is redirected`() {
        val root = Files.createDirectory(directory.resolve("project")).toRealPath()
        val other = Files.createDirectory(directory.resolve("other")).toRealPath()
        val saved = Conversation(transport = AgentTransport.PRINT, providerId = "saved-provider", root = root.toString(), worktreeMode = WorktreeMode.DEFAULT)
        assertTrue(saved.canResume(root.toString(), WorktreeMode.DEFAULT))
        Files.delete(root)
        Files.createSymbolicLink(root, other)
        val workspace = TurnWorkspace(root.toString(), WorktreeMode.DEFAULT, saved.providerId)
        assertEquals(listOf("--workspace", other.toString(), "--resume", "saved-provider"), workspace.arguments())
        assertFalse(saved.canResume(root.toString(), WorktreeMode.DEFAULT), "The UI gate accepts saved-provider although actual --workspace changed")
    }
}
