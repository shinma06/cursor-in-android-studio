package com.cursoragent.ui.composer.image

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One project's owned snapshots. Call filesystem operations off the EDT. */
internal class ImageAttachmentStore(
    private val parent: Path = Path.of(System.getProperty("java.io.tmpdir")),
    private val onRetained: (String) -> Unit = {},
) : AutoCloseable {
    private val owner = UUID.randomUUID().toString()
    private val directory = parent.resolve(PREFIX + owner)
    private val images = mutableSetOf<Stored>()
    private var closed = false

    init {
        Files.createDirectory(directory)
        privatePermissions(directory, "rwx------")
        val process = ProcessHandle.current()
        val marker = mapOf("schema" to 1, "owner" to owner, "pid" to process.pid(),
            "started" to process.info().startInstant().map { it.toString() }.orElse("unknown"))
        Files.writeString(directory.resolve(MARKER), Gson().toJson(marker))
        privatePermissions(directory.resolve(MARKER), "rw-------")
    }

    @Synchronized
    fun save(image: ValidatedImage): ImageAttachment {
        check(!closed) { "添付先は閉じられています。" }
        val bytes = image.bytes()
        val path = directory.resolve("${UUID.randomUUID()}.png")
        try {
            Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW)
            privatePermissions(path, "r--------")
            val stored = Stored(path, digest(bytes), image.width, image.height)
            images.add(stored)
            return ImageAttachment(this, stored)
        } catch (error: Exception) {
            Files.deleteIfExists(path)
            throw error
        }
    }

    @Synchronized
    private fun retain(stored: Stored): ImageAttachment {
        check(!closed && stored in images) { "画像を再添付してください。" }
        stored.references++
        return ImageAttachment(this, stored)
    }

    @Synchronized
    private fun bytes(stored: Stored): ByteArray {
        check(!closed && stored in images) { "画像を再添付してください。" }
        require(Files.isRegularFile(stored.path, NOFOLLOW_LINKS)) { "添付画像を確認できません。再添付してください。" }
        val bytes = try {
            Files.newInputStream(stored.path, NOFOLLOW_LINKS).use { it.readNBytes(ImageInput.MAX_PNG_BYTES + 1) }
        } catch (_: Exception) { throw ImageInputException("添付画像を読み取れません。再添付してください。") }
        check(bytes.size <= ImageInput.MAX_PNG_BYTES && digest(bytes) == stored.digest) { "添付画像が変わっています。再添付してください。" }
        return bytes
    }

    @Synchronized
    private fun release(stored: Stored) {
        if (stored !in images) return
        stored.references--
        if (stored.references == 0) {
            images.remove(stored)
            Files.deleteIfExists(stored.path)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        for (stored in images) runCatching { Files.deleteIfExists(stored.path) }.onFailure {
            onRetained("解放できなかった添付一時データを保持しました。所有プロセス終了後に再確認します。")
        }
        images.clear()
        val remaining = Files.newDirectoryStream(directory).use { entries -> entries.map { it.fileName.toString() }.toSet() }
        if (remaining == setOf(MARKER)) {
            Files.deleteIfExists(directory.resolve(MARKER))
            Files.deleteIfExists(directory)
        } else {
            onRetained("所有を確認できないファイルがあるため、添付一時ディレクトリを保持しました。")
        }
    }

    internal class Stored(val path: Path, val digest: String, val width: Int, val height: Int) {
        var references = 1
    }

    /** Each draft, queue item and in-flight send owns its own idempotent lease. */
    class ImageAttachment internal constructor(private val store: ImageAttachmentStore, private val stored: Stored) : AutoCloseable {
        private val closed = AtomicBoolean()
        val width: Int get() = stored.width
        val height: Int get() = stored.height
        fun retain(): ImageAttachment {
            check(!closed.get()) { "画像を再添付してください。" }
            return store.retain(stored)
        }
        fun bytes(): ByteArray {
            check(!closed.get()) { "画像を再添付してください。" }
            return store.bytes(stored)
        }
        override fun close() { if (closed.compareAndSet(false, true)) store.release(stored) }
    }

    companion object {
        private const val PREFIX = "cursor-agent-image-"
        private const val MARKER = "owner.json"
        private val pngName = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png")
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun privatePermissions(path: Path, permissions: String) {
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
        }

        /** Age is irrelevant. Only an identified, stopped PID/start-time owner can be reclaimed. */
        fun recoverStopped(parent: Path = Path.of(System.getProperty("java.io.tmpdir"))): List<String> {
            val retained = mutableListOf<String>()
            Files.newDirectoryStream(parent, "$PREFIX*").use { entries ->
                for (directory in entries) {
                    try {
                        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
                        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                            require(Files.getPosixFilePermissions(directory).none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") })
                        }
                        val marker = directory.resolve(MARKER)
                        require(Files.isRegularFile(marker, NOFOLLOW_LINKS) && Files.size(marker) <= 1024)
                        val data = Gson().fromJson(Files.readString(marker), JsonObject::class.java)
                        require(data.keySet() == setOf("schema", "owner", "pid", "started"))
                        require(data.get("schema").isJsonPrimitive && data.get("schema").asJsonPrimitive.isNumber && data.get("schema").asString == "1")
                        require(data.get("pid").isJsonPrimitive && data.get("pid").asJsonPrimitive.isNumber)
                        require(listOf("owner", "started").all { data.get(it).isJsonPrimitive && data.get(it).asJsonPrimitive.isString })
                        val owner = UUID.fromString(data.get("owner").asString).toString()
                        require(directory.fileName.toString() == PREFIX + owner)
                        val started = data.get("started").asString
                        require(started != "unknown")
                        java.time.Instant.parse(started)
                        val pid = data.get("pid").asString.toLongOrNull()
                        require(pid != null && pid > 0)
                        val process = ProcessHandle.of(pid).orElse(null)
                        if (process != null && process.isAlive) {
                            val actualStart = process.info().startInstant().orElse(null)
                            if (actualStart == null || actualStart.toString() == started) {
                                retained.add("生存中または終了を確認できないownerの添付一時データを保持しました。")
                                continue
                            }
                        }
                        val files = Files.newDirectoryStream(directory).use { it.toList() }
                        require(files.all { Files.isRegularFile(it, NOFOLLOW_LINKS) && (it.fileName.toString() == MARKER || pngName.matches(it.fileName.toString())) })
                        for (file in files.filter { it.fileName.toString() != MARKER }) Files.deleteIfExists(file)
                        Files.deleteIfExists(marker)
                        Files.deleteIfExists(directory)
                    } catch (_: Exception) {
                        retained.add("所有または終了を確認できない添付一時データを保持しました。")
                    }
                }
            }
            return retained
        }
    }
}
