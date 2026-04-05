package com.neovita.app.auth

actual class GoogleSignInClient actual constructor() {
    // Uses Google Sign-In for Android SDK (com.google.android.gms:play-services-auth:21.2.0)
    // Full implementation uses Activity result API or rememberLauncherForActivityResult
    actual suspend fun signIn(): GoogleSignInResult {
        TODO("Implement with play-services-auth")
    }
    actual suspend fun signOut() {
        TODO("signOut via GoogleSignIn.getClient(...).signOut()")
    }
}
