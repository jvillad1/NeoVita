package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.module
import com.neovita.server.services.JwtService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the assessment write path against the column-width class of bug: the app stores the
 * user-visible label, so every option the UI can offer must fit its column. "Menos de 5 horas"
 * (16 chars) used to blow up a varchar(10) with a 500 and silently lose the assessment.
 */
class AssessmentRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret, issuer = "neovita", audience = "neovita-app", expirationMs = 3600_000L
    )

    private fun testConfig(dbName: String) = MapApplicationConfig(
        "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "database.driver" to "org.h2.Driver",
        "jwt.secret" to testSecret,
        "jwt.issuer" to "neovita",
        "jwt.audience" to "neovita-app",
        "jwt.expirationMs" to "3600000",
        "claude.apiKey" to "dummy-key",
        "claude.model" to "dummy-model",
    )

    // Exactamente las opciones de AssessmentViewModel.QUESTIONS.
    private val sleepOptions =
        listOf("8+ horas", "7-8 horas", "6-7 horas", "5-6 horas", "Menos de 5 horas")
    private val frequencyOptions =
        listOf("Todos los días", "4-5 veces", "2-3 veces", "1 vez", "Nunca")
    private val typeOptions = listOf(
        "Cardio (caminar, correr, ciclismo)", "Pesas o resistencia", "Yoga o pilates"
    )

    @Test
    fun `every sleep option the app offers can be saved`() = testApplication {
        environment { config = testConfig("assessment_test_sleep") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("sleep@test.dev", "Sleep")
        val token = jwtService.generateToken(user.id, "USER")

        sleepOptions.forEach { option ->
            val response = client.post("/api/assessments") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    """{"exerciseFrequency":"2-3 veces","exerciseType":"Yoga o pilates",
                       "sleepHours":"$option","sleepQuality":6,"mainGoal":"Aumentar energía y vitalidad"}"""
                )
            }
            assertEquals(HttpStatusCode.Created, response.status, "falló con sleepHours=$option")
        }
    }

    @Test
    fun `every exercise frequency and type the app offers can be saved`() = testApplication {
        environment { config = testConfig("assessment_test_exercise") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("exercise@test.dev", "Exercise")
        val token = jwtService.generateToken(user.id, "USER")

        frequencyOptions.forEach { frequency ->
            typeOptions.forEach { type ->
                val response = client.post("/api/assessments") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"exerciseFrequency":"$frequency","exerciseType":"$type",
                           "sleepHours":"7-8 horas","sleepQuality":7,"mainGoal":"Mejorar memoria y función cognitiva"}"""
                    )
                }
                assertEquals(
                    HttpStatusCode.Created, response.status,
                    "falló con frequency=$frequency type=$type"
                )
            }
        }
    }

    @Test
    fun `the saved assessment round-trips through latest`() = testApplication {
        environment { config = testConfig("assessment_test_roundtrip") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("roundtrip@test.dev", "Round")
        val token = jwtService.generateToken(user.id, "USER")

        client.post("/api/assessments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"exerciseFrequency":"1 vez","exerciseType":"Yoga o pilates",
                   "sleepHours":"Menos de 5 horas","sleepQuality":3,"mainGoal":"Aumentar energía y vitalidad"}"""
            )
        }
        val latest = client.get("/api/assessments/latest") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, latest.status)
        val body = latest.bodyAsText()
        assertEquals(true, body.contains("Menos de 5 horas"), body)
    }
}
