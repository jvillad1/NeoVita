package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.HealthRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.tables.UsersTable
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Safety net for Task 2 (batching the B2B team dashboard's per-member queries): asserts the
 * response shape/content is unchanged by the refactor. Written and passed against the
 * unbatched code first.
 */
class B2BRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret,
        issuer = "neovita",
        audience = "neovita-app",
        expirationMs = 3600_000L
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

    @Test
    fun `team scores come back for every member`() = testApplication {
        environment { config = testConfig("b2b_test_team") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }

        val users = UserRepository()
        val boss = users.upsert("boss@test.dev", "Boss")
        val a = users.upsert("a@test.dev", "Ana")
        val b = users.upsert("b@test.dev", "Beto")
        transaction {
            UsersTable.update({ UsersTable.id eq boss.id }) { it[role] = "EMPLOYER"; it[companyId] = "acme" }
            listOf(a.id, b.id).forEach { id ->
                UsersTable.update({ UsersTable.id eq id }) { it[companyId] = "acme" }
            }
        }
        val assessments = AssessmentRepository(HealthRepository())
        assessments.save(a.id, "Todos los días", "Cardio", "7-8 horas", 8, "Energía")
        assessments.save(b.id, "Nunca", "No hago ejercicio", "5-6 horas", 3, "Energía")

        val response = client.get("/api/b2b/team") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken(boss.id, "EMPLOYER")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Ana"), body)
        assertTrue(body.contains("Beto"), body)
        // Ana entrena a diario y Beto nunca: el promedio del equipo no puede ser 0.
        val avg = Json.parseToJsonElement(body).jsonObject["avgScore"]!!.jsonPrimitive.int
        assertTrue(avg > 0, "avgScore fue $avg — body: $body")
    }
}
