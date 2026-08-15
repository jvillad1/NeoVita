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

/**
 * Empaqueta [chunk] como un evento SSE. Cada línea lleva su propio prefijo `data:` porque
 * una línea sin él no forma parte del evento y el cliente la descarta: los deltas de Claude
 * traen saltos de línea (listas, párrafos, títulos), así que `data: $chunk` a secas perdía
 * texto y pegaba los fragmentos supervivientes.
 */
internal fun sseFrame(chunk: String): String =
    chunk.split("\r\n", "\n").joinToString("\n") { "data: $it" } + "\n\n"

fun Route.chatRoutes(claudeService: ClaudeService) {
    authenticate("jwt-auth") {
        post("/chat") {
            val request = call.receive<ChatRequest>()
            val messages = request.messages.map { ClaudeMessage(it.role, it.content) }
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                claudeService.streamChat(messages).collect { chunk ->
                    write(sseFrame(chunk))
                    flush()
                }
                write(sseFrame("[DONE]"))
                flush()
            }
        }
    }
}
