package com.neovita.server.routes

import com.neovita.server.db.repositories.ContentRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireRole
import com.neovita.shared.network.dto.ContentRequest
import com.neovita.shared.network.dto.ContentTaxonomy
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
            if (!call.requireRole(userRepository, "EMPLOYER")) return@get
            call.respond(repo.listAll())
        }
        post("/content") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@post
            val req = call.receive<ContentRequest>()
            call.validate(req)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            call.respond(HttpStatusCode.Created, repo.create(req))
        }
        put("/content/{id}") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<ContentRequest>()
            call.validate(req)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }
            val updated = repo.update(id, req) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }
        delete("/content/{id}") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (repo.delete(id)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}

/** Returns an error body if category/type are outside the allowed taxonomy, else null. */
private fun ApplicationCall.validate(req: ContentRequest): Map<String, String>? = when {
    req.category !in ContentTaxonomy.CATEGORIES ->
        mapOf("code" to "INVALID_CATEGORY", "message" to "Categoría inválida: ${req.category}")
    req.type !in ContentTaxonomy.TYPES ->
        mapOf("code" to "INVALID_TYPE", "message" to "Tipo inválido: ${req.type}")
    else -> null
}
