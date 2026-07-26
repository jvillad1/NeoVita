package com.neovita.shared.config

import com.neovita.shared.network.ApiService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteConfigRepositoryTest {
    private var failNow = false
    private val engine = MockEngine { _ ->
        if (failNow) respond("boom", HttpStatusCode.InternalServerError)
        else respond(
            """{"maintenance":true,"unknownFutureField":123}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    private val repo = RemoteConfigRepository(
        ApiService(
            baseUrl = "http://localhost",
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
    )

    @Test fun `starts empty and stores fetched config`() = runTest {
        assertNull(repo.config.value)
        repo.refresh()
        assertTrue(repo.config.value!!.maintenance)
    }

    @Test fun `failed refresh keeps last good config`() = runTest {
        repo.refresh()
        val good = repo.config.value
        failNow = true
        repo.refresh()
        assertEquals(good, repo.config.value)
    }
}
