package com.neovita.app.auth

actual class GoogleSignInClient actual constructor() {
    // Native iOS sign-in is sub-project 1b (see the 2026-07-26 strategy spec).
    actual suspend fun signIn(serverClientId: String?): GoogleSignInResult =
        GoogleSignInResult(idToken = null, error = "Google Sign-In aún no está disponible en iOS")

    actual suspend fun signOut() {}
}
