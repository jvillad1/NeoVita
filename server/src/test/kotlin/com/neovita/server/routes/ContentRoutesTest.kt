package com.neovita.server.routes

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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The content backoffice (create/edit/delete the dashboard feed) used to be gated by the
 * EMPLOYER role — the same role that means "can see my own company's team" in B2BRoutes.
 * That let any single company's team lead rewrite the article feed every user of the app
 * sees, not just their own team's. isContentAdmin is the fix: its own flag, independent of
 * company/team membership.
 */
class ContentRoutesTest {

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

    /** Company/team role only — must NOT unlock the content backoffice. */
    private fun employer(): String {
        val user = UserRepository().upsert("boss@test.dev", "Boss")
        transaction { UsersTable.update({ UsersTable.id eq user.id }) { it[role] = "EMPLOYER" } }
        return user.id
    }

    /** The content/screens backoffice flag — not a company employer. */
    private fun contentAdmin(): String {
        val user = UserRepository().upsert("content-admin@test.dev", "Content Admin")
        transaction { UsersTable.update({ UsersTable.id eq user.id }) { it[isContentAdmin] = true } }
        return user.id
    }

    private val validBody = """
        {"title":"Duerme mejor","category":"SLEEP","type":"TIP","teaser":"...","readMinutes":3}
    """.trimIndent()

    @Test
    fun `the public feed needs no auth`() = testApplication {
        environment { config = testConfig("content_test_public") }
        application { module() }

        val response = client.get("/api/content")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `an employer without content-admin rights cannot manage content`() = testApplication {
        // Regression test — this is exactly the gap that motivated splitting the role.
        environment { config = testConfig("content_test_employer_not_admin") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/api/content/all") { header(HttpHeaders.Authorization, "Bearer $token") }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/api/content") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json); setBody(validBody)
            }.status
        )
    }

    @Test
    fun `a content admin can create, list, update and delete`() = testApplication {
        environment { config = testConfig("content_test_crud") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(contentAdmin(), "USER")

        val created = client.post("/api/content") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val id = Regex("\"id\":\"([^\"]+)\"").find(created.bodyAsText())!!.groupValues[1]

        assertTrue(
            client.get("/api/content/all") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText().contains("Duerme mejor")
        )

        val updated = client.put("/api/content/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Duerme mejor v2","category":"SLEEP","type":"TIP","teaser":"...","readMinutes":3}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertTrue(updated.bodyAsText().contains("Duerme mejor v2"))

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/api/content/$id") { header(HttpHeaders.Authorization, "Bearer $token") }.status
        )
    }

    @Test
    fun `an invalid category is rejected with 400`() = testApplication {
        environment { config = testConfig("content_test_invalid_category") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(contentAdmin(), "USER")

        val response = client.post("/api/content") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"x","category":"NOPE","type":"TIP","teaser":"...","readMinutes":3}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("INVALID_CATEGORY"))
    }
}
