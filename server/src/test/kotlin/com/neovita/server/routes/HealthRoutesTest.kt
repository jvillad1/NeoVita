package com.neovita.server.routes

import com.neovita.server.db.repositories.HealthRepository
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
import kotlin.test.assertTrue

class HealthRoutesTest {

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

    private val twoDays = """
        {"metrics":[
          {"date":"2026-07-25","steps":8000,"sleepMinutes":420,"restingHeartRate":60},
          {"date":"2026-07-26","steps":12000,"sleepMinutes":480,"restingHeartRate":58}
        ]}
    """.trimIndent()

    @Test
    fun `uploads metrics and returns an averaged summary`() = testApplication {
        environment { config = testConfig("health_test_summary") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        val upload = client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(twoDays)
        }
        assertEquals(HttpStatusCode.NoContent, upload.status)

        val summary = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, summary.status)
        val body = summary.bodyAsText()
        assertTrue(body.contains("\"avgDailySteps\":10000"), body)
        assertTrue(body.contains("\"avgSleepMinutes\":450"), body)
        assertTrue(body.contains("\"daysWithData\":2"), body)
    }

    @Test
    fun `re-uploading the same day updates instead of duplicating`() = testApplication {
        environment { config = testConfig("health_test_upsert") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-2", "USER")
        val oneDay = """{"metrics":[{"date":"2026-07-25","steps":%d}]}"""

        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(oneDay.format(3000))
        }
        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(oneDay.format(9000))
        }
        val body = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        assertTrue(body.contains("\"avgDailySteps\":9000"), body)
        assertTrue(body.contains("\"daysWithData\":1"), body)
    }

    @Test
    fun `metrics of one user never leak into another summary`() = testApplication {
        environment { config = testConfig("health_test_isolation") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-a", "USER")}")
            contentType(ContentType.Application.Json); setBody(twoDays)
        }
        val other = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-b", "USER")}")
        }.bodyAsText()
        assertTrue(other.contains("\"daysWithData\":0"), other)
    }

    @Test
    fun `health endpoints require auth`() = testApplication {
        environment { config = testConfig("health_test_401") }
        application { module() }
        startApplication()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/health/summary").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/health/metrics").status)
    }

    @Test
    fun `assessment scores use the uploaded health data`() = testApplication {
        environment { config = testConfig("health_test_scoring") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        // assessments.user_id has an FK to users.id, so unlike the other health-only tests
        // above (which never touch assessments) the JWT subject must be a real user row.
        val userId = UserRepository().upsert("user-3@test.dev", "User 3").id
        val token = jwtService.generateToken(userId, "USER")
        // Cuestionario pesimista: "1 vez" por semana.
        val assessment = """{"exerciseFrequency":"1 vez","exerciseType":"Cardio",
            "sleepHours":"6-8 horas","sleepQuality":6,"mainGoal":"Energía"}""".trimIndent()

        val before = client.post("/api/assessments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(assessment)
        }.bodyAsText()

        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"metrics":[{"date":"2026-07-26","steps":12000}]}""")
        }

        val after = client.get("/api/assessments/latest") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()

        assertTrue(before.contains("\"exercise\":40"), "declarado: $before")
        assertTrue(after.contains("\"exercise\":100"), "medido: $after")
    }
}
