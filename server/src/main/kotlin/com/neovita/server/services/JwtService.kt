package com.neovita.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

data class JwtPrincipal(val userId: String, val role: String)

class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expirationMs: Long
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: String, role: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationMs))
            .sign(algorithm)

    fun verify(token: String): JwtPrincipal? = runCatching {
        val decoded = JWT.require(algorithm).withIssuer(issuer).build().verify(token)
        JwtPrincipal(
            userId = decoded.getClaim("userId").asString(),
            role = decoded.getClaim("role").asString()
        )
    }.getOrNull()
}
