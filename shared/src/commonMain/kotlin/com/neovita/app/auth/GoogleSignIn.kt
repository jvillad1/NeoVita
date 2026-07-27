package com.neovita.app.auth

data class GoogleSignInResult(val idToken: String?, val error: String?)

expect class GoogleSignInClient() {
    // serverClientId: OAuth Web Client ID from /api/config; platforms that resolve it
    // themselves (wasm) use it as the preferred source and fall back to their own.
    suspend fun signIn(serverClientId: String?): GoogleSignInResult
    suspend fun signOut()
}
