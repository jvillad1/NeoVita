package com.neovita.app.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*
import com.neovita.shared.domain.model.MessageRole

class ChatScreen : Screen {
    @Composable
    override fun Content() {
        val vm: ChatViewModel = koinInject()
        val state by vm.state.collectAsState()
        val listState = rememberLazyListState()

        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty())
                listState.animateScrollToItem(state.messages.size - 1)
        }

        Column(
            Modifier.fillMaxSize().background(NeoDarkBg)
        ) {
            // Header
            Surface(color = NeoDarkSurface, shadowElevation = 0.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium,
                        color = NeoTextPrimary, fontWeight = FontWeight.Light)
                    Spacer(Modifier.weight(1f))
                    Text("NeoVita AI Coach", style = MaterialTheme.typography.titleMedium,
                        color = NeoTextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(NeoCrimson.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Text("👤", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(state.messages) { msg ->
                    val isUser = msg.role == MessageRole.USER
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (isUser) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                                    bottomStart = 18.dp, bottomEnd = 4.dp),
                                color = NeoCrimson,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(msg.content,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                                    bottomStart = 4.dp, bottomEnd = 18.dp),
                                color = NeoDarkSurface2,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(msg.content,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = NeoTextPrimary)
                            }
                        }
                    }
                }
                if (state.isStreaming) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Surface(shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                                bottomStart = 4.dp, bottomEnd = 18.dp), color = NeoDarkSurface2) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                        color = NeoCrimson, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }

            if (state.error != null) ErrorBanner(state.error!!)

            // Suggestion chips
            LazyRow(
                Modifier.background(NeoDarkBg),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf("¿Qué ceno hoy?", "Rutina de 10 min", "Consejos de sueño", "Recetas fáciles")
                items(suggestions) { suggestion ->
                    Surface(
                        onClick = { vm.sendMessage(suggestion) },
                        shape = RoundedCornerShape(20.dp),
                        color = NeoCrimson.copy(alpha = 0.18f)
                    ) {
                        Text(suggestion,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoCrimsonLight, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Input row
            Surface(color = NeoDarkSurface, shadowElevation = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = vm::updateInput,
                        placeholder = {
                            Text("Escribe tu mensaje...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoTextSecondary)
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoCrimson,
                            unfocusedBorderColor = NeoDarkSurface2,
                            focusedTextColor = NeoTextPrimary,
                            unfocusedTextColor = NeoTextPrimary,
                            cursorColor = NeoCrimson,
                            focusedContainerColor = NeoDarkSurface2,
                            unfocusedContainerColor = NeoDarkSurface2
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    val enabled = state.inputText.isNotBlank() && !state.isStreaming
                    Surface(
                        onClick = { if (enabled) vm.sendMessage(state.inputText) },
                        shape = CircleShape,
                        color = if (enabled) NeoCrimson else NeoCrimson.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("➤", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
