package com.cursoragent.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded, coalesced snapshots; disk I/O never runs on EDT. No raw exception text reaches UI/logs. */
@Service(Service.Level.PROJECT)
class ConversationHistory(project: Project) : Disposable {
    private val writer = ConversationWriter(ConversationStore(Path.of(PathManager.getConfigPath(), "cursor-agent-conversations", project.locationHash)))
    fun save(value: Conversation, completion: (Boolean) -> Unit) = writer.save(value, completion)
    fun load(completion: (Result<ConversationStore.Loaded>) -> Unit) = writer.load(completion)
    fun delete(id: String, completion: (Boolean) -> Unit) = writer.delete(id, completion)
    fun isDeleted(id: String): Boolean = writer.isDeleted(id)
    override fun dispose() = writer.dispose()
}

/** Serial disk owner, separated from IDE service construction for real failure/race tests. */
internal class ConversationWriter(private val store: ConversationStore) : Disposable {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "Cursor conversation storage").apply { isDaemon = true } }
    private val pending = linkedMapOf<String, Pair<Conversation, (Boolean) -> Unit>>()
    private var scheduled = false
    private var closed = false
    private val deleted = mutableSetOf<String>()

    @Synchronized fun isDeleted(id: String): Boolean = id in deleted

    @Synchronized
    fun save(value: Conversation, completion: (Boolean) -> Unit) {
        if (closed || value.id in deleted) { completion(false); return }
        pending[value.id] = value to completion
        if (!scheduled) {
            scheduled = true
            executor.schedule(::flush, 200, TimeUnit.MILLISECONDS)
        }
    }

    private fun flush() {
        val batch = synchronized(this) { pending.values.toList().also { pending.clear(); scheduled = false } }
        batch.forEach { (value, completion) ->
            completion(!isDeleted(value.id) && runCatching { store.save(value) }.isSuccess)
        }
    }

    @Synchronized
    fun load(completion: (Result<ConversationStore.Loaded>) -> Unit) {
        if (closed) { completion(Result.failure(IllegalStateException("closed"))); return }
        executor.execute { flush(); completion(runCatching { store.load() }) }
    }

    @Synchronized
    fun delete(id: String, completion: (Boolean) -> Unit) {
        if (closed || !deleted.add(id)) { completion(false); return }
        pending.remove(id)
        executor.execute {
            val success = runCatching { store.delete(id) }.isSuccess
            if (!success) synchronized(this) { deleted.remove(id) }
            completion(success)
        }
    }

    override fun dispose() {
        synchronized(this) {
            if (closed) return
            closed = true
            executor.execute(::flush)
            executor.shutdown()
        }
        // The IDE normally persists terminal turns before disposal; bounded final drain preserves close/Stop.
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
