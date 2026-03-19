package com.neovita.app.auth

actual class GoogleSignInClient {
    // Uses Google Identity Services JS library via JS interop
    // Add <script src="https://accounts.google.com/gsi/client"> to index.html
    actual suspend fun signIn(): GoogleSignInResult = TODO("google.accounts.id.prompt()")
    actual suspend fun signOut() {}
}
