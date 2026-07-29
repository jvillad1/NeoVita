package com.neovita.app.auth

data class GoogleSignInResult(val idToken: String?, val error: String?)

// Google emite un OAuth client distinto por plataforma (y un `aud` distinto en el token),
// así que llevamos ambos ids y cada actual toma el suyo: sin detección de plataforma en common.
data class GoogleClientIds(val web: String? = null, val ios: String? = null)

expect class GoogleSignInClient() {
    suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult
    suspend fun signOut()
}
