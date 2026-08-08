package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeServiceTest {

    private fun serviceWith(
        respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): Pair<ClaudeService, MutableList<String>> {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += (request.body as? io.ktor.http.content.TextContent)?.text
                ?: request.body.toByteArray().decodeToString()
            respond(request)
        }
        // Same client configuration as Application.kt — ContentNegotiation installed.
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        return ClaudeService(client, apiKey = "sk-ant-test", model = "claude-sonnet-4-6") to bodies
    }

    private fun sse(vararg lines: String) = lines.joinToString("") { "$it\n\n" }

    @Test
    fun `the request body is a JSON object, not a double-encoded string`() = runTest {
        val (service, bodies) = serviceWith {
            respond(
                sse("""data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"hola"}}"""),
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }

        service.streamChat(listOf(ClaudeMessage("user", "hola"))).toList()

        val raw = bodies.single()
        // A double-encoded body starts with a quote: "{\"model\":...}" — Anthropic rejects
        // that with a 400, and the swallowed error surfaces as an empty stream.
        assertTrue(raw.trimStart().startsWith("{"), "el cuerpo no es un objeto JSON: ${raw.take(80)}")
        val parsed = Json.parseToJsonElement(raw).jsonObject
        assertEquals("claude-sonnet-4-6", parsed["model"]?.jsonPrimitive?.content)
        assertEquals(true, parsed["stream"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `max_tokens travels in the body even though it has a default value`() = runTest {
        val (service, bodies) = serviceWith {
            respond(sse("data: [DONE]"), headers = headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }

        service.streamChat(listOf(ClaudeMessage("user", "hola"))).toList()

        // kotlinx.serialization omite los campos con valor por defecto salvo encodeDefaults;
        // sin él Anthropic responde 400 "max_tokens: Field required" en TODAS las peticiones.
        val parsed = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals(1024, parsed["max_tokens"]?.jsonPrimitive?.content?.toInt(), "falta max_tokens en el cuerpo")
    }

    @Test
    fun `text deltas are emitted`() = runTest {
        val (service, _) = serviceWith {
            respond(
                sse(
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"fun"}}""",
                    """data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"ciona"}}""",
                    "data: [DONE]"
                ),
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }

        assertEquals(listOf("fun", "ciona"), service.streamChat(listOf(ClaudeMessage("user", "x"))).toList())
    }

    @Test
    fun `an API error is surfaced, not swallowed into an empty stream`() = runTest {
        val (service, _) = serviceWith {
            respond(
                """{"type":"error","error":{"type":"invalid_request_error","message":"bad model"}}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val error = runCatching { service.streamChat(listOf(ClaudeMessage("user", "x"))).toList() }
            .exceptionOrNull()
        assertTrue(error != null, "un 400 de Anthropic terminó como stream vacío en vez de lanzar")
        // No basta con que lance: el motivo que da Anthropic tiene que llegar al log, que es
        // lo que convirtió "el chat responde vacío" en un diagnóstico en cuestión de minutos.
        assertTrue(
            error is ClaudeApiException && error.detail.contains("bad model"),
            "la excepción no lleva el motivo de Anthropic: $error"
        )
    }
}
