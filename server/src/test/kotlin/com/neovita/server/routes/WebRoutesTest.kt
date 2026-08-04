package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.tables.UsersTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebRoutesTest {

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
    fun `demo page renders without session`() = testApplication {
        environment { config = testConfig("web_test_demo_no_session") }
        application { module() }

        val response = client.get("/web/demo")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("NeoVita"))
        assertTrue(body.contains("Sesión: no detectada"))
    }

    @Test
    fun `demo page detects the session header`() = testApplication {
        environment { config = testConfig("web_test_demo_session") }
        application { module() }

        val response = client.get("/web/demo") {
            header(HttpHeaders.Authorization, "Bearer some-jwt")
        }
        assertTrue(response.bodyAsText().contains("Sesión: activa"))
    }

    @Test
    fun `screen editor requires auth`() = testApplication {
        environment { config = testConfig("web_test_editor_401") }
        application { module() }

        val response = client.get("/web/admin/screens")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `screen editor requires EMPLOYER role`() = testApplication {
        environment { config = testConfig("web_test_editor_403") }
        application { module() }
        startApplication()

        val user = UserRepository().upsert("plain@test.dev", "Plain")
        val token = jwtService.generateToken(user.id, "USER")

        val response = client.get("/web/admin/screens") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `screen editor bootstraps the caller's token for an employer`() = testApplication {
        environment { config = testConfig("web_test_editor_200") }
        application { module() }
        startApplication()

        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val response = client.get("/web/admin/screens") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("const TOKEN = \"$token\";"), body)
        assertFalse(body.contains("__BOOTSTRAP_TOKEN__"), body)
    }
}
