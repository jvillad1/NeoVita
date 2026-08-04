package com.neovita.server.routes

import com.neovita.server.db.repositories.ScreenRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireRole
import com.neovita.shared.network.dto.ScreenUpdateRequest
import com.neovita.shared.network.dto.validateScreenSections
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.screenRoutes(repo: ScreenRepository, userRepository: UserRepository) {
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

        // Administración de pantallas (EMPLOYER). El editor web vive en /web/admin/screens.
        get("/screens") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@get
            call.respond(repo.listAll())
        }
        put("/screens/{slug}") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@put
            val slug = call.parameters["slug"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            // El slug entra en varchar(64) y crea filas nuevas: acotarlo evita un 500 por
            // desbordar la columna y basura arbitraria en la tabla.
            if (!slug.matches(Regex("^[a-z0-9-]{1,64}$"))) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("code" to "INVALID_SCREEN", "message" to "Slug inválido: sólo minúsculas, números y guiones (máx. 64)")
                )
            }
            val body = call.receive<ScreenUpdateRequest>()
            // El servidor es la frontera de confianza: el editor valida por comodidad,
            // pero lo que decide es esto.
            validateScreenSections(body.sections)?.let { reason ->
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("code" to "INVALID_SCREEN", "message" to reason)
                )
            }
            // If-Match lleva la versión que el editor tenía cargada; si no viene, se fuerza.
            val expected = call.request.headers[HttpHeaders.IfMatch]?.toIntOrNull()
            val saved = repo.save(slug, body.sections, expected)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("code" to "SCREEN_CONFLICT",
                          "message" to "Otra persona guardó esta pantalla mientras editabas. Recarga y vuelve a aplicar tus cambios.")
                )
            call.respond(saved)
        }
    }
}
