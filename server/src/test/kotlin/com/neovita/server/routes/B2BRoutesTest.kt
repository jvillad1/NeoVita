package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.HealthRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.tables.UsersTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import com.neovita.shared.network.dto.TeamResponse
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The employer panel shows health-derived scores, which are sensitive data under Colombia's
 * Ley 1581 de 2012. The employer may only ever see team aggregates, and only once enough
 * people have answered that no single person's score can be read off the average.
 */
class B2BRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret,
        issuer = "neovita",
        audience = "neovita-app",
        expirationMs = 3600_000L
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

    private val answers = listOf(
        listOf("Todos los días", "Cardio", "7-8 horas", "8"),
        listOf("Nunca", "No hago ejercicio", "5-6 horas", "3"),
        listOf("3-4 veces por semana", "Fuerza", "7-8 horas", "6"),
        listOf("1-2 veces por semana", "Yoga", "Menos de 5 horas", "4"),
        listOf("Todos los días", "Fuerza", "Más de 8 horas", "9"),
        listOf("Nunca", "No hago ejercicio", "7-8 horas", "5"),
    )

    private class Team(val bossToken: String, val employeeIds: List<String>, val emails: List<String>)

    /** A company "acme" with one employer (who also answered) and [employees] employees. */
    private fun seedTeam(employees: Int, evaluated: Int): Team {
        val users = UserRepository()
        val assessments = AssessmentRepository(HealthRepository())
        val boss = users.upsert("boss@acme.co", "Jefa")
        val staff = (1..employees).map { users.upsert("empleado$it@acme.co", "Empleado $it") }
        transaction {
            UsersTable.update({ UsersTable.id eq boss.id }) { it[role] = "EMPLOYER"; it[companyId] = "acme" }
            staff.forEach { u -> UsersTable.update({ UsersTable.id eq u.id }) { it[companyId] = "acme" } }
        }
        assessments.save(boss.id, "Todos los días", "Cardio", "7-8 horas", 10, "Energía")
        staff.take(evaluated).forEachIndexed { i, u ->
            val a = answers[i % answers.size]
            assessments.save(u.id, a[0], a[1], a[2], a[3].toInt(), "Energía")
        }
        return Team(
            bossToken = jwtService.generateToken(boss.id, "EMPLOYER"),
            employeeIds = staff.map { it.id },
            emails = staff.map { it.email } + boss.email,
        )
    }

    @Test
    fun `no individual identity or score ever reaches the employer`() = testApplication {
        environment { config = testConfig("b2b_no_individuals") }
        application { module() }
        startApplication()
        val team = seedTeam(employees = 6, evaluated = 6)

        val body = client.get("/api/b2b/team") {
            header(HttpHeaders.Authorization, "Bearer ${team.bossToken}")
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.bodyAsText()

        (team.emails + team.employeeIds + listOf("Empleado", "Jefa")).forEach {
            assertFalse(body.contains(it), "the response leaks '$it': $body")
        }
    }

    @Test
    fun `with enough answers the employer gets team averages, without their own score`() = testApplication {
        environment { config = testConfig("b2b_aggregates") }
        application { module() }
        startApplication()
        val team = seedTeam(employees = 6, evaluated = 6)

        val body = client.get("/api/b2b/team") {
            header(HttpHeaders.Authorization, "Bearer ${team.bossToken}")
        }.bodyAsText()
        val r = json.decodeFromString<TeamResponse>(body)

        assertEquals(6, r.totalMembers, "the employer is not part of their own team stats")
        assertEquals(6, r.evaluatedCount)
        val expected = AssessmentRepository(HealthRepository()).latestScoresFor(team.employeeIds).values
        val avg = assertNotNull(r.averages, body)
        assertEquals(expected.map { it.overall }.average().roundToInt(), avg.overall)
        assertEquals(expected.map { it.exercise }.average().roundToInt(), avg.exercise)
        assertEquals(expected.map { it.sleep }.average().roundToInt(), avg.sleep)
        assertEquals(expected.map { it.nutrition }.average().roundToInt(), avg.nutrition)
    }

    @Test
    fun `below the minimum group size there are no averages at all`() = testApplication {
        environment { config = testConfig("b2b_below_minimum") }
        application { module() }
        startApplication()
        // 4 employees answered, plus the employer: if the employer counted, this would be 5.
        val team = seedTeam(employees = 6, evaluated = TeamResponse.MIN_GROUP_SIZE - 1)

        val r = json.decodeFromString<TeamResponse>(client.get("/api/b2b/team") {
            header(HttpHeaders.Authorization, "Bearer ${team.bossToken}")
        }.bodyAsText())

        assertEquals(6, r.totalMembers)
        assertEquals(TeamResponse.MIN_GROUP_SIZE - 1, r.evaluatedCount)
        assertNull(r.averages, "an average over fewer than ${TeamResponse.MIN_GROUP_SIZE} people exposes individuals")
    }

    @Test
    fun `a regular user cannot read the team`() = testApplication {
        environment { config = testConfig("b2b_forbidden") }
        application { module() }
        startApplication()
        val team = seedTeam(employees = 1, evaluated = 1)

        val response = client.get("/api/b2b/team") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken(team.employeeIds[0], "EMPLOYER")}")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
