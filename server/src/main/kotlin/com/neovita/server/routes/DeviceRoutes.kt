package com.neovita.server.routes

import com.neovita.server.db.repositories.DeviceTokenRepository
import com.neovita.shared.network.dto.RegisterDeviceRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.deviceRoutes(repo: DeviceTokenRepository) {
    authenticate("jwt-auth") {
        // The app re-registers on every activation/token rotation; upsert keeps one row per device.
        post("/devices/token") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<RegisterDeviceRequest>()
            if (req.token.isBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            repo.upsert(req.token.trim(), userId, req.platform.trim().lowercase())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
