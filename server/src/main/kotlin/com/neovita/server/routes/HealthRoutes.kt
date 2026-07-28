package com.neovita.server.routes

import com.neovita.server.db.repositories.HealthRepository
import com.neovita.shared.network.dto.HealthSummaryDto
import com.neovita.shared.network.dto.HealthUploadRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes(repo: HealthRepository) {
    authenticate("jwt-auth") {
        post("/health/metrics") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<HealthUploadRequest>()
            repo.upsertAll(userId, req.metrics)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/health/summary") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val s = repo.summary(userId)
            call.respond(
                HealthSummaryDto(
                    avgDailySteps = s.avgDailySteps,
                    avgSleepMinutes = s.avgSleepMinutes,
                    restingHeartRate = s.restingHeartRate,
                    daysWithData = repo.daysWithData(userId)
                )
            )
        }
    }
}
