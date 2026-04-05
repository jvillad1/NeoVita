package com.neovita.server.plugins

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.PlanRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.routes.*
import com.neovita.server.services.ClaudeService
import com.neovita.server.services.GoogleAuthService
import com.neovita.server.services.JwtService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    googleAuthService: GoogleAuthService,
    jwtService: JwtService,
    userRepo: UserRepository,
    assessmentRepo: AssessmentRepository,
    planRepo: PlanRepository,
    claudeService: ClaudeService
) {
    routing {
        authRoutes(googleAuthService, jwtService, userRepo)
        userRoutes(userRepo)
        assessmentRoutes(assessmentRepo)
        planRoutes(claudeService, assessmentRepo, planRepo)
        chatRoutes(claudeService)
        b2bRoutes(userRepo, assessmentRepo)
    }
}
