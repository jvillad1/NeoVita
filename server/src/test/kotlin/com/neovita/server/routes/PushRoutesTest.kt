package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.tables.UsersTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import com.neovita.server.services.PushService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushRoutesTest {

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

    /** Creates a user and promotes it to EMPLOYER; returns its id. */
    private fun employer(): String {
        val user = UserRepository().upsert("admin@test.dev", "Admin")
        transaction { UsersTable.update({ UsersTable.id eq user.id }) { it[role] = "EMPLOYER" } }
        return user.id
    }

    @Test
    fun `push test returns 503 when sending is not configured`() = testApplication {
        environment { config = testConfig("push_test_disabled") }
        application { module() }
        // Force the app (and its DB connection) to start before employer() writes
        // through a fresh UserRepository/transaction — otherwise that write can race
        // against the lazy-started test engine and land on the wrong H2 instance.
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")
        val response = client.post("/api/push/test") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hola","body":"Prueba"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("PUSH_DISABLED"))
    }

    @Test
    fun `push test requires the EMPLOYER role`() = testApplication {
        environment { config = testConfig("push_test_role") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("user@test.dev", "User")
        val token = jwtService.generateToken(user.id, "USER")
        val response = client.post("/api/push/test") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hola","body":"Prueba"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `disabled service reports zero sends without touching the network`() {
        val service = PushService(serviceAccountJson = null)
        assertEquals(false, service.enabled)
        assertEquals(0, service.send(listOf("t1", "t2"), "Hola", "Prueba", null))
    }
}
