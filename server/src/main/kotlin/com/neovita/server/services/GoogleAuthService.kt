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
    // OAuth client IDs whose tokens we accept: the Web one (used by web and, via
    // setServerClientId, by Android) plus the iOS one — Google mints a different `aud`
    // for each client. Empty set = reject everything (fail closed on misconfiguration).
    private val allowedAudiences: Set<String> = emptySet()
) {
    // tokeninfo returns ~15 fields (iss, azp, exp, picture, ...) — parse leniently
    private val json = Json { ignoreUnknownKeys = true }

    // Calls Google's tokeninfo endpoint to verify the ID token
    suspend fun verifyIdToken(idToken: String): GoogleUserInfo? = runCatching {
        if (allowedAudiences.isEmpty()) return null
        val response = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }
        if (!response.status.isSuccess()) return null
        val info = json.decodeFromString<GoogleUserInfo>(response.bodyAsText())
        if (info.aud !in allowedAudiences) return null
        info
    }.getOrNull()
}
