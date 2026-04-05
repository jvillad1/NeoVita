package com.neovita.server.routes

import com.neovita.server.services.ClaudeMessage
import com.neovita.server.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.chatRoutes(claudeService: ClaudeService) {
    authenticate("jwt-auth") {
        post("/chat") {
            @Serializable data class ChatRequest(val messages: List<ClaudeMessage>)
            val request = call.receive<ChatRequest>()
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                claudeService.streamChat(request.messages).collect { chunk ->
                    write("data: $chunk\n\n")
                    flush()
                }
                write("data: [DONE]\n\n")
                flush()
            }
        }
    }
}
