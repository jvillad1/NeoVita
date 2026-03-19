package com.neovita.shared.network

import com.neovita.shared.network.dto.*
import com.neovita.shared.network.error.NetworkError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ApiService(private val baseUrl: String, private val httpClient: HttpClient) {

    private var token: String? = null
    fun setToken(t: String) { token = t }

    suspend fun authenticateWithGoogle(idToken: String): Result<AuthResponse> = safeCall {
        httpClient.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleAuthRequest(idToken))
        }.body()
    }

    suspend fun getMe(): Result<UserDto> = safeCall {
        httpClient.get("$baseUrl/users/me") { bearerAuth() }.body()
    }

    suspend fun patchMe(name: String? = null, age: Int? = null): Result<UserDto> = safeCall {
        httpClient.patch("$baseUrl/users/me") {
            bearerAuth(); contentType(ContentType.Application.Json)
            setBody(PatchUserRequest(name, age))
        }.body()
    }

    suspend fun saveAssessment(req: AssessmentRequest): Result<AssessmentResponse> = safeCall {
        httpClient.post("$baseUrl/assessments") {
            bearerAuth(); contentType(ContentType.Application.Json); setBody(req)
        }.body()
    }

    suspend fun getLatestAssessment(): Result<AssessmentResponse> = safeCall {
        httpClient.get("$baseUrl/assessments/latest") { bearerAuth() }.body()
    }

    fun streamPlanGeneration(): Flow<String> =
        sseFlow("$baseUrl/plans/generate", method = HttpMethod.Post)

    fun streamChat(messages: List<ChatMessageDto>): Flow<String> =
        sseFlow("$baseUrl/chat", method = HttpMethod.Post, body = ChatRequest(messages))

    private fun sseFlow(url: String, method: HttpMethod, body: Any? = null): Flow<String> = flow {
        httpClient.prepareRequest(url) {
            this.method = method
            bearerAuth()
            body?.let { setBody(it) }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ") && line != "data: [DONE]") {
                    emit(line.removePrefix("data: "))
                }
            }
        }
    }

    private fun HttpRequestBuilder.bearerAuth() {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
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
