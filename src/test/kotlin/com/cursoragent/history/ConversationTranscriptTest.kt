package com.cursoragent.history

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConversationTranscriptTest {
    @TempDir lateinit var directory: Path

    @Test fun `export freezes ordered original markdown without provider paths or injected metadata`() {
        val recorder = ConversationRecorder(Conversation(providerId = "private-provider", root = "/private/workspace")) {}
        val prompt = "元の入力\n`code`😀"
        val answer = "**回答**\n```kotlin\nval x = 1\n```\n末尾"
        recorder.begin(newHistoryId(), prompt)
        recorder.assistant(answer)
        recorder.tool("wire-tool-id", "ファイル: 完了")
        val snapshot = recorder.conversation
        recorder.assistant("後続差替え")
        recorder.finish("completed")
        val markdown = conversationMarkdown(snapshot)
        assertTrue(markdown.contains(prompt))
        assertTrue(markdown.contains(answer))
        assertTrue(markdown.indexOf(prompt) < markdown.indexOf(answer))
        assertTrue(markdown.indexOf(answer) < markdown.indexOf("ファイル: 完了"))
        assertTrue(markdown.contains("実行中（この時点までの内容）"))
        listOf("private-provider", "/private/workspace", "wire-tool-id", "後続差替え").forEach { assertFalse(markdown.contains(it)) }
        val target = directory.resolve("会話.md")
        writeConversationMarkdown(snapshot, target)
        assertEquals(markdown, Files.readString(target))
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test fun `atomic failure preserves previous destination and cleans temporary files`() {
        val target = directory.resolve("existing.md")
        Files.createDirectory(target)
        val existing = target.resolve("keep")
        Files.writeString(existing, "original")
        assertThrows(Exception::class.java) { writeConversationMarkdown(Conversation(), target) }
        assertEquals("original", Files.readString(existing))
        assertEquals(1L, Files.list(directory).use { it.count() })
        val missing = directory.resolve("missing/file.md")
        assertThrows(Exception::class.java) { writeConversationMarkdown(Conversation(), missing) }
        assertFalse(Files.exists(missing))
    }

    @Test fun `loaded valid conversations export while corrupt metadata remains untouched`() {
        val store = ConversationStore(directory)
        val value = Conversation(turns = listOf(SavedTurn(state = "stopped", messages = listOf(ChatMessage(role = "user", text = "残る本文")))))
        store.save(value)
        val broken = directory.resolve("broken.json")
        Files.writeString(broken, "not-json")
        val loaded = store.load()
        assertEquals(1, loaded.unreadable)
        assertTrue(conversationMarkdown(loaded.conversations.single()).contains("残る本文"))
        assertEquals("not-json", Files.readString(broken))
    }
}
