package com.neovita.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuthentication(secret: String, issuer: String) {
    val algorithm = Algorithm.HMAC256(secret)
    install(Authentication) {
        jwt("jwt-auth") {
            verifier(JWT.require(algorithm).withIssuer(issuer).build())
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId != null) UserIdPrincipal(userId) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("code" to "AUTH_EXPIRED", "message" to "Token inválido o expirado")
                )
            }
        }
    }
}
