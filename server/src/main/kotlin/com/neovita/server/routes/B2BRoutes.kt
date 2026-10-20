package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireRole
import com.neovita.shared.network.dto.PillarScoresDto
import com.neovita.shared.network.dto.TeamResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.math.roundToInt

fun Route.b2bRoutes(userRepository: UserRepository, assessmentRepo: AssessmentRepository) {
    authenticate("jwt-auth") {
        get("/b2b/team") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@get
            val user = userRepository.findById(call.principal<UserIdPrincipal>()!!.name)!!
            // Employers are left out of their own team's numbers: someone who knows their own
            // score can subtract it from the average, which shrinks the effective group by one.
            val employees = userRepository.findByCompany(user.companyId!!)
                .filter { it.role != "EMPLOYER" }
            val scores = assessmentRepo.latestScoresFor(employees.map { it.id }).values
            call.respond(
                TeamResponse(
                    totalMembers = employees.size,
                    evaluatedCount = scores.size,
                    averages = if (scores.size >= TeamResponse.MIN_GROUP_SIZE) scores.average() else null
                )
            )
        }
    }
}

private fun Collection<PillarScoresDto>.average() = PillarScoresDto(
    overall = map { it.overall }.average().roundToInt(),
    exercise = map { it.exercise }.average().roundToInt(),
    sleep = map { it.sleep }.average().roundToInt(),
    nutrition = map { it.nutrition }.average().roundToInt(),
)
