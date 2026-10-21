package com.neovita.server.routes

import com.neovita.server.db.tables.EventsTable
import com.neovita.server.db.tables.HabitLogsTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret, issuer = "neovita", audience = "neovita-app", expirationMs = 3600_000L
    )
    private val json = Json { ignoreUnknownKeys = true }

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

    private fun seedLog(userId: String, habitId: String, date: LocalDate) = transaction {
        HabitLogsTable.insert {
            it[HabitLogsTable.userId] = userId
            it[HabitLogsTable.habitId] = habitId
            it[HabitLogsTable.date] = date.toString()
            it[loggedAt] = System.currentTimeMillis()
        }
    }

    @Test
    fun `toggling on then off marks and unmarks today, same day`() = testApplication {
        environment { config = testConfig("habits_toggle") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        val on = client.post("/api/habits/meditation/toggle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, on.status)
        var status = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(on.bodyAsText())
        assertTrue(status["loggedHabitIds"].toString().contains("meditation"))

        val off = client.post("/api/habits/meditation/toggle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        status = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(off.bodyAsText())
        assertFalse(status["loggedHabitIds"].toString().contains("meditation"), status.toString())
    }

    @Test
    fun `rejects an unknown habit id`() = testApplication {
        environment { config = testConfig("habits_invalid") }
        application { module() }
        val token = jwtService.generateToken("user-1", "USER")

        val response = client.post("/api/habits/not-a-real-habit/toggle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `marking a habit logs a habit_logged event, unmarking does not`() = testApplication {
        environment { config = testConfig("habits_events") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        client.post("/api/habits/sleep_8h/toggle") { header(HttpHeaders.Authorization, "Bearer $token") }
        client.post("/api/habits/sleep_8h/toggle") { header(HttpHeaders.Authorization, "Bearer $token") }

        val count = transaction {
            EventsTable.selectAll().where { (EventsTable.userId eq "user-1") and (EventsTable.type eq "habit_logged") }.count()
        }
        assertEquals(1, count, "un toggle on + un toggle off debe dejar exactamente un evento, no dos ni cero")
    }

    @Test
    fun `streak counts consecutive days ending today`() = testApplication {
        environment { config = testConfig("habits_streak_today") }
        application { module() }
        startApplication()
        val today = LocalDate.now()
        seedLog("user-1", "meditation", today)
        seedLog("user-1", "walk_10k", today.minusDays(1))
        seedLog("user-1", "sleep_8h", today.minusDays(2))

        val r = json.decodeFromString<com.neovita.shared.network.dto.HabitStatusDto>(
            client.get("/api/habits/today") {
                header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-1", "USER")}")
            }.bodyAsText()
        )
        assertEquals(3, r.streak)
    }

    @Test
    fun `streak survives a day not yet logged, as long as yesterday was`() = testApplication {
        environment { config = testConfig("habits_streak_grace") }
        application { module() }
        startApplication()
        val today = LocalDate.now()
        seedLog("user-1", "meditation", today.minusDays(1))
        seedLog("user-1", "meditation", today.minusDays(2))

        val r = json.decodeFromString<com.neovita.shared.network.dto.HabitStatusDto>(
            client.get("/api/habits/today") {
                header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-1", "USER")}")
            }.bodyAsText()
        )
        assertEquals(2, r.streak, "no marcar hoy TODAVÍA no debe romper la racha de ayer")
    }

    @Test
    fun `streak resets to zero after a full missed day`() = testApplication {
        environment { config = testConfig("habits_streak_broken") }
        application { module() }
        startApplication()
        val today = LocalDate.now()
        seedLog("user-1", "meditation", today.minusDays(2))
        seedLog("user-1", "meditation", today.minusDays(3))

        val r = json.decodeFromString<com.neovita.shared.network.dto.HabitStatusDto>(
            client.get("/api/habits/today") {
                header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-1", "USER")}")
            }.bodyAsText()
        )
        assertEquals(0, r.streak, "antier + antes de antier, sin ayer ni hoy, es una racha rota")
    }

    @Test
    fun `no habits logged means a streak of zero, not an error`() = testApplication {
        environment { config = testConfig("habits_streak_empty") }
        application { module() }
        val response = client.get("/api/habits/today") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-1", "USER")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val r = json.decodeFromString<com.neovita.shared.network.dto.HabitStatusDto>(response.bodyAsText())
        assertEquals(0, r.streak)
        assertEquals(emptyList(), r.loggedHabitIds)
    }

    @Test
    fun `requires auth`() = testApplication {
        environment { config = testConfig("habits_401") }
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/habits/today").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/habits/meditation/toggle").status)
    }
}
