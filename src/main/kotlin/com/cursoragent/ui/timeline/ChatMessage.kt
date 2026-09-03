package com.cursoragent.ui.timeline

sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Assistant(val text: String) : ChatMessage
    data class Status(val text: String) : ChatMessage
}
