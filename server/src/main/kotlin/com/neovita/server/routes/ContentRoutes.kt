package com.neovita.server.routes

import com.neovita.server.db.repositories.ContentRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.shared.network.dto.ContentRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.contentRoutes(repo: ContentRepository, userRepository: UserRepository) {
    // Public: the dashboard feed (also visible to anonymous "Ahora no" users).
    get("/content") {
        call.respond(repo.listActive())
    }

    // Management endpoints — require a valid session AND the EMPLOYER (admin) role.
    authenticate("jwt-auth") {
        get("/content/all") {
            if (!call.requireEmployer(userRepository)) return@get
            call.respond(repo.listAll())
        }
        post("/content") {
            if (!call.requireEmployer(userRepository)) return@post
            val req = call.receive<ContentRequest>()
            call.respond(HttpStatusCode.Created, repo.create(req))
        }
        put("/content/{id}") {
            if (!call.requireEmployer(userRepository)) return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<ContentRequest>()
            val updated = repo.update(id, req) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
        delete("/content/{id}") {
            if (!call.requireEmployer(userRepository)) return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (repo.delete(id)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}

/** Responds 403 and returns false unless the caller is an EMPLOYER (content admin). */
private suspend fun ApplicationCall.requireEmployer(users: UserRepository): Boolean {
    val userId = principal<UserIdPrincipal>()?.name
    val user = userId?.let { users.findById(it) }
    if (user?.role != "EMPLOYER") {
        respond(HttpStatusCode.Forbidden, mapOf("code" to "FORBIDDEN", "message" to "Se requiere rol EMPLOYER"))
        return false
    }
    return true
}
