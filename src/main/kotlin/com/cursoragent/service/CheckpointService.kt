package com.cursoragent.service

import com.cursoragent.settings.AgentSettingsState
import com.cursoragent.settings.CheckpointHistoryState
import com.cursoragent.settings.CheckpointRecord
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class CheckpointService(private val project: Project) {
    private val LOG = logger<CheckpointService>()

    private fun currentTarget(): RestoreTarget =
        RestoreTarget.capture(project.basePath, AgentSettingsState.getInstance().worktreeMode)

    fun unavailableReason(target: RestoreTarget): String? = RestorePolicy.rejectionReason(target, currentTarget())

    fun isAvailable(): Boolean {
        val target = currentTarget()
        return unavailableReason(target) == null && GitSnapshotStore(File(target.rootPath!!)).isGitRepo()
    }

    /** Call off EDT with the same immutable target used to build the CLI command. */
    fun createSnapshot(
        prompt: String,
        chatId: String?,
        target: RestoreTarget = RestoreTarget.UNKNOWN,
    ): String? {
        if (unavailableReason(target) != null) return null
        val store = GitSnapshotStore(File(target.rootPath!!)).takeIf { it.isGitRepo() } ?: return null
        val sha = store.createSnapshot()
        if (sha == null) {
            LOG.warn("Could not resolve a checkpoint SHA for this prompt (no commits yet?)")
            return null
        }

        val id = UUID.randomUUID().toString()
        CheckpointHistoryState.getInstance(project).addRecord(
            CheckpointRecord(
                id = id,
                promptPreview = prompt.take(120),
                timestampMs = System.currentTimeMillis(),
                gitSha = sha,
                chatId = chatId,
                untrackedFilesAtSnapshot = store.listUntrackedFiles().toMutableList(),
                rootPath = target.rootPath,
                worktreeMode = target.worktreeMode?.name,
            ),
        )
        return id
    }

    fun restore(id: String): Boolean = restoreResult(id).restored

    /** Call off EDT. Old records remain visible but are never assigned an inferred restore root. */
    fun restoreResult(id: String): RestoreResult {
        val record = CheckpointHistoryState.getInstance(project).findRecord(id)
            ?: return RestoreResult(RestorePolicy.UNKNOWN_TARGET)
        val target = record.restoreTarget()
        unavailableReason(target)?.let { return RestoreResult(it) }
        val store = GitSnapshotStore(File(target.rootPath!!))
        return store.restore(record.gitSha, record.untrackedFilesAtSnapshot, target, currentTarget())
    }

    fun pruneExpired(retentionDays: Int = DEFAULT_RETENTION_DAYS) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        CheckpointHistoryState.getInstance(project).pruneOlderThan(cutoff)
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 15
    }
}
