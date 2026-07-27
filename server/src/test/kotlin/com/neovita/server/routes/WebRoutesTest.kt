package com.neovita.server.routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRoutesTest {

    @Test
    fun `demo page renders without session`() = testApplication {
        application { routing { webRoutes() } }
        val response = client.get("/web/demo")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("NeoVita"))
        assertTrue(body.contains("Sesión: no detectada"))
    }

    @Test
    fun `demo page detects the session header`() = testApplication {
        application { routing { webRoutes() } }
        val response = client.get("/web/demo") {
            header(HttpHeaders.Authorization, "Bearer some-jwt")
        }
        assertTrue(response.bodyAsText().contains("Sesión: activa"))
    }
}
