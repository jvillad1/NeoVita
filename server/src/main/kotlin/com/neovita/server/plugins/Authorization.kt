package com.neovita.server.plugins

import com.neovita.server.db.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/**
 * Authorization gate: responds 403 and returns false unless the authenticated caller
 * has [role] (looked up from the DB, so role changes take effect immediately rather
 * than waiting for token expiry). Shared by every role-restricted route.
 */
suspend fun ApplicationCall.requireRole(users: UserRepository, role: String): Boolean {
    val userId = principal<UserIdPrincipal>()?.name
    val user = userId?.let { users.findById(it) }
    if (user?.role != role) {
        respond(HttpStatusCode.Forbidden, mapOf("code" to "FORBIDDEN", "message" to "Se requiere rol $role"))
        return false
    }
    return true
}
