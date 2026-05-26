package com.neovita.app.auth

actual class GoogleSignInClient actual constructor() {
    // Uses GoogleSignIn-iOS SDK (added via CocoaPods: pod 'GoogleSignIn', '~> 7.0')
    actual suspend fun signIn(): GoogleSignInResult = TODO("GIDSignIn.sharedInstance.signIn")
    actual suspend fun signOut() { TODO("GIDSignIn.sharedInstance.signOut()") }
}
