package com.neovita.server.routes

import com.neovita.server.db.repositories.ScreenRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.SEED_SCREENS
import com.neovita.server.db.tables.ScreensTable
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * First HTTP test harness in the repo: uses testApplication + an in-memory H2 database
 * configured through MapApplicationConfig (application.conf's keys, PostgreSQL-compatible
 * H2 mode). Each test uses its own H2 db name to stay isolated from the others.
 */
class ScreenRoutesTest {

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

    /** Creates a user and promotes it to EMPLOYER; returns its id. */
    private fun employer(): String {
        val user = UserRepository().upsert("admin@test.dev", "Admin")
        transaction { UsersTable.update({ UsersTable.id eq user.id }) { it[role] = "EMPLOYER" } }
        return user.id
    }

    @Test
    fun `screen 200 with seeded dashboard`() = testApplication {
        environment { config = testConfig("screens_test_200") }
        application { module() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        val response = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("dashboard", body["slug"]?.jsonPrimitive?.content)
        assertEquals(1, body["version"]?.jsonPrimitive?.int)
        assertEquals(5, body["sections"]?.jsonArray?.size)
    }

    @Test
    fun `screen 304 when version matches`() = testApplication {
        environment { config = testConfig("screens_test_304") }
        application { module() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        val response = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.IfNoneMatch, "1")
        }

        assertEquals(HttpStatusCode.NotModified, response.status)
        assertTrue(response.bodyAsText().isEmpty())
    }

    @Test
    fun `screen 404 for unknown slug`() = testApplication {
        environment { config = testConfig("screens_test_404") }
        application { module() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        val response = client.get("/api/screens/nope") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `screen requires auth`() = testApplication {
        environment { config = testConfig("screens_test_401") }
        application { module() }

        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/screens/dashboard")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `seed is idempotent`() = testApplication {
        environment { config = testConfig("screens_test_seed") }
        application { module() }
        startApplication()

        // Simulate a manual edit made after the initial boot seed.
        transaction {
            ScreensTable.update({ ScreensTable.slug eq "dashboard" }) { it[version] = 7 }
        }

        // Re-invoking the seed must not duplicate rows nor clobber the manual edit.
        ScreenRepository().seedIfEmpty(SEED_SCREENS)

        val rowCount = transaction { ScreensTable.selectAll().count() }
        assertEquals(1L, rowCount)

        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")
        val response = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(7, body["version"]?.jsonPrimitive?.int)
    }

    private val validBody = """
        {"sections":[
          {"type":"HERO_SCORE"},
          {"type":"CARD_ROW","title":"Novedades","cards":[
            {"title":"Página demo","action":{"type":"OPEN_WEBVIEW","target":"/web/demo"}}
          ]}
        ]}
    """.trimIndent()

    @Test
    fun `employer updates a screen and the version bumps`() = testApplication {
        environment { config = testConfig("screens_test_put") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val before = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        val beforeVersion = Json.parseToJsonElement(before).jsonObject["version"]!!.jsonPrimitive.int

        val put = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val newVersion = Json.parseToJsonElement(put.bodyAsText()).jsonObject["version"]!!.jsonPrimitive.int
        assertEquals(beforeVersion + 1, newVersion)

        // Lo guardado es lo que se lee después.
        val after = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        assertTrue(after.contains("Página demo"), after)
        assertTrue(after.contains("\"version\":$newVersion"), after)
    }

    @Test
    fun `an invalid definition is rejected with the reason`() = testApplication {
        environment { config = testConfig("screens_test_put_invalid") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val response = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"sections":[{"type":"MYSTERY_MEAT"}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("MYSTERY_MEAT"), response.bodyAsText())
    }

    @Test
    fun `a non-employer cannot update a screen`() = testApplication {
        environment { config = testConfig("screens_test_put_role") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("plain@test.dev", "Plain")
        val token = jwtService.generateToken(user.id, "USER")

        val response = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `employer lists the screens`() = testApplication {
        environment { config = testConfig("screens_test_list") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val response = client.get("/api/screens") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("dashboard"), response.bodyAsText())
    }
}
