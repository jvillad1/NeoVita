package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class ChatMessageDto(val role: String, val content: String)
@Serializable data class ChatRequest(val messages: List<ChatMessageDto>)
