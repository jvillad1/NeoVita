package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.services.GoogleAuthService
import com.neovita.server.services.JwtService
import com.neovita.shared.network.dto.AuthResponse
import com.neovita.shared.network.dto.GoogleAuthRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(
    googleAuthService: GoogleAuthService,
    jwtService: JwtService,
    userRepository: UserRepository
) {
    post("/auth/google") {
        val request = call.receive<GoogleAuthRequest>()
        val googleUser = googleAuthService.verifyIdToken(request.idToken)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("code" to "AUTH_INVALID_TOKEN",
                      "message" to "El token de Google es inválido o ha expirado")
            )

        val isNew = userRepository.findByEmail(googleUser.email) == null
        val user = userRepository.upsert(googleUser.email, googleUser.name)
        val token = jwtService.generateToken(user.id, user.role)
        call.respond(AuthResponse(token = token, isNewUser = isNew))
    }
}
