package com.cursoragent.service

import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GitSnapshotStoreTest {
    @TempDir
    lateinit var repoDir: File

    private lateinit var sampleFile: File

    @BeforeEach
    fun setUp() {
        git("init", "-q")
        git("config", "user.email", "test@test.com")
        git("config", "user.name", "test")
        sampleFile = File(repoDir, "sample.txt")
        sampleFile.writeText("original content\n")
        git("add", "sample.txt")
        git("commit", "-q", "-m", "init")
    }

    @Test
    fun `recognizes a git repo and rejects a non-git directory`(@TempDir nonGitDir: File) {
        assertTrue(GitSnapshotStore(repoDir).isGitRepo())

        // nonGitDir is a separate @TempDir instance, not nested inside repoDir, so it
        // isn't picked up as part of repoDir's working tree by `git rev-parse`.
        assertFalse(GitSnapshotStore(nonGitDir).isGitRepo())
    }

    @Test
    fun `snapshot then restore reverts a modified tracked file`() {
        val store = GitSnapshotStore(repoDir)
        val sha = store.createSnapshot()
        assertNotNull(sha)

        sampleFile.writeText("modified by agent\n")
        assertEquals("modified by agent\n", sampleFile.readText())

        assertTrue(store.restore(sha!!, emptyList(), target(), target()).restored)
        assertEquals("original content\n", sampleFile.readText())
    }

    @Test
    fun `restore deletes files the agent created after the snapshot`() {
        val store = GitSnapshotStore(repoDir)
        val untrackedAtSnapshot = store.listUntrackedFiles()
        val sha = store.createSnapshot()!!

        val newFile = File(repoDir, "new_by_agent.txt")
        newFile.writeText("created after snapshot\n")
        assertTrue(newFile.exists())

        assertTrue(store.restore(sha, untrackedAtSnapshot, target(), target()).restored)
        assertFalse(newFile.exists())
    }

    @Test
    fun `restore does not delete a file that was already untracked at snapshot time`() {
        val preExisting = File(repoDir, "pre_existing.txt")
        preExisting.writeText("was here before the prompt\n")
        val store = GitSnapshotStore(repoDir)
        val untrackedAtSnapshot = store.listUntrackedFiles()
        assertTrue(untrackedAtSnapshot.contains("pre_existing.txt"))

        val sha = store.createSnapshot()!!
        assertTrue(store.restore(sha, untrackedAtSnapshot, target(), target()).restored)
        assertTrue(preExisting.exists())
    }

    @Test
    fun `snapshot and restore never touch git stash list or commit history`() {
        val store = GitSnapshotStore(repoDir)
        val logBefore = git("log", "--oneline").trim()

        val sha = store.createSnapshot()!!
        sampleFile.writeText("modified by agent\n")
        store.restore(sha, emptyList(), target(), target()).restored

        assertEquals("", git("stash", "list").trim())
        assertEquals(logBefore, git("log", "--oneline").trim())
    }

    @Test
    fun `isolated target cannot restore tracked or delete untracked files after switching back`() {
        val store = GitSnapshotStore(repoDir)
        val sha = store.createSnapshot()!!
        sampleFile.writeText("later content")
        val untracked = File(repoDir, "keep.txt").apply { writeText("keep") }
        val isolated = RestoreTarget.capture(repoDir.absolutePath, WorktreeMode.ISOLATED)
        assertEquals(RestorePolicy.ISOLATED, store.restore(sha, emptyList(), isolated, target()).rejectionReason)
        assertEquals("later content", sampleFile.readText())
        assertEquals("keep", untracked.readText())
    }

    @Test
    fun `old unknown targets refuse all writes`() {
        val store = GitSnapshotStore(repoDir)
        val sha = store.createSnapshot()!!
        sampleFile.writeText("later content")
        assertFalse(store.restore(sha, emptyList(), RestoreTarget.UNKNOWN, target()).restored)
        assertEquals("later content", sampleFile.readText())
    }

    @Test
    fun `same git objects in another worktree do not authorize restoring that worktree`(@TempDir other: File) {
        val store = GitSnapshotStore(repoDir)
        val sha = store.createSnapshot()!!
        val linked = File(other, "linked")
        git("worktree", "add", "--detach", linked.absolutePath, "HEAD")
        val linkedFile = File(linked, "sample.txt").apply { writeText("linked changes") }
        sampleFile.writeText("original root changes")
        val linkedTarget = RestoreTarget.capture(linked.absolutePath, WorktreeMode.DEFAULT)
        assertFalse(GitSnapshotStore(linked).restore(sha, emptyList(), target(), linkedTarget).restored)
        // Also reject a mismatched store even when the caller supplies identical old targets.
        assertFalse(GitSnapshotStore(linked).restore(sha, emptyList(), target(), target()).restored)
        assertEquals("linked changes", linkedFile.readText())
        assertEquals("original root changes", sampleFile.readText())
        assertTrue(GitSnapshotStore(linked).restore(sha, emptyList(), linkedTarget, linkedTarget).restored)
        assertEquals("original content\n", linkedFile.readText())
        assertEquals("original root changes", sampleFile.readText())
    }

    @Test
    fun `nested directory is not accepted as snapshot root`() {
        val child = File(repoDir, "child").apply { mkdir() }
        assertFalse(GitSnapshotStore(child).isGitRepo())
    }

    @Test
    fun `untracked names preserve whitespace unicode and newlines`() {
        val names = listOf(" leading.txt", "末尾 .txt", "line\nbreak.txt")
        names.forEach { File(repoDir, it).writeText("existing") }
        val store = GitSnapshotStore(repoDir)
        val before = store.listUntrackedFiles()
        assertEquals(names.toSet(), before.toSet())
        val sha = store.createSnapshot()!!
        val added = File(repoDir, "new\nfile.txt").apply { writeText("new") }
        assertTrue(store.restore(sha, before, target(), target()).restored)
        names.forEach { assertEquals("existing", File(repoDir, it).readText()) }
        assertFalse(added.exists())
    }

    @Test
    fun `untracked symlink cleanup never deletes its external target`(@TempDir other: File) {
        val store = GitSnapshotStore(repoDir)
        val sha = store.createSnapshot()!!
        val external = File(other, "external.txt").apply { writeText("keep") }
        val link = File(repoDir, "link.txt").toPath()
        java.nio.file.Files.createSymbolicLink(link, external.toPath())
        assertTrue(store.restore(sha, emptyList(), target(), target()).restored)
        assertFalse(java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals("keep", external.readText())
    }

    private fun target() = RestoreTarget.capture(repoDir.absolutePath, WorktreeMode.DEFAULT)

    private fun git(vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(repoDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }
}
