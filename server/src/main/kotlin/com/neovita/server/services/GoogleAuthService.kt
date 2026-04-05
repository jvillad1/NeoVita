package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class GoogleUserInfo(val sub: String, val email: String, val name: String)

class GoogleAuthService(private val httpClient: HttpClient) {
    // Calls Google's tokeninfo endpoint to verify the ID token
    suspend fun verifyIdToken(idToken: String): GoogleUserInfo? = runCatching {
        httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }.body<GoogleUserInfo>()
    }.getOrNull()
}
