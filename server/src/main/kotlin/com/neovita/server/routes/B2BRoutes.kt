package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.services.PillarScores
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.b2bRoutes(userRepository: UserRepository, assessmentRepo: AssessmentRepository) {
    authenticate("jwt-auth") {
        get("/b2b/team") {
            val principal = call.principal<UserIdPrincipal>()!!
            val user = userRepository.findById(principal.name)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            if (user.role != "EMPLOYER") return@get call.respond(
                HttpStatusCode.Forbidden,
                mapOf("code" to "FORBIDDEN", "message" to "Se requiere rol EMPLOYER")
            )
            val team = userRepository.findByCompany(user.companyId!!)
            val teamData = team.map { member ->
                val scores = assessmentRepo.findLatest(member.id)?.scores
                mapOf(
                    "userId" to member.id, "name" to member.name,
                    "email" to member.email, "scores" to scores
                )
            }
            val avgScore = teamData
                .mapNotNull { (it["scores"] as? PillarScores)?.overall }
                .average()
                .let { if (it.isNaN()) 0 else it.toInt() }
            call.respond(mapOf("team" to teamData, "avgScore" to avgScore))
        }
    }
}
