package com.cursoragent.acp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.UUID

/** Opt-in, local-only correlation. Never routed to IDE logs, conversation bodies or provider wire. */
internal object AcpPrivateDiagnostics {
    const val DIRECTORY_PROPERTY = "cursor.agent.acp.privateDiagnosticsDir"
    private val directoryPermissions = PosixFilePermissions.fromString("rwx------")
    private val filePermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
    private var attempts = 0

    enum class Site { PROMPT_REQUEST, PROCESS_SAMPLE, PROMPT_RESPONSE, CHILD_EXIT, CONNECTION_CLOSED, CANCEL_TIMEOUT }
    enum class ResultStage { NOT_RECEIVED, STOP_REASON, TOOL_STATE, ACCEPTED }
    enum class Category { NONE, ACP, INTERRUPTED, CANCELLED, IO, SECURITY, INVALID_ARGUMENT, INVALID_STATE, OTHER }
    data class Failure(
        val site: Site,
        val category: Category,
        val resultStage: ResultStage,
        val terminal: Boolean,
        val children: List<AcpProcessTree.DiagnosticChild>?,
    )

    fun forConversation(tabId: String, conversationId: String?): ((Long, Instant?, UUID?, Failure?) -> Unit)? = runCatching {
        val directory = System.getProperty(DIRECTORY_PROPERTY)?.takeIf { it.isNotBlank() } ?: return null
        val tab = UUID.fromString(tabId)
        val conversation = UUID.fromString(conversationId ?: return null)
        val path = Path.of(directory)
        if (!path.isAbsolute) return null
        val callback: (Long, Instant?, UUID?, Failure?) -> Unit = { pid, started, trace, failure ->
            record(path, tab, conversation, pid, started, trace, failure)
        }
        callback
    }.getOrNull()

    // ponytail: at most 128 bounded records per IDE lifetime; restart a dedicated diagnostic IDE for more.
    @Synchronized
    private fun record(directory: Path, tab: UUID, conversation: UUID, pid: Long, started: Instant?, trace: UUID?, failure: Failure?) {
        if (attempts >= 128) return
        attempts++
        runCatching {
            val attributes = Files.readAttributes(directory, PosixFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!attributes.isDirectory || attributes.permissions() != directoryPermissions) return
            val record = JsonObject().apply {
                addProperty("at", Instant.now().toString())
                addProperty("event", if (failure != null) "first-failure" else if (trace == null) "process-started" else "prompt-dispatch")
                addProperty("tab", tab.toString())
                addProperty("conversation", conversation.toString())
                addProperty("pid", pid)
                addProperty("processStartedAt", started?.toString())
                addProperty("trace", trace?.toString())
                if (failure != null) {
                    addProperty("site", failure.site.name)
                    addProperty("category", failure.category.name)
                    addProperty("resultStage", failure.resultStage.name)
                    addProperty("terminal", failure.terminal)
                    add("children", failure.children?.let { children ->
                        JsonArray().apply {
                            children.forEach { child -> add(JsonObject().apply {
                                addProperty("pid", child.pid)
                                addProperty("processStartedAt", child.started.toString())
                                addProperty("alive", child.alive)
                                addProperty("parentPid", child.parentPid)
                                addProperty("parentStartedAt", child.parentStarted?.toString())
                            }) }
                        }
                    })
                }
            }
            val bytes = ByteBuffer.wrap((record.toString() + "\n").toByteArray(Charsets.UTF_8))
            // CREATE_NEW refuses existing files/symlinks; permissions apply atomically at creation.
            FileChannel.open(directory.resolve("acp-${UUID.randomUUID()}.json"), setOf(CREATE_NEW, WRITE), filePermissions).use {
                while (bytes.hasRemaining()) it.write(bytes)
            }
        } // Missing/unsupported permissions, disk errors, etc. cannot affect the ACP lifecycle.
    }
}
