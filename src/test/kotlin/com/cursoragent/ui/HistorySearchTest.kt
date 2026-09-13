package com.cursoragent.ui

import com.cursoragent.history.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HistorySearchTest {
    @Test fun `literal title and original body search preserve match identity and exclude tool context`() {
        val user = ChatMessage(role = "user", text = "最初の入力")
        val tool = ChatMessage(role = "tool", text = "ツールだけの検索語")
        val assistant = ChatMessage(role = "assistant", text = "Markdown **日本語😀**\nKotlin")
        val conversation = Conversation(turns = listOf(SavedTurn(messages = listOf(user, tool, assistant))))
        val entry = HistoryEntry(conversation, null, conversation.preview, 1)
        val found = searchHistory(listOf(entry), "日本語😀").single()
        assertEquals(assistant.id, found.match!!.messageId)
        assertTrue(found.match.excerpt.contains("日本語😀"))
        assertEquals(user.id, searchHistory(listOf(entry), "最初").single().match!!.messageId)
        assertEquals(assistant.id, searchHistory(listOf(entry), "kotlin").single().match!!.messageId)
        assertEquals(emptyList<HistoryHit>(), searchHistory(listOf(entry), "ツールだけ"))
        assertEquals(emptyList<HistoryHit>(), searchHistory(listOf(entry), ".*"))
    }

    @Test fun `empty none metadata-only and repeated titles remain distinct`() {
        val legacy = HistoryEntry(null, "old", "同じタイトル", 0)
        val saved = HistoryEntry(Conversation(turns = listOf(SavedTurn(messages = listOf(ChatMessage(role = "user", text = "同じタイトル"))))), null, "同じタイトル", 1)
        assertEquals(emptyList<HistoryHit>(), searchHistory(emptyList(), ""))
        val all = searchHistory(listOf(legacy, saved), "")
        assertEquals(listOf(legacy, saved), all.map { it.entry })
        assertTrue(all.all { it.match == null })
        assertNull(searchHistory(listOf(legacy), "同じ").single().match)
        assertEquals(emptyList<HistoryHit>(), searchHistory(listOf(legacy, saved), "未一致"))
        assertEquals(2, searchHistory(listOf(legacy, saved), "同じ").size)
    }
}
