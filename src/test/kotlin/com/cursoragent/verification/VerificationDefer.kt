package com.cursoragent.verification

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Test source only. The separate #313 build copies this file into its own main sources. */
object VerificationDefer {
    private val identities = WeakHashMap<Any, String>()
    private val control: DeferControl? by lazy {
        System.getProperty("cursor.verification.directory")?.let { directory ->
            DeferControl(Path.of(directory)).also { value ->
                ApplicationManager.getApplication()?.let { Disposer.register(it, Disposable { value.close() }) }
                value.start()
            }
        }
    }

    @Synchronized
    fun owner(project: Any, field: Any): String {
        fun id(value: Any) = identities.getOrPut(value) { UUID.randomUUID().toString() }
        return "${id(project)}/${id(field)}".also { control?.event("owner", "identity", it, "none") }
    }

    fun edt(point: String, owner: String, token: String, block: () -> Unit) {
        check(SwingUtilities.isEventDispatchThread())
        if (control?.hold(point, owner, token, { SwingUtilities.invokeLater(block) }, {}) == null) block()
    }

    fun fingerprint(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun awaitPreparation(owner: String, token: String) { control?.awaitPreparation(owner, token) }
    fun event(event: String, point: String, owner: String, token: String, result: String = "") {
        control?.event(event, point, owner, token, result)
    }
}

/** One trial, one callback. All callbacks enter the original guards after an explicit release. */
class DeferControl(private val directory: Path, private val timeoutMillis: Long = 60_000) : AutoCloseable {
    private val gson = Gson()
    private val run: String
    private var commandId = ""
    private var armed: Triple<String, String, String>? = null
    private var pending: Pending? = null
    private var closed = false
    private var events = 0
    private var poller: java.util.concurrent.ScheduledExecutorService? = null
    private data class Pending(val id: String, val point: String, val owner: String, val token: String,
        val deadline: Long, val release: () -> Unit, val abort: () -> Unit)

    init {
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        require(Files.getPosixFilePermissions(directory).none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") })
        val manifest = read("manifest.json")
        run = UUID.fromString(manifest.get("run").asString).toString()
        require(timeoutMillis in 1..60_000)
    }

    @Synchronized
    fun start() {
        check(poller == null && !closed)
        poller = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "verification-313-control").apply { isDaemon = true } }
        poller!!.scheduleWithFixedDelay({
            try { poll() } catch (_: Exception) { close() }
        }, 0, 100, TimeUnit.MILLISECONDS)
    }

    private fun read(name: String): JsonObject {
        val path = directory.resolve(name)
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) <= 4096)
        return gson.fromJson(Files.readString(path), JsonObject::class.java)
    }

    @Synchronized
    fun poll() {
        if (closed) return
        pending?.takeIf { System.nanoTime() >= it.deadline }?.let { abort("timeout") }
        if (!Files.exists(directory.resolve("command.json"), NOFOLLOW_LINKS)) return
        val command = read("command.json")
        require(command.get("run").asString == run)
        val id = UUID.fromString(command.get("id").asString).toString()
        if (id == commandId) return
        commandId = id
        val result = try {
            when (command.get("op").asString) {
                "arm" -> {
                    check(armed == null && pending == null)
                    val point = command.get("point").asString
                    require(point in setOf("queue", "preparation", "popup", "edt-chunk", "edt-stop", "edt-complete", "edt-update"))
                    val owner = command.get("owner").asString
                    val token = command.get("token").asString
                    require(owner.matches(Regex("[a-f0-9-]{36}/[a-f0-9-]{36}")))
                    require(token == "next" || token.matches(Regex("[a-zA-Z0-9:/_-]{1,200}")))
                    armed = Triple(point, owner, token)
                    event("armed", point, owner, token)
                }
                "release" -> {
                    val item = pending
                    check(item != null && command.get("pending").asString == item.id)
                    pending = null
                    event("released", item.point, item.owner, item.token, item.id)
                    item.release()
                }
                "close" -> close()
                else -> error("unknown operation")
            }
            "accepted"
        } catch (_: IllegalArgumentException) { "rejected" } catch (_: IllegalStateException) { "rejected" }
        writeState(id, result)
    }

    @Synchronized
    fun hold(point: String, owner: String, token: String, release: () -> Unit, abort: () -> Unit): String? {
        if (closed) return null
        val expected = armed ?: return null
        if (expected.first != point || expected.second != owner || (expected.third != "next" && expected.third != token)) return null
        armed = null
        val id = UUID.randomUUID().toString()
        pending = Pending(id, point, owner, token, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis), release, abort)
        event("reached", point, owner, token, id)
        writeState(commandId, "reached")
        return id
    }

    fun awaitPreparation(owner: String, token: String) {
        check(!SwingUtilities.isEventDispatchThread())
        val latch = CountDownLatch(1)
        val aborted = java.util.concurrent.atomic.AtomicBoolean()
        val heldId = hold("preparation", owner, token, { latch.countDown() }, { aborted.set(true); latch.countDown() }) ?: return
        try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                synchronized(this) { abort("timeout", heldId) }
            }
            check(!aborted.get()) { "Verification trial aborted" }
        } catch (error: InterruptedException) {
            synchronized(this) { abort("interrupted", heldId) }
            Thread.currentThread().interrupt()
            throw error
        }
    }

    @Synchronized
    fun event(event: String, point: String, owner: String, token: String, result: String = "") {
        if (closed) return
        check(++events <= 10_000) { "Verification event limit" }
        val row = mapOf("run" to run, "nanos" to System.nanoTime(), "event" to event,
            "point" to point, "owner" to owner, "token" to token, "result" to result)
        val path = directory.resolve("events.jsonl")
        require(!Files.isSymbolicLink(path))
        Files.writeString(path, gson.toJson(row) + "\n", CREATE, APPEND)
    }

    private fun writeState(id: String, result: String) {
        val value = mapOf("run" to run, "command" to id, "result" to result, "closed" to closed,
            "pending" to pending?.id, "point" to pending?.point, "owner" to pending?.owner, "token" to pending?.token)
        val temporary = directory.resolve("state.tmp")
        require(!Files.isSymbolicLink(temporary))
        Files.writeString(temporary, gson.toJson(value) + "\n")
        Files.move(temporary, directory.resolve("state.json"), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    private fun abort(reason: String, id: String? = null) {
        val item = pending ?: return
        if (id != null && item.id != id) return
        pending = null
        try { event("aborted", item.point, item.owner, item.token, reason) } finally {
            try { writeState(commandId, reason) } finally { item.abort() }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        try { abort("closed") } finally {
            armed = null
            closed = true
            poller?.shutdown()
            poller = null
            writeState(commandId, "closed")
        }
    }
}
