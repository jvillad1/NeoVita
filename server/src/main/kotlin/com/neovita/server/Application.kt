package com.neovita.server

import com.neovita.server.config.AppRuntimeConfig
import com.neovita.server.config.parseFeatures
import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.ContentRepository
import com.neovita.server.db.repositories.PlanRepository
import com.neovita.server.db.repositories.ScreenRepository
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
import io.ktor.server.netty.*

// EngineMain loads application.conf (resolving ${?DB_URL}/${?JWT_SECRET}/${?CLAUDE_API_KEY}
// from the environment) and binds ktor.deployment.port — which reads $PORT on Railway,
// defaulting to 8080 locally. Host defaults to 0.0.0.0.
fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = environment.config
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }
    val userRepo = UserRepository()
    val assessmentRepo = AssessmentRepository()
    val planRepo = PlanRepository()
    val contentRepo = ContentRepository()
    val screenRepo = ScreenRepository()
    val jwtService = JwtService(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
        expirationMs = config.property("jwt.expirationMs").getString().toLong()
    )
    val googleClientId = config.propertyOrNull("google.clientId")?.getString()
    val appConfig = AppRuntimeConfig(
        features = parseFeatures(config.propertyOrNull("appConfig.features")?.getString() ?: ""),
        minVersionAndroid = config.propertyOrNull("appConfig.minVersionAndroid")?.getString()?.toIntOrNull() ?: 0,
        minVersionIos = config.propertyOrNull("appConfig.minVersionIos")?.getString()?.toIntOrNull() ?: 0,
        maintenance = config.propertyOrNull("appConfig.maintenance")?.getString()?.toBoolean() ?: false
    )
    log.info("appConfig: $appConfig")
    val googleService = GoogleAuthService(httpClient, clientId = googleClientId)
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
    configureRouting(googleService, jwtService, userRepo, assessmentRepo, planRepo, claudeService, contentRepo, screenRepo, googleClientId, appConfig)
}
