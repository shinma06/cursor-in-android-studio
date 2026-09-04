package com.cursoragent.ui.composer.mention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BranchDiffBuilderTest {
    @Test
    fun `resolveBaseBranch prefers origin HEAD`() {
        val workspace = File("/tmp/project")
        val base = BranchDiffBuilder.resolveBaseBranch(workspace) { _, args ->
            when (args.toList()) {
                listOf("symbolic-ref", "refs/remotes/origin/HEAD") -> "ref: refs/remotes/origin/develop"
                else -> null
            }
        }
        assertEquals("develop", base)
    }

    @Test
    fun `resolveBaseBranch falls back to main`() {
        val workspace = File("/tmp/project")
        val base = BranchDiffBuilder.resolveBaseBranch(workspace) { _, args ->
            when (args.toList()) {
                listOf("symbolic-ref", "refs/remotes/origin/HEAD") -> null
                listOf("rev-parse", "--verify", "main") -> "abc123"
                else -> null
            }
        }
        assertEquals("main", base)
    }

    @Test
    fun `buildBlock wraps git diff output`() {
        val workspace = File("/tmp/project")
        val block = BranchDiffBuilder.buildBlock(workspace) { _, args ->
            when (args.toList()) {
                listOf("rev-parse", "--abbrev-ref", "HEAD") -> "feature/foo"
                listOf("symbolic-ref", "refs/remotes/origin/HEAD") -> "ref: refs/remotes/origin/main"
                listOf("diff", "main...HEAD") -> "+added line"
                else -> null
            }
        }

        assertTrue(block!!.contains("@branch (feature/foo vs main):"))
        assertTrue(block.contains("+added line"))
    }
}
