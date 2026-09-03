package com.cursoragent.service

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

        assertTrue(store.restore(sha!!, emptyList()))
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

        assertTrue(store.restore(sha, untrackedAtSnapshot))
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
        assertTrue(store.restore(sha, untrackedAtSnapshot))
        assertTrue(preExisting.exists())
    }

    @Test
    fun `snapshot and restore never touch git stash list or commit history`() {
        val store = GitSnapshotStore(repoDir)
        val logBefore = git("log", "--oneline").trim()

        val sha = store.createSnapshot()!!
        sampleFile.writeText("modified by agent\n")
        store.restore(sha, emptyList())

        assertEquals("", git("stash", "list").trim())
        assertEquals(logBefore, git("log", "--oneline").trim())
    }

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
