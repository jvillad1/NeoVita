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

/**
 * Gate for the platform-wide content/screens backoffice — deliberately NOT the same check
 * as [requireRole]("EMPLOYER"). `EMPLOYER` only means "can see their own company's team" via
 * `company_id`; it used to also — accidentally — unlock editing the content feed and the
 * dashboard layout for every user of the app, regardless of which company granted it. Any
 * one company's team lead could silently rewrite what every other company's employees see.
 * `isContentAdmin` is its own flag so the two capabilities can be granted independently.
 */
suspend fun ApplicationCall.requireContentAdmin(users: UserRepository): Boolean {
    val userId = principal<UserIdPrincipal>()?.name
    val user = userId?.let { users.findById(it) }
    if (user?.isContentAdmin != true) {
        respond(
            HttpStatusCode.Forbidden,
            mapOf("code" to "FORBIDDEN", "message" to "Se requiere permiso de administrador de contenido")
        )
        return false
    }
    return true
}
