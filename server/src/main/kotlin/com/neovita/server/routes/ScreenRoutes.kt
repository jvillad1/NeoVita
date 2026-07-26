package com.neovita.server.routes

import com.neovita.server.db.repositories.ScreenRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.screenRoutes(repo: ScreenRepository) {
    authenticate("jwt-auth") {
        get("/screens/{slug}") {
            val slug = call.parameters["slug"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val screen = repo.getActive(slug) ?: return@get call.respond(HttpStatusCode.NotFound)

            val ifNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]
            if (ifNoneMatch == screen.version.toString()) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }

            call.respond(screen)
        }
    }
}
