package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.toDto
import com.neovita.shared.network.dto.AssessmentRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.assessmentRoutes(repo: AssessmentRepository) {
    authenticate("jwt-auth") {
        post("/assessments") {
            val userId = call.principal<UserIdPrincipal>()!!.name
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
