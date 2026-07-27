package com.neovita.shared.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiServiceTest {
    private val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/auth/google" -> respond(
                content = """{"token":"jwt-abc","isNewUser":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
            "/config" -> respond(
                content = """{"googleClientId":"web-client-id-123"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
            else -> respond("Not Found", HttpStatusCode.NotFound)
        }
    }

    private val apiService = ApiService(
        baseUrl = "http://localhost",
        httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
    )

    @Test fun `authenticateWithGoogle returns token`() = runTest {
        val result = apiService.authenticateWithGoogle("any-token")
        assertTrue(result.isSuccess)
        assertEquals("jwt-abc", result.getOrNull()?.token)
    }

    @Test fun `getConfig returns google client id`() = runTest {
        val result = apiService.getConfig()
        assertTrue(result.isSuccess)
        assertEquals("web-client-id-123", result.getOrNull()?.googleClientId)
    }
}
