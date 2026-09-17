package com.neovita.server.routes

import com.neovita.server.db.repositories.EventRepository
import com.neovita.shared.network.dto.LogEventRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eventRoutes(repo: EventRepository) {
    authenticate("jwt-auth") {
        post("/events") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<LogEventRequest>()
            if (req.type.isBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            repo.log(userId, req.type.trim())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
