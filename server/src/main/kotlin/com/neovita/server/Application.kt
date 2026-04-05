package com.neovita.server

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.PlanRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.*
import com.neovita.server.services.ClaudeService
import com.neovita.server.services.GoogleAuthService
import com.neovita.server.services.JwtService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    val config = environment.config
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }
    val userRepo = UserRepository()
    val assessmentRepo = AssessmentRepository()
    val planRepo = PlanRepository()
    val jwtService = JwtService(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
        expirationMs = config.property("jwt.expirationMs").getString().toLong()
    )
    val googleService = GoogleAuthService(httpClient)
    val claudeService = ClaudeService(
        client = httpClient,
        apiKey = config.property("claude.apiKey").getString(),
        model = config.property("claude.model").getString()
    )

    configureDatabase()
    configureSerialization()
    configureAuthentication(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString()
    )
    configureRouting(googleService, jwtService, userRepo, assessmentRepo, planRepo, claudeService)
}
