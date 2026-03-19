package com.neovita.app.auth

data class GoogleSignInResult(val idToken: String?, val error: String?)

expect class GoogleSignInClient {
    suspend fun signIn(): GoogleSignInResult
    suspend fun signOut()
}
