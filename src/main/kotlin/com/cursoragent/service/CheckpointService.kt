package com.cursoragent.service

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

    private val store: GitSnapshotStore?
        get() = project.basePath?.let { GitSnapshotStore(File(it)) }

    fun isAvailable(): Boolean = store?.isGitRepo() == true

    fun createSnapshot(prompt: String, chatId: String?): String? {
        val store = store?.takeIf { it.isGitRepo() } ?: return null
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
            ),
        )
        return id
    }

    fun restore(id: String): Boolean {
        val record = CheckpointHistoryState.getInstance(project).findRecord(id) ?: return false
        val store = store ?: return false
        return store.restore(record.gitSha, record.untrackedFilesAtSnapshot)
    }

    fun pruneExpired(retentionDays: Int = DEFAULT_RETENTION_DAYS) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        CheckpointHistoryState.getInstance(project).pruneOlderThan(cutoff)
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 15
    }
}
