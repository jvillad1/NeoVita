package com.neovita.shared.network

import com.neovita.shared.network.dto.DailyHealthMetricDto
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
                content = """{"googleClientId":"web-client-id-123","googleClientIdIos":"ios-client-id-456","firebase":{"apiKey":"AIza","appId":"1:2:android:3","projectId":"neovita-x","senderId":"99"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
            "/devices/token" -> respond("", HttpStatusCode.NoContent)
            "/health/metrics" -> respond("", HttpStatusCode.NoContent)
            "/health/summary" -> respond(
                content = """{"avgDailySteps":9000,"avgSleepMinutes":430,"daysWithData":5}""",
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

    @Test fun `getConfig parses the ios client id`() = runTest {
        assertEquals("ios-client-id-456", apiService.getConfig().getOrNull()?.googleClientIdIos)
    }

    @Test fun `getConfig parses firebase client config`() = runTest {
        val firebase = apiService.getConfig().getOrNull()?.firebase
        assertEquals("neovita-x", firebase?.projectId)
        assertEquals("99", firebase?.senderId)
    }

    @Test fun `registerDeviceToken posts and succeeds`() = runTest {
        assertTrue(apiService.registerDeviceToken("fcm-abc", "android").isSuccess)
    }

    @Test fun `uploadHealthMetrics posts and succeeds`() = runTest {
        val result = apiService.uploadHealthMetrics(
            listOf(DailyHealthMetricDto(date = "2026-07-26", steps = 9000))
        )
        assertTrue(result.isSuccess)
    }

    @Test fun `getHealthSummary parses the averages`() = runTest {
        val summary = apiService.getHealthSummary().getOrNull()
        assertEquals(9000, summary?.avgDailySteps)
        assertEquals(5, summary?.daysWithData)
    }
}
