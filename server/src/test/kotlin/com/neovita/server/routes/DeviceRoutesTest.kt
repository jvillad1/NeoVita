package com.neovita.server.routes

import com.neovita.server.db.tables.DeviceTokensTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceRoutesTest {

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

    @Test
    fun `registers and upserts a device token`() = testApplication {
        environment { config = testConfig("devices_test_upsert") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        repeat(2) {
            val response = client.post("/api/devices/token") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"token":"fcm-abc","platform":"android"}""")
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
        val rows = transaction { DeviceTokensTable.selectAll().count() }
        assertEquals(1, rows)
    }

    @Test
    fun `rejects blank token`() = testApplication {
        environment { config = testConfig("devices_test_blank") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")
        val response = client.post("/api/devices/token") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"  ","platform":"android"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `requires auth`() = testApplication {
        environment { config = testConfig("devices_test_401") }
        application { module() }
        val response = client.post("/api/devices/token")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
