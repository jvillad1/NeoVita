package com.neovita.server.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtServiceTest {
    private val service = JwtService(
        secret = "test-secret-that-is-long-enough-32chars",
        issuer = "neovita",
        audience = "neovita-app",
        expirationMs = 3600_000L
    )

    @Test fun `generates token and verifies user id`() {
        val token = service.generateToken(userId = "user-123", role = "USER")
        val principal = service.verify(token)
        assertEquals("user-123", principal?.userId)
    }

    @Test fun `verify returns role from token`() {
        val token = service.generateToken(userId = "user-456", role = "EMPLOYER")
        val principal = service.verify(token)
        assertEquals("EMPLOYER", principal?.role)
    }

    @Test fun `returns null for tampered token`() {
        val token = service.generateToken("user-123", "USER") + "tampered"
        assertNull(service.verify(token))
    }
}
