package com.neovita.server.plugins

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.ContentRepository
import com.neovita.server.db.repositories.PlanRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.routes.*
import com.neovita.server.services.ClaudeService
import com.neovita.server.services.GoogleAuthService
import com.neovita.server.services.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    googleAuthService: GoogleAuthService,
    jwtService: JwtService,
    userRepo: UserRepository,
    assessmentRepo: AssessmentRepository,
    planRepo: PlanRepository,
    claudeService: ClaudeService,
    contentRepo: ContentRepository
) {
    routing {
        get("/health") { call.respondText("OK") }

        // All REST endpoints are namespaced under /api so the wasmJs web app can be
        // served same-origin from "/" without path collisions.
        route("/api") {
            authRoutes(googleAuthService, jwtService, userRepo)
            userRoutes(userRepo)
            assessmentRoutes(assessmentRepo)
            planRoutes(claudeService, assessmentRepo, planRepo)
            chatRoutes(claudeService)
            b2bRoutes(userRepo, assessmentRepo)
            contentRoutes(contentRepo, userRepo)
        }

        // Serve the wasmJs web bundle (copied into resources/static by the Docker build).
        // Declared last so /health and /api take priority. Ktor 3.x doesn't register
        // the application/wasm content type by default.
        staticResources("/", "static") {
            default("index.html")
            contentType { url ->
                if (url.path.endsWith(".wasm")) ContentType("application", "wasm") else null
            }
        }
    }
}
