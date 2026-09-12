package com.cursoragent.history

import com.cursoragent.service.AgentTransport
import com.cursoragent.settings.ChatHistoryRecord
import com.cursoragent.settings.ChatHistoryState
import com.cursoragent.settings.WorktreeMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConversationStoreTest {
    @TempDir lateinit var directory: Path

    @Test fun `stable IDs and ordered text survive restart with unfinished turns interrupted`() {
        val store = ConversationStore(directory)
        val recorder = ConversationRecorder(Conversation(transport = AgentTransport.ACP), store::save)
        val turn = newHistoryId()
        recorder.begin(turn, "元入力")
        recorder.assistant("同じ")
        recorder.assistant("同じ同じ")
        recorder.tool("tool-1", "ツール実行中")
        recorder.tool("tool-1", "ツール完了")
        recorder.newAssistant()
        recorder.assistant("続き")
        val expected = recorder.conversation
        val loaded = ConversationStore(directory).load().conversations.single()
        assertEquals(expected.id, loaded.id)
        assertEquals(turn, loaded.turns.single().id)
        assertEquals(expected.turns.single().messages, loaded.turns.single().messages)
        assertEquals(listOf("元入力", "同じ同じ", "ツール完了", "続き"), loaded.turns.single().messages.map { it.text })
        assertEquals("interrupted", loaded.turns.single().state)
        recorder.finish("completed")
        assertEquals("completed", store.load().conversations.single().turns.single().state)
    }

    @Test fun `corrupt unknown version and null fields do not lose healthy conversations`() {
        val store = ConversationStore(directory)
        val good = Conversation()
        store.save(good)
        Files.writeString(directory.resolve("broken.json"), "{")
        Files.writeString(directory.resolve("future.json"), "{\"version\":999}")
        Files.writeString(directory.resolve("null.json"), "{\"version\":1,\"id\":null}")
        val loaded = store.load()
        assertEquals(listOf(good), loaded.conversations)
        assertEquals(3, loaded.unreadable)
        assertEquals("{", Files.readString(directory.resolve("broken.json")))
    }

    @Test fun `failed atomic save preserves previous bytes and other conversations`() {
        val store = ConversationStore(directory)
        val original = Conversation()
        val other = Conversation()
        store.save(original)
        store.save(other)
        val path = directory.resolve("${original.id}.json")
        val bytes = Files.readAllBytes(path)
        assertThrows(IllegalArgumentException::class.java) {
            store.save(original.copy(turns = listOf(SavedTurn(messages = listOf(ChatMessage(role = "user", text = "a".repeat(ConversationStore.MAX_BYTES)))))))
        }
        assertArrayEquals(bytes, Files.readAllBytes(path))
        assertEquals(2, store.load().conversations.size)
        val blocked = directory.resolve("not-a-directory")
        Files.writeString(blocked, "keep")
        assertThrows(Exception::class.java) { ConversationStore(blocked).save(original) }
        assertEquals("keep", Files.readString(blocked))
    }

    @Test fun `existing corrupted or future file is not overwritten by a later snapshot`() {
        val store = ConversationStore(directory)
        val original = Conversation()
        store.save(original)
        val path = directory.resolve("${original.id}.json")
        for (bytes in listOf("{", "{\"version\":999}")) {
            Files.writeString(path, bytes)
            assertThrows(Exception::class.java) { store.save(original.copy(updatedMs = original.updatedMs + 1)) }
            assertEquals(bytes, Files.readString(path))
        }
    }

    @Test fun `count limit is explicit and delete frees only the selected file`() {
        val store = ConversationStore(directory)
        val records = List(ConversationStore.MAX_CONVERSATIONS) { Conversation() }
        records.forEach(store::save)
        val next = Conversation()
        assertThrows(IllegalArgumentException::class.java) { store.save(next) }
        store.delete(records.first().id)
        store.save(next)
        assertEquals(ConversationStore.MAX_CONVERSATIONS, store.load().conversations.size)
        assertFalse(Files.exists(directory.resolve("${records.first().id}.json")))
        assertThrows(IllegalArgumentException::class.java) { store.delete("../escape") }
    }

    @Test fun `legacy XML state remains metadata only and is never mistaken for ACP`() {
        val legacy = ChatHistoryState.State().apply { records.add(ChatHistoryRecord("provider-legacy", "preview", 123)) }
        val history = ChatHistoryState()
        history.loadState(legacy)
        assertEquals("provider-legacy", history.list().single().chatId)
        assertEquals(0, ConversationStore(directory).load().conversations.size)
        assertEquals(legacy, history.getState())
    }

    @Test fun `provider resume requires print and matching known workspace while display does not`() {
        val print = Conversation(providerId = "same-provider", root = "/project", worktreeMode = WorktreeMode.DEFAULT)
        assertTrue(print.canResume("/project", WorktreeMode.DEFAULT))
        assertFalse(print.copy(transport = AgentTransport.ACP).canResume("/project", WorktreeMode.DEFAULT))
        assertFalse(print.copy(root = null).canResume("/project", WorktreeMode.DEFAULT))
        assertFalse(print.canResume("/other", WorktreeMode.DEFAULT))
        assertFalse(print.canResume("/project", WorktreeMode.ISOLATED))
        val store = ConversationStore(directory)
        store.save(print.copy(transport = AgentTransport.ACP))
        assertEquals("same-provider", store.load().conversations.single().providerId)
    }
}
