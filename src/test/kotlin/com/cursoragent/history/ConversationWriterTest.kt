package com.cursoragent.history

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ConversationWriterTest {
    @TempDir lateinit var directory: Path

    @Test fun `close drains latest terminal snapshot and delete prevents stale resurrection`() {
        val store = ConversationStore(directory)
        val writer = ConversationWriter(store)
        val id = newHistoryId()
        val running = Conversation(turns = listOf(SavedTurn(id)))
        writer.save(running) {}
        val terminal = running.copy(turns = listOf(SavedTurn(id, "stopped")))
        val saved = CompletableFuture<Boolean>()
        writer.save(terminal, saved::complete)
        val loaded = CompletableFuture<Result<ConversationStore.Loaded>>()
        writer.load(loaded::complete)
        assertEquals("stopped", loaded.get(5, TimeUnit.SECONDS).getOrThrow().conversations.single().turns.single().state)
        assertTrue(saved.get(5, TimeUnit.SECONDS))
        val deleted = CompletableFuture<Boolean>()
        writer.delete(running.id, deleted::complete)
        val stale = CompletableFuture<Boolean>()
        writer.save(running, stale::complete)
        assertFalse(stale.get(5, TimeUnit.SECONDS))
        assertTrue(deleted.get(5, TimeUnit.SECONDS))
        writer.dispose()
        assertTrue(store.load().conversations.isEmpty())
    }

    @Test fun `disposal flushes pending data and failed writes report failure without changing prior bytes`() {
        val store = ConversationStore(directory)
        val writer = ConversationWriter(store)
        val value = Conversation()
        val saved = CompletableFuture<Boolean>()
        writer.save(value, saved::complete)
        writer.dispose()
        assertTrue(saved.get(5, TimeUnit.SECONDS))
        val path = directory.resolve("${value.id}.json")
        Files.writeString(path, "corrupt fixture")
        val next = ConversationWriter(store)
        val failed = CompletableFuture<Boolean>()
        next.save(value, failed::complete)
        next.dispose()
        assertFalse(failed.get(5, TimeUnit.SECONDS))
        assertEquals("corrupt fixture", Files.readString(path))
    }
}
