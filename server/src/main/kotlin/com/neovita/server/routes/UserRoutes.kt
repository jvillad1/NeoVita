package com.neovita.server.routes

import com.neovita.server.db.Mappers
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.toDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.userRoutes(userRepository: UserRepository) {
    authenticate("jwt-auth") {
        get("/users/me") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val user = userRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user.toDto())
        }
        patch("/users/me") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            @Serializable data class PatchRequest(val name: String? = null, val age: Int? = null)
            val req = call.receive<PatchRequest>()
            val updated = userRepository.update(userId, req.name, req.age)
            call.respond(updated?.toDto() ?: HttpStatusCode.NotFound)
        }
    }
}
