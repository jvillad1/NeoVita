package com.neovita.server.routes

import com.neovita.server.config.AppRuntimeConfig
import com.neovita.shared.network.dto.MinVersions
import com.neovita.shared.network.dto.WebConfigResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.configRoutes(googleClientId: String?, appConfig: AppRuntimeConfig) {
    // Public config for clients (nothing here is a secret).
    get("/config") {
        call.respond(
            WebConfigResponse(
                googleClientId = googleClientId?.takeIf { it.isNotBlank() },
                features = appConfig.features,
                minVersion = MinVersions(
                    android = appConfig.minVersionAndroid,
                    ios = appConfig.minVersionIos
                ),
                maintenance = appConfig.maintenance,
                firebase = appConfig.firebase
            )
        )
    }
}
