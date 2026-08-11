package com.neovita.shared.network

import com.neovita.shared.network.dto.ChatMessageDto
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El chat no funcionaba desde NINGÚN cliente: `sseFlow` era la única petición con cuerpo de
 * todo ApiService que no ponía Content-Type. Sin él, ContentNegotiation no encuentra
 * convertidor y Ktor revienta antes de salir a la red con
 * `IllegalStateException: Fail to prepare request body for sending`.
 *
 * Invisible desde fuera: el ViewModel traducía cualquier excepción a "Coach no disponible,
 * intenta más tarde", así que parecía un problema del servidor. Y probarlo con curl lo tapa,
 * porque ahí la cabecera la pone quien escribe el comando.
 */
class ApiServiceChatTest {

    private fun serviceCapturing(
        captured: MutableList<HttpRequestData>,
        sse: String = "data: hola\n\ndata: [DONE]\n\n"
    ) = ApiService(
        baseUrl = "http://localhost",
        httpClient = HttpClient(MockEngine { request ->
            captured += request
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }) { install(ContentNegotiation) { json() } }
    )

    @Test
    fun `the chat request declares its body as JSON`() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val api = serviceCapturing(captured)

        api.streamChat(listOf(ChatMessageDto("user", "hola"))).toList()

        val ct = captured.single().body.contentType
        assertTrue(
            ct?.match(ContentType.Application.Json) == true,
            "sin Content-Type JSON, Ktor no puede serializar el cuerpo y el chat nunca sale: $ct"
        )
    }

    @Test
    fun `text deltas arrive and the DONE sentinel is not emitted`() = runTest {
        val api = serviceCapturing(
            mutableListOf(),
            sse = "data: Dormir\n\ndata:  mejor\n\ndata: [DONE]\n\n"
        )

        assertEquals(
            listOf("Dormir", " mejor"),
            api.streamChat(listOf(ChatMessageDto("user", "consejo"))).toList()
        )
    }
}
