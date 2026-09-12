package com.cursoragent.history

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** A single literal match in original user/assistant text, never injected context or tool payloads. */
data class ConversationMatch(val messageId: String, val excerpt: String)

fun findConversationText(conversation: Conversation, query: String): ConversationMatch? {
    for (turn in conversation.turns) for (message in turn.messages) {
        if (Thread.currentThread().isInterrupted) return null
        if (message.role != "user" && message.role != "assistant") continue
        val offset = message.text.indexOf(query, ignoreCase = true)
        if (offset >= 0) {
            var start = (offset - 40).coerceAtLeast(0)
            var end = (offset + query.length + 100).coerceAtMost(message.text.length)
            if (start > 0 && message.text[start].isLowSurrogate()) start--
            if (end < message.text.length && message.text[end].isLowSurrogate()) end++
            return ConversationMatch(message.id, message.text.substring(start, end).replace('\n', ' '))
        }
    }
    return null
}

/** Uses the selected immutable snapshot. No provider IDs, paths, environment or unsent draft. */
fun conversationMarkdown(conversation: Conversation): String = buildString {
    append("# 会話の記録\n\n")
    append("選択時点の元入力・応答とツール状態です。注入context・未送信の下書きは含みません。\n")
    conversation.turns.forEachIndexed { index, turn ->
        append("\n## ターン ${index + 1}\n\n")
        append("状態: ").append(when (turn.state) {
            "completed" -> "完了"
            "running" -> "実行中（この時点までの内容）"
            "stopped", "cancelled" -> "停止"
            "failed" -> "失敗"
            "refused" -> "拒否"
            "interrupted" -> "中断"
            else -> "上限到達"
        }).append('\n')
        turn.messages.forEach { message ->
            val label = when (message.role) {
                "user" -> "User"
                "assistant" -> "Assistant"
                "tool" -> "ツール状態"
                "error" -> "エラー"
                else -> return@forEach
            }
            append("\n### $label\n\n").append(message.text).append('\n')
        }
    }
}

/** A failed write must leave the user's existing destination intact. Call off EDT. */
fun writeConversationMarkdown(conversation: Conversation, destination: Path) {
    val target = destination.toAbsolutePath()
    require(!Files.isSymbolicLink(target))
    val temporary = Files.createTempFile(target.parent, ".transcript-", ".tmp")
    try {
        Files.writeString(temporary, conversationMarkdown(conversation), Charsets.UTF_8)
        Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
