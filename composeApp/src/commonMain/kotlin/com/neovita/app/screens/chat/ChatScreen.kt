package com.neovita.app.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.neovita.app.ui.components.ChatBubble
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*
import com.neovita.shared.domain.model.MessageRole

class ChatScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<ChatViewModel>()
        val state by vm.state.collectAsState()
        val listState = rememberLazyListState()

        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty())
                listState.animateScrollToItem(state.messages.size - 1)
        }

        Column(Modifier.fillMaxSize().background(NeoBg)) {
            // Header
            Text(
                "Coach NeoVita",
                style = MaterialTheme.typography.titleLarge,
                color = NeoNavy, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            Divider()

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(state.messages) { msg ->
                    ChatBubble(
                        message = msg.content,
                        isUser = msg.role == MessageRole.USER
                    )
                }
                if (state.isStreaming) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(start = 8.dp),
                            color = NeoTeal700, strokeWidth = 2.dp
                        )
                    }
                }
            }

            state.error?.let { ErrorBanner(it) }

            // Suggestion chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf("Nutrición", "Ejercicio", "Sueño", "Estrés")
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { vm.sendMessage("Dime más sobre $suggestion") },
                        label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            // Input
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = vm::updateInput,
                    placeholder = { Text("Escribe tu pregunta...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.sendMessage(state.inputText) },
                    enabled = state.inputText.isNotBlank() && !state.isStreaming
                ) {
                    Text("➤", color = if (state.inputText.isNotBlank()) NeoTeal700 else Color.Gray)
                }
            }
        }
    }
}
