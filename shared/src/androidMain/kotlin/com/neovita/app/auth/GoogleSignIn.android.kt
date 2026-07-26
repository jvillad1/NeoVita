package com.neovita.app.auth

actual class GoogleSignInClient actual constructor() {
    actual suspend fun signIn(serverClientId: String?): GoogleSignInResult =
        GoogleSignInResult(idToken = null, error = "Google Sign-In aún no está implementado en Android")

    actual suspend fun signOut() {}
}
