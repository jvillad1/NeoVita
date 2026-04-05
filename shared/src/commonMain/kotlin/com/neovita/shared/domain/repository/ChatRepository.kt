package com.neovita.shared.domain.repository

import com.neovita.shared.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun sendMessage(history: List<ChatMessage>): Flow<String>
}
