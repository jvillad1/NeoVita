package com.neovita.server.routes

import com.neovita.server.db.repositories.DeviceTokenRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireRole
import com.neovita.server.services.PushService
import com.neovita.shared.network.dto.PushSendRequest
import com.neovita.shared.network.dto.PushSendResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pushRoutes(pushService: PushService, deviceRepo: DeviceTokenRepository, userRepo: UserRepository) {
    authenticate("jwt-auth") {
        // Test/ops sends only (EMPLOYER). Product-triggered pushes come later server-side.
        post("/push/test") {
            if (!call.requireRole(userRepo, "EMPLOYER")) return@post
            if (!pushService.enabled) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("code" to "PUSH_DISABLED",
                          "message" to "Configura FIREBASE_SERVICE_ACCOUNT para habilitar el envío")
                )
            }
            val req = call.receive<PushSendRequest>()
            if (req.title.isBlank() || req.body.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest)
            }
            val tokens = req.userId?.let { deviceRepo.tokensForUser(it) } ?: deviceRepo.allTokens()
            call.respond(PushSendResponse(sent = pushService.send(tokens, req.title, req.body, req.target)))
        }
    }
}
