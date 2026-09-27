package com.neovita.server.routes

import com.neovita.server.module
import com.neovita.server.services.JwtService
import com.neovita.shared.network.dto.ManualMetricsDto
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ManualMetricsRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret, issuer = "neovita", audience = "neovita-app", expirationMs = 3600_000L
    )
    private val today = LocalDate.now().toString()

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

    private suspend fun ApplicationTestBuilder.post(path: String, token: String, json: String) =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    private suspend fun ApplicationTestBuilder.latest(token: String): ManualMetricsDto {
        val body = client.get("/api/health/manual") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(body)
    }

    @Test
    fun `saves manual metrics and reads them back`() = testApplication {
        environment { config = testConfig("manual_metrics_roundtrip") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        val res = post("/api/health/manual", token,
            """{"date":"$today","steps":8000,"weightKg":72.5,"bloodPressureSys":120,"bloodPressureDia":80,"glucoseMgdl":95}""")

        assertEquals(HttpStatusCode.NoContent, res.status)
        assertEquals(
            ManualMetricsDto(steps = 8000, weightKg = 72.5, bloodPressureSys = 120, bloodPressureDia = 80, glucoseMgdl = 95),
            latest(token)
        )
    }

    @Test
    fun `a partial save keeps the other fields of that day`() = testApplication {
        environment { config = testConfig("manual_metrics_partial") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        post("/api/health/manual", token, """{"date":"$today","weightKg":72.5,"glucoseMgdl":95}""")
        post("/api/health/manual", token, """{"date":"$today","weightKg":71.0}""")

        val m = latest(token)
        assertEquals(71.0, m.weightKg, "el peso nuevo debe reemplazar al anterior")
        assertEquals(95, m.glucoseMgdl, "guardar sólo el peso no debe borrar la glucosa del mismo día")
    }

    @Test
    fun `manual entry does not wipe what the device synced that day`() = testApplication {
        environment { config = testConfig("manual_metrics_no_clobber") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        post("/api/health/metrics", token,
            """{"metrics":[{"date":"$today","steps":5000,"sleepMinutes":420,"avgHeartRate":70}]}""")
        post("/api/health/manual", token, """{"date":"$today","weightKg":72.5}""")

        val summary = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        assertEquals(true, summary.contains("\"avgSleepMinutes\":420"), summary)
        assertEquals(true, summary.contains("\"avgHeartRate\":70"), summary)
        assertEquals(72.5, latest(token).weightKg)
    }

    @Test
    fun `rejects an empty save`() = testApplication {
        environment { config = testConfig("manual_metrics_empty") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        assertEquals(HttpStatusCode.BadRequest, post("/api/health/manual", token, """{"date":"$today"}""").status)
    }

    @Test
    fun `rejects a lone systolic, an inverted pressure, and an absurd weight`() = testApplication {
        environment { config = testConfig("manual_metrics_invalid") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        assertEquals(HttpStatusCode.BadRequest,
            post("/api/health/manual", token, """{"date":"$today","bloodPressureSys":120}""").status)
        assertEquals(HttpStatusCode.BadRequest,
            post("/api/health/manual", token, """{"date":"$today","bloodPressureSys":70,"bloodPressureDia":120}""").status)
        assertEquals(HttpStatusCode.BadRequest,
            post("/api/health/manual", token, """{"date":"$today","weightKg":7250.0}""").status)
    }

    @Test
    fun `rejects a date outside the window`() = testApplication {
        environment { config = testConfig("manual_metrics_date") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        assertEquals(HttpStatusCode.BadRequest,
            post("/api/health/manual", token, """{"date":"2099-01-01","weightKg":72.5}""").status)
    }

    @Test
    fun `requires auth`() = testApplication {
        environment { config = testConfig("manual_metrics_401") }
        application { module() }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/health/manual").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/health/manual").status)
    }
}
