package com.cursoragent.acp

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Bounded newline JSON-RPC for ACP v1. Run [read] off EDT; callbacks must not wait for RPC replies. */
internal class AcpJsonRpc(
    private val input: InputStream,
    private val output: OutputStream,
    private val onNotification: (String, JsonObject) -> Unit,
    private val onRequest: (Request, String, JsonObject) -> Unit,
    private val onClosed: (String) -> Unit,
    private val frameLimit: Int = MAX_FRAME_BYTES,
) : AutoCloseable {
    private val stopped = AtomicBoolean()
    private val sequence = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableFuture<JsonElement>>()
    private val requests = ConcurrentHashMap<String, Request>()
    private val writer = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32),
        { runnable -> Thread(runnable, "Cursor ACP writer").apply { isDaemon = true } },
    )

    val isClosed: Boolean get() = stopped.get()
    internal val pendingCount: Int get() = pending.size

    fun request(method: String, params: JsonObject, onDispatch: () -> Unit = {}, onResult: (JsonElement) -> Unit = {}): CompletableFuture<JsonElement> {
        val id = "client-${sequence.incrementAndGet()}"
        val result = CompletableFuture<JsonElement>()
        synchronized(pending) {
            if (isClosed || pending.size >= 32) {
                result.completeExceptionally(AcpException("ACP接続を利用できません"))
                return result
            }
            pending[id] = result
        }
        val response = result.thenApply { onResult(it); it }
        send(envelope(method, params).apply { addProperty("id", id) }, onDispatch)
        return response
    }

    fun notify(method: String, params: JsonObject) = send(envelope(method, params))

    /** Complete lines are decoded together, so a split multibyte character cannot be corrupted. */
    fun read() {
        val frame = ByteArrayOutputStream()
        val bytes = ByteArray(4096)
        try {
            while (!isClosed) {
                val count = input.read(bytes)
                if (count < 0) {
                    fail(if (frame.size() == 0) "ACP接続が終了しました" else "ACPの受信が途中で終了しました")
                    return
                }
                for (index in 0 until count) {
                    if (isClosed) return
                    val value = bytes[index].toInt() and 0xff
                    if (value == 10) {
                        if (frame.size() > 0) {
                            val text = StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(frame.toByteArray())).toString()
                            accept(text)
                            frame.reset()
                        }
                    } else {
                        if (frame.size() >= frameLimit) throw AcpException("ACPの受信上限を超えました")
                        frame.write(value)
                    }
                }
            }
        } catch (_: Exception) {
            fail("ACPの受信形式または接続に問題があります")
        }
    }

    private fun accept(text: String) {
        val parsed = JsonParser.parseString(text)
        require(parsed.isJsonObject)
        val message = parsed.asJsonObject
        require(message.string("jsonrpc") == "2.0")
        if (message.has("method")) {
            require(!message.has("result") && !message.has("error"))
            val method = requireNotNull(message.string("method"))
            val params = if (message.has("params")) message.getAsJsonObject("params") else JsonObject()
            if (message.has("id")) {
                val id = requireNotNull(message["id"].takeIf(::validId)).deepCopy()
                val key = idKey(id)
                val request = Request(id, key)
                require(requests.size < 32 && requests.putIfAbsent(key, request) == null)
                onRequest(request, method, params)
            } else {
                onNotification(method, params)
            }
            return
        }
        require(message.has("id") && validId(message["id"]))
        require(message.has("result") != message.has("error"))
        val id = message["id"]
        val error = if (message.has("error")) message.getAsJsonObject("error") else null
        if (error != null) {
            require(error["code"]?.isJsonPrimitive == true && error["code"].asJsonPrimitive.isNumber)
            require(error.string("message") != null)
        }
        // Outbound IDs are strings; unsolicited, late and duplicate responses cannot finish another request.
        val future = if (id.asJsonPrimitive.isString) pending.remove(id.asString) else null
        if (error != null) future?.completeExceptionally(AcpException("ACP要求に失敗しました（${error["code"].asInt}）"))
        else future?.complete(message["result"])
    }

    inner class Request internal constructor(private val id: JsonElement, private val key: String) {
        private val answered = AtomicBoolean()
        val isPending: Boolean get() = !answered.get() && !isClosed

        fun respond(result: JsonElement = JsonNull.INSTANCE): Boolean = reply("result", result)

        fun reject(code: Int = -32601, message: String = "Method not supported"): Boolean =
            reply("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })

        private fun reply(field: String, value: JsonElement): Boolean {
            if (!answered.compareAndSet(false, true)) return false
            requests.remove(key, this)
            return send(JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                add("id", id)
                add(field, value)
            })
        }
    }

    private fun send(message: JsonObject, onDispatch: () -> Unit = {}): Boolean {
        if (isClosed) return false
        val bytes = (message.toString() + "\n").toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > frameLimit) {
            fail("ACPへの送信上限を超えました")
            return false
        }
        return try {
            writer.execute {
                if (!isClosed) try {
                    // Local frame/queue rejection and cancellation before this point wrote no bytes.
                    // After dispatch begins, a write failure can be partial and must remain uncertain.
                    onDispatch()
                    output.write(bytes)
                    output.flush()
                } catch (_: Exception) {
                    fail("ACPへの送信に失敗しました")
                }
            }
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            fail("ACPの送信待ち上限を超えました")
            false
        }
    }

    fun fail(reason: String) {
        if (!stopped.compareAndSet(false, true)) return
        // Mark closed before releasing waiters, so their continuations cannot create fresh requests.
        val failure = AcpException(reason)
        val waiters = synchronized(pending) { pending.values.toList().also { pending.clear() } }
        requests.clear()
        writer.shutdownNow()
        waiters.forEach { it.completeExceptionally(failure) }
        onClosed(reason)
    }

    override fun close() = fail("ACP接続を閉じました")

    private fun envelope(method: String, params: JsonObject) = JsonObject().apply {
        addProperty("jsonrpc", "2.0")
        addProperty("method", method)
        add("params", params)
    }

    private fun validId(id: JsonElement): Boolean = id.isJsonPrimitive &&
        (id.asJsonPrimitive.isString || id.asJsonPrimitive.isNumber)

    private fun idKey(id: JsonElement): String = if (id.asJsonPrimitive.isString) "s:${id.asString}"
        else "n:${id.asBigDecimal.stripTrailingZeros().toString()}"

    companion object {
        // ponytail: text-only first connection; revisit with the image limit before enabling image input.
        const val MAX_FRAME_BYTES = 1024 * 1024
    }
}

internal class AcpException(message: String) : RuntimeException(message)

internal fun JsonObject.string(name: String): String? = get(name)?.takeIf {
    it.isJsonPrimitive && it.asJsonPrimitive.isString
}?.asString

internal fun jsonObject(vararg entries: Pair<String, String>) = JsonObject().apply {
    entries.forEach { (key, value) -> addProperty(key, value) }
}
