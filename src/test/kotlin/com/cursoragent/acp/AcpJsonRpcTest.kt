package com.cursoragent.acp

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AcpJsonRpcTest {
    @Test
    fun `split UTF8 and multiple messages preserve order including notification without a response`() {
        val received = mutableListOf<String>()
        val frames = """{"jsonrpc":"2.0","method":"update","params":{"text":"日本語"}}
{"jsonrpc":"2.0","method":"update","params":{"text":"日本語"}}
""".toByteArray()
        val input = object : ByteArrayInputStream(frames) {
            override fun read(bytes: ByteArray, offset: Int, length: Int) = super.read(bytes, offset, minOf(1, length))
        }
        val output = ByteArrayOutputStream()
        val rpc = AcpJsonRpc(input, output, { _, p -> received += p.string("text")!! }, { _, _, _ -> fail<Unit>() }, {})
        rpc.read()
        assertEquals(listOf("日本語", "日本語"), received)
        assertEquals(0, output.size())
        assertTrue(rpc.isClosed)
    }

    @Test
    fun `numeric zero and string request IDs reply once and null result completes client future`() {
        val input = PipedInputStream()
        val server = PipedOutputStream(input)
        val output = ByteArrayOutputStream()
        val replies = CountDownLatch(2)
        val closed = CountDownLatch(1)
        val rpc = AcpJsonRpc(input, output, { _, _ -> }, { request, _, _ ->
            assertTrue(request.respond(JsonNull.INSTANCE))
            assertFalse(request.reject())
            replies.countDown()
        }, { closed.countDown() })
        val reader = thread { rpc.read() }
        try {
            val future = rpc.request("initialize", JsonObject())
            server.write(("""{"jsonrpc":"2.0","id":0,"method":"permission","params":{}}
{"jsonrpc":"2.0","id":"question","method":"question","params":{}}
{"jsonrpc":"2.0","id":"client-1","result":null}
""").toByteArray())
            server.flush()
            assertEquals(JsonNull.INSTANCE, future.get(5, TimeUnit.SECONDS))
            assertTrue(replies.await(5, TimeUnit.SECONDS))
            // A flush barrier: a response to a later outbound request proves prior writes reached the server.
            val barrier = rpc.request("barrier", JsonObject())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!output.toString().contains("barrier") && System.nanoTime() < deadline) Thread.yield()
            assertTrue(output.toString().contains("barrier"))
            val messages = output.toString().lineSequence().filter(String::isNotBlank).map { JsonParser.parseString(it).asJsonObject }.toList()
            val responses = messages.filter { it.has("result") }
            assertEquals(listOf("0", "\"question\""), responses.map { it["id"].toString() })
            server.write("{\"jsonrpc\":\"2.0\",\"id\":\"client-2\",\"error\":{\"code\":-1,\"message\":\"private server text\"}}\n".toByteArray())
            server.flush()
            val error = assertThrows(java.util.concurrent.ExecutionException::class.java) { barrier.get(5, TimeUnit.SECONDS) }
            assertFalse(error.cause!!.message!!.contains("private"))
            assertEquals(0, rpc.pendingCount)
        } finally {
            server.close()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            reader.join(5000)
            input.close()
        }
    }

    @Test
    fun `huge exponent request ID remains bounded and replies with the original ID`() {
        val input = PipedInputStream()
        val server = PipedOutputStream(input)
        val output = ByteArrayOutputStream()
        val received = CountDownLatch(1)
        val rpc = AcpJsonRpc(input, output, { _, _ -> }, { request, _, _ ->
            request.reject()
            received.countDown()
        }, {})
        val reader = thread { rpc.read() }
        try {
            server.write("{\"jsonrpc\":\"2.0\",\"id\":1e9999,\"method\":\"unknown\"}\n".toByteArray())
            server.flush()
            assertTrue(received.await(5, TimeUnit.SECONDS))
            rpc.request("barrier", JsonObject())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!output.toString().contains("barrier") && System.nanoTime() < deadline) Thread.yield()
            val first = JsonParser.parseString(output.toString().lineSequence().first()).asJsonObject
            assertEquals("1e9999", first["id"].toString())
            assertTrue(output.size() < 500)
        } finally {
            server.close()
            reader.join(5000)
            rpc.close()
            input.close()
        }
    }

    @Test
    fun `malformed oversized truncated and invalid UTF8 frames fail and release pending exactly once`() {
        val invalid = listOf(
            "not json\n".toByteArray(),
            "{\"jsonrpc\":\"2.0\",\"id\":1e100000000,\"method\":\"unknown\"}\n".toByteArray(),
            "{\"jsonrpc\":\"2.0\",\"id\":true,\"result\":null}\n".toByteArray(),
            "{\"jsonrpc\":\"2.0\",\"id\":\"client-1\",\"result\":null,\"error\":{}}\n".toByteArray(),
            "x".repeat(200).toByteArray(),
            "{\"jsonrpc\":\"2.0\"}".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28, 10),
        )
        invalid.forEach { bytes ->
            var closures = 0
            val rpc = AcpJsonRpc(ByteArrayInputStream(bytes), ByteArrayOutputStream(), { _, _ -> }, { _, _, _ -> }, { closures++ }, 128)
            val future = rpc.request("pending", JsonObject())
            rpc.read()
            rpc.close()
            assertTrue(future.isCompletedExceptionally)
            assertEquals(0, rpc.pendingCount)
            assertEquals(1, closures)
        }
    }
}
