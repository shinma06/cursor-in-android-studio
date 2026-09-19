package com.cursoragent.history

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** One atomic file per conversation. Unknown/corrupt files remain untouched for recovery. */
class ConversationStore(private val directory: Path) {
    data class Loaded(val conversations: List<Conversation>, val unreadable: Int)
    private val gson = Gson()

    fun load(): Loaded {
        if (!Files.exists(directory)) return Loaded(emptyList(), 0)
        val conversations = mutableListOf<Conversation>()
        var unreadable = 0
        Files.list(directory).use { paths -> paths.filter { it.fileName.toString().endsWith(".json") }.forEach { path ->
            try {
                require(conversations.size < MAX_CONVERSATIONS)
                val value = read(path)
                conversations.add(value.interrupted())
            } catch (_: Exception) { unreadable++ }
        } }
        return Loaded(conversations.sortedByDescending { it.updatedMs }, unreadable)
    }

    fun save(value: Conversation) {
        validate(value)
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "会話の保存上限（8 MiB）に達しました。新しい会話を開始してください。" }
        Files.createDirectories(directory)
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        }
        val target = path(value.id)
        require(!Files.isSymbolicLink(target))
        if (Files.exists(target)) require(read(target).id == value.id)
        if (!Files.exists(target)) {
            val count = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().endsWith(".json") }.count() }
            require(count < MAX_CONVERSATIONS) { "保存上限（100会話）に達しました。履歴から不要な会話を削除してください。" }
        }
        val temporary = Files.createTempFile(directory, ".conversation-", ".tmp")
        try {
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"))
            }
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            // Unsupported atomic replacement is a save failure; never truncate the previous file.
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun read(path: Path): Conversation {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) <= MAX_BYTES)
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES)
        val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(json["version"]?.toString() == "1")
        require(json["transport"]?.asString in setOf("PRINT", "ACP"))
        require(json.has("id") && json.has("updatedMs") && json["turns"]?.isJsonArray == true)
        val value = gson.fromJson(json, Conversation::class.java)
        validate(value)
        require(path.fileName.toString() == "${value.id}.json")
        return value
    }

    fun delete(id: String) { Files.deleteIfExists(path(id)) }
    private fun path(id: String): Path { require(UUID.fromString(id).toString() == id); return directory.resolve("$id.json") }
    private fun validate(value: Conversation) {
        require(value.version == 1 && UUID.fromString(value.id).toString() == value.id)
        require(value.updatedMs >= 0)
        require(value.providerId == null || value.providerId.isNotBlank())
        val ids = mutableSetOf<String>()
        require(value.turns.size <= 10000)
        value.turns.forEach { turn ->
            require(UUID.fromString(turn.id).toString() == turn.id && ids.add(turn.id))
            require(turn.state in setOf("running", "completed", "interrupted", "stopped", "failed", "refused", "token_limit", "request_limit", "cancelled"))
            turn.messages.forEach { message ->
                require(UUID.fromString(message.id).toString() == message.id && ids.add(message.id))
                require(message.role in setOf("user", "assistant", "tool", "error"))
                require(message.presentation == null || message.presentation == "acp_content" && message.role == "assistant")
                require(message.text.length <= MAX_BYTES)
            }
        }
    }
    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_CONVERSATIONS = 100
    }
}
