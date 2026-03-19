package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable data class ClaudeMessage(val role: String, val content: String)

class ClaudeService(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String
) {
    private val systemPrompt = """
        Eres el coach de longevidad personal de NeoVita, una IA especializada en salud y bienestar
        para adultos mayores de 45 años en Colombia. Responde siempre en español con referencias
        culturales colombianas. Sé empático, práctico y motivador. Limita respuestas a 150-200 palabras.
    """.trimIndent()

    fun streamChat(messages: List<ClaudeMessage>): Flow<String> = flow {
        client.preparePost("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(buildJsonBody(messages, stream = true))
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") return@execute
                    runCatching {
                        val json = Json.parseToJsonElement(data)
                        val text = json.jsonObject["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        if (text != null) emit(text)
                    }
                }
            }
        }
    }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int = 1024,
        val system: String,
        val messages: List<ClaudeMessage>,
        val stream: Boolean
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildJsonBody(messages: List<ClaudeMessage>, stream: Boolean): String =
        json.encodeToString(
            ClaudeRequest(
                model = model, system = systemPrompt, messages = messages, stream = stream
            )
        )
}
