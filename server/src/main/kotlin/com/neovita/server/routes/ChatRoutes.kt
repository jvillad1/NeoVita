package com.neovita.server.routes

import com.neovita.server.services.ClaudeMessage
import com.neovita.server.services.ClaudeService
import com.neovita.shared.network.dto.ChatRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.chatRoutes(claudeService: ClaudeService) {
    authenticate("jwt-auth") {
        post("/chat") {
            val request = call.receive<ChatRequest>()
            val messages = request.messages.map { ClaudeMessage(it.role, it.content) }
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                claudeService.streamChat(messages).collect { chunk ->
                    write("data: $chunk\n\n")
                    flush()
                }
                write("data: [DONE]\n\n")
                flush()
            }
        }
    }
}
