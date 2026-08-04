package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireRole
import com.neovita.shared.network.dto.PillarScoresDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class TeamMemberDto(
    val userId: String, val name: String, val email: String, val scores: PillarScoresDto?
)

@Serializable
data class TeamResponse(val team: List<TeamMemberDto>, val avgScore: Int)

fun Route.b2bRoutes(userRepository: UserRepository, assessmentRepo: AssessmentRepository) {
    authenticate("jwt-auth") {
        get("/b2b/team") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@get
            val user = userRepository.findById(call.principal<UserIdPrincipal>()!!.name)!!
            val members = userRepository.findByCompany(user.companyId!!)
            val scoresByUser = assessmentRepo.latestScoresFor(members.map { it.id })
            val team = members.map { member ->
                TeamMemberDto(
                    userId = member.id, name = member.name, email = member.email,
                    scores = scoresByUser[member.id]
                )
            }
            val avgScore = team.mapNotNull { it.scores?.overall }
                .average()
                .let { if (it.isNaN()) 0 else it.toInt() }
            call.respond(TeamResponse(team, avgScore))
        }
    }
}
