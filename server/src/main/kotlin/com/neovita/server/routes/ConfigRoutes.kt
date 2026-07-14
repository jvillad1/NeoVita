package com.neovita.server.routes

import com.neovita.shared.network.dto.WebConfigResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.configRoutes(googleClientId: String?) {
    // Public config for the web client (the OAuth client ID is not a secret).
    get("/config") {
        call.respond(WebConfigResponse(googleClientId = googleClientId?.takeIf { it.isNotBlank() }))
    }
}
