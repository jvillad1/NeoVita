package com.neovita.shared.data.repository

import com.neovita.shared.domain.model.ChatMessage
import com.neovita.shared.domain.model.MessageRole
import com.neovita.shared.domain.repository.ChatRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.ChatMessageDto
import kotlinx.coroutines.flow.Flow

class ChatRepositoryImpl(private val apiService: ApiService) : ChatRepository {
    override fun sendMessage(history: List<ChatMessage>): Flow<String> {
        val dtos = history.map {
            ChatMessageDto(
                role = if (it.role == MessageRole.USER) "user" else "assistant",
                content = it.content
            )
        }
        return apiService.streamChat(dtos)
    }
}
