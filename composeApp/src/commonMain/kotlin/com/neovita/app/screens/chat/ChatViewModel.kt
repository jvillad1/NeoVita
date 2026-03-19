package com.neovita.app.screens.chat

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.neovita.shared.domain.model.ChatMessage
import com.neovita.shared.domain.model.MessageRole
import com.neovita.shared.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null
)

class ChatViewModel(private val chatRepo: ChatRepository) : ScreenModel {
    private val _state = MutableStateFlow(
        ChatState(
            messages = listOf(
                ChatMessage(
                    "init", MessageRole.ASSISTANT,
                    "¡Hola! Soy tu coach de longevidad NeoVita. ¿En qué puedo ayudarte hoy?",
                    System.currentTimeMillis()
                )
            )
        )
    )
    val state = _state.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return
        val userMsg = ChatMessage(uuid(), MessageRole.USER, text, System.currentTimeMillis())
        val assistantMsg = ChatMessage(uuid(), MessageRole.ASSISTANT, "", System.currentTimeMillis())
        _state.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                inputText = "", isStreaming = true, error = null
            )
        }
        var accumulated = ""
        screenModelScope.launch {
            chatRepo.sendMessage(_state.value.messages.dropLast(1))
                .catch {
                    _state.update { s ->
                        s.copy(isStreaming = false, error = "Coach no disponible, intenta más tarde")
                    }
                }
                .collect { chunk ->
                    accumulated += chunk
                    _state.update { s ->
                        val updated = s.messages.dropLast(1) + assistantMsg.copy(content = accumulated)
                        s.copy(messages = updated)
                    }
                }
            _state.update { it.copy(isStreaming = false) }
        }
    }

    fun updateInput(text: String) = _state.update { it.copy(inputText = text) }

    private fun uuid() = "msg-${System.currentTimeMillis()}-${(0..9999).random()}"
}
