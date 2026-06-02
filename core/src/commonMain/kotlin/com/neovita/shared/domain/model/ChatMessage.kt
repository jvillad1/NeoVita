package com.neovita.shared.domain.model

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String, val role: MessageRole,
    val content: String, val timestamp: Long
)
