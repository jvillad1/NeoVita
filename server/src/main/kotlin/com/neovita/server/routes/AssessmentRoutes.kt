package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.toDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.assessmentRoutes(repo: AssessmentRepository) {
    authenticate("jwt-auth") {
        post("/assessments") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            @Serializable data class AssessmentRequest(
                val exerciseFrequency: String, val exerciseType: String,
                val sleepHours: String, val sleepQuality: Int, val mainGoal: String
            )
            val req = call.receive<AssessmentRequest>()
            val entity = repo.save(
                userId, req.exerciseFrequency, req.exerciseType,
                req.sleepHours, req.sleepQuality, req.mainGoal
            )
            call.respond(HttpStatusCode.Created, entity.toDto())
        }
        get("/assessments/latest") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val entity = repo.findLatest(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(entity.toDto())
        }
    }
}
