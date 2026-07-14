package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GoogleUserInfo(val sub: String, val email: String, val name: String, val aud: String? = null)

class GoogleAuthService(
    private val httpClient: HttpClient,
    // OAuth Web Client ID; when set, tokens minted for other apps are rejected (aud check)
    private val clientId: String? = null
) {
    // tokeninfo returns ~15 fields (iss, azp, exp, picture, ...) — parse leniently
    private val json = Json { ignoreUnknownKeys = true }

    // Calls Google's tokeninfo endpoint to verify the ID token
    suspend fun verifyIdToken(idToken: String): GoogleUserInfo? = runCatching {
        val response = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }
        if (!response.status.isSuccess()) return null
        val info = json.decodeFromString<GoogleUserInfo>(response.bodyAsText())
        if (!clientId.isNullOrBlank() && info.aud != clientId) return null
        info
    }.getOrNull()
}
