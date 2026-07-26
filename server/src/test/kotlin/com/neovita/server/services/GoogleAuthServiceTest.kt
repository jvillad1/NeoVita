package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleAuthServiceTest {

    // Realistic tokeninfo payload: Google returns many more fields than we model.
    private val realTokenInfoJson = """
        {
          "iss": "https://accounts.google.com",
          "azp": "1234.apps.googleusercontent.com",
          "aud": "1234.apps.googleusercontent.com",
          "sub": "10769150350006150715113082367",
          "email": "ana@example.com",
          "email_verified": "true",
          "name": "Ana García",
          "picture": "https://lh3.googleusercontent.com/a/photo.jpg",
          "given_name": "Ana",
          "family_name": "García",
          "iat": "1719999999",
          "exp": "1720003599",
          "alg": "RS256",
          "kid": "abc123",
          "typ": "JWT"
        }
    """.trimIndent()

    private fun clientReturning(status: HttpStatusCode, body: String) = HttpClient(MockEngine { _ ->
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    })

    @Test fun `parses real tokeninfo payload with extra fields`() = runBlocking {
        val service = GoogleAuthService(
            clientReturning(HttpStatusCode.OK, realTokenInfoJson),
            clientId = "1234.apps.googleusercontent.com"
        )
        val user = service.verifyIdToken("some-token")
        assertEquals("ana@example.com", user?.email)
        assertEquals("Ana García", user?.name)
    }

    @Test fun `rejects token when no client id configured`() = runBlocking {
        val noClientId = GoogleAuthService(clientReturning(HttpStatusCode.OK, realTokenInfoJson))
        assertNull(noClientId.verifyIdToken("some-token"))

        val blankClientId = GoogleAuthService(
            clientReturning(HttpStatusCode.OK, realTokenInfoJson),
            clientId = ""
        )
        assertNull(blankClientId.verifyIdToken("some-token"))
    }

    @Test fun `accepts token when aud matches configured client id`() = runBlocking {
        val service = GoogleAuthService(
            clientReturning(HttpStatusCode.OK, realTokenInfoJson),
            clientId = "1234.apps.googleusercontent.com"
        )
        assertEquals("ana@example.com", service.verifyIdToken("some-token")?.email)
    }

    @Test fun `rejects token minted for another app`() = runBlocking {
        val service = GoogleAuthService(
            clientReturning(HttpStatusCode.OK, realTokenInfoJson),
            clientId = "other-app.apps.googleusercontent.com"
        )
        assertNull(service.verifyIdToken("some-token"))
    }

    @Test fun `returns null for invalid token response`() = runBlocking {
        // clientId set so the test exercises the non-2xx branch, not the fail-closed guard
        val service = GoogleAuthService(
            clientReturning(HttpStatusCode.BadRequest, """{"error":"invalid_token"}"""),
            clientId = "1234.apps.googleusercontent.com"
        )
        assertNull(service.verifyIdToken("bad-token"))
    }
}
