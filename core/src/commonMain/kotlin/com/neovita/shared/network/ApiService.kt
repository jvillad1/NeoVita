package com.neovita.shared.network

import com.neovita.shared.network.dto.*
import com.neovita.shared.network.error.NetworkError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// The auth token is attached centrally by the HttpClient (see sharedModule's
// defaultRequest), so endpoints below no longer set the Authorization header
// per-call. 401/2xx handling also lives in the client's HttpCallValidator.
class ApiService(private val baseUrl: String, private val httpClient: HttpClient) {

    suspend fun authenticateWithGoogle(idToken: String): Result<AuthResponse> = safeCall {
        httpClient.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleAuthRequest(idToken))
        }.body()
    }

    suspend fun getConfig(): Result<WebConfigResponse> = safeCall {
        httpClient.get("$baseUrl/config").body()
    }

    suspend fun registerDeviceToken(token: String, platform: String): Result<Unit> = safeCall {
        httpClient.post("$baseUrl/devices/token") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(token, platform))
        }
        Unit
    }

    suspend fun uploadHealthMetrics(metrics: List<DailyHealthMetricDto>): Result<Unit> = safeCall {
        httpClient.post("$baseUrl/health/metrics") {
            contentType(ContentType.Application.Json)
            setBody(HealthUploadRequest(metrics))
        }
        Unit
    }

    suspend fun getHealthSummary(): Result<HealthSummaryDto> = safeCall {
        httpClient.get("$baseUrl/health/summary").body()
    }

    suspend fun getMe(): Result<UserDto> = safeCall {
        httpClient.get("$baseUrl/users/me").body()
    }

    suspend fun patchMe(name: String? = null, age: Int? = null): Result<UserDto> = safeCall {
        httpClient.patch("$baseUrl/users/me") {
            contentType(ContentType.Application.Json)
            setBody(PatchUserRequest(name, age))
        }.body()
    }

    suspend fun saveAssessment(req: AssessmentRequest): Result<AssessmentResponse> = safeCall {
        httpClient.post("$baseUrl/assessments") {
            contentType(ContentType.Application.Json); setBody(req)
        }.body()
    }

    suspend fun getLatestAssessment(): Result<AssessmentResponse> = safeCall {
        httpClient.get("$baseUrl/assessments/latest").body()
    }

    /** Equipo del empleador. El servidor exige rol EMPLOYER y responde 403 si no lo tiene. */
    suspend fun getTeam(): Result<TeamResponse> = safeCall {
        httpClient.get("$baseUrl/b2b/team").body()
    }

    suspend fun getContent(): Result<List<ContentItemDto>> = safeCall {
        httpClient.get("$baseUrl/content").body()
    }

    // The shared HttpClient runs with expectSuccess = true (see SharedModule.kt), so any
    // non-2xx status -- including 304 -- is thrown by Ktor's default validator as a
    // ResponseException before we ever get an HttpResponse back to inspect. A 304 comes
    // back as RedirectResponseException specifically (3xx range), so we catch just that
    // type and translate a Not Modified status into `null` (cache is still fresh).
    // Any other status (401/404/etc.) is a different exception and rethrows unchanged,
    // so it still reaches safeCall's existing message-based classification below.
    suspend fun getScreen(slug: String, cachedVersion: Int? = null): Result<ScreenDefinitionDto?> = safeCall {
        try {
            httpClient.get("$baseUrl/screens/$slug") {
                if (cachedVersion != null) {
                    header(HttpHeaders.IfNoneMatch, cachedVersion.toString())
                }
            }.body<ScreenDefinitionDto>()
        } catch (e: RedirectResponseException) {
            if (e.response.status == HttpStatusCode.NotModified) null else throw e
        }
    }

    // --- Content administration (EMPLOYER role; token attached by the client) ---

    suspend fun getAllContent(): Result<List<ContentItemDto>> = safeCall {
        httpClient.get("$baseUrl/content/all").body()
    }

    suspend fun createContent(req: ContentRequest): Result<ContentItemDto> = safeCall {
        httpClient.post("$baseUrl/content") {
            contentType(ContentType.Application.Json); setBody(req)
        }.body()
    }

    suspend fun updateContent(id: String, req: ContentRequest): Result<ContentItemDto> = safeCall {
        httpClient.put("$baseUrl/content/$id") {
            contentType(ContentType.Application.Json); setBody(req)
        }.body()
    }

    suspend fun deleteContent(id: String): Result<Unit> = safeCall {
        httpClient.delete("$baseUrl/content/$id"); Unit
    }

    fun streamPlanGeneration(): Flow<String> =
        sseFlow("$baseUrl/plans/generate", method = HttpMethod.Post)

    fun streamChat(messages: List<ChatMessageDto>): Flow<String> =
        sseFlow("$baseUrl/chat", method = HttpMethod.Post, body = ChatRequest(messages))

    private fun sseFlow(url: String, method: HttpMethod, body: Any? = null): Flow<String> = flow {
        httpClient.prepareRequest(url) {
            this.method = method
            body?.let {
                // Sin Content-Type, ContentNegotiation no sabe con qué serializar y Ktor
                // falla antes de salir a la red ("Fail to prepare request body for sending").
                // Era la única petición con cuerpo de este archivo que no lo declaraba, y
                // por eso el chat no funcionaba desde ningún cliente.
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            // Un evento SSE puede ocupar VARIAS líneas `data:`; se unen con \n y termina en
            // una línea en blanco. Quedarse sólo con la primera perdía todo lo que viniera
            // tras un salto de línea (listas, párrafos) y pegaba los fragmentos entre sí.
            val event = StringBuilder()
            // Hay que distinguir "el evento no ha empezado" de "el evento empezó con una
            // línea vacía": mirar si el StringBuilder está vacío confunde los dos casos y
            // perdía el salto de línea inicial, pegando dos párrafos.
            var eventoAbierto = false
            suspend fun flushEvent() {
                if (!eventoAbierto) return
                val payload = event.toString()
                event.clear()
                eventoAbierto = false
                if (payload != "[DONE]") emit(payload)
            }
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isEmpty() -> flushEvent()
                    line.startsWith("data:") -> {
                        if (eventoAbierto) event.append('\n')
                        eventoAbierto = true
                        // SSE se come UN espacio tras los dos puntos, no más.
                        event.append(line.removePrefix("data:").removePrefix(" "))
                    }
                    // Cualquier otro campo (event:, id:, retry:, comentarios) se ignora.
                }
            }
            flushEvent()   // por si el stream acaba sin línea en blanco final
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        runCatching { block() }
            .recoverCatching { e ->
                when {
                    e.message?.contains("401") == true -> throw NetworkError.Unauthorized
                    e.message?.contains("404") == true -> throw NetworkError.NotFound
                    else -> throw NetworkError.Unknown(e)
                }
            }
}
