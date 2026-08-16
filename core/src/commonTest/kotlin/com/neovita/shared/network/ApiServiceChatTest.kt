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
    fun `an event split across several data lines keeps its newlines`() = runTest {
        // Así es como SSE representa un texto con saltos de línea: varias líneas `data:`
        // dentro del mismo evento. Quedarse sólo con la primera perdía texto y pegaba los
        // fragmentos, que es lo que se veía en producción.
        val api = serviceCapturing(
            mutableListOf(),
            sse = "data: Duerme mejor\ndata: - acuéstate a la misma hora\n\ndata: [DONE]\n\n"
        )

        assertEquals(
            listOf("Duerme mejor\n- acuéstate a la misma hora"),
            api.streamChat(listOf(ChatMessageDto("user", "x"))).toList()
        )
    }

    @Test
    fun `an empty data line inside an event is a blank line, not a separator`() = runTest {
        val api = serviceCapturing(
            mutableListOf(),
            sse = "data: uno\ndata: \ndata: dos\n\ndata: [DONE]\n\n"
        )

        assertEquals(
            listOf("uno\n\ndos"),
            api.streamChat(listOf(ChatMessageDto("user", "x"))).toList()
        )
    }

    @Test
    fun `an event whose first data line is empty keeps its leading newline`() = runTest {
        // Medido contra producción: Anthropic manda deltas como "\n" + texto, que el servidor
        // enmarca con una primera línea `data:` vacía. El acumulador sólo anteponía el salto
        // "si ya había algo", así que con la primera línea vacía el salto se perdía y dos
        // párrafos quedaban pegados ("...celular.Evita: Café...").
        val api = serviceCapturing(
            mutableListOf(),
            sse = "data: \ndata: Acuéstate temprano\n\ndata: [DONE]\n\n"
        )

        assertEquals(
            listOf("\nAcuéstate temprano"),
            api.streamChat(listOf(ChatMessageDto("user", "x"))).toList()
        )
    }

    @Test
    fun `several empty data lines in a row survive as blank lines`() = runTest {
        val api = serviceCapturing(
            mutableListOf(),
            sse = "data: \ndata: \ndata: texto\n\ndata: [DONE]\n\n"
        )

        assertEquals(
            listOf("\n\ntexto"),
            api.streamChat(listOf(ChatMessageDto("user", "x"))).toList()
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
