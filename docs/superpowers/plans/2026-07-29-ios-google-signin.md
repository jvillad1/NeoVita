# iOS Google Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sign in with Google on iOS using `ASWebAuthenticationSession` + OAuth code flow with PKCE — no third-party SDK, no Swift changes — and teach the server to accept the iOS client's audience alongside the web one.

**Architecture:** Google requires a *separate* iOS OAuth client whose tokens carry a different `aud` than the Web client used by Android/web, so the server's single-audience check would reject every iOS login. `GoogleAuthService` therefore accepts a **set** of allowed audiences (web + iOS), both env-sourced. `/api/config` serves both ids; the sign-in contract carries both (`GoogleClientIds`) so each platform picks its own without any platform detection in commonMain. iOS runs the flow entirely in Kotlin/Native: PKCE verifier/challenge via `CommonCrypto`, the consent sheet via `ASWebAuthenticationSession` (callback intercepted by scheme — no `CFBundleURLTypes` needed), then a code→token exchange against Google that yields the same `id_token` the other platforms already send.

**Tech Stack:** Kotlin/Native (`platform.AuthenticationServices`, `platform.CommonCrypto`, `platform.Foundation`), Ktor Darwin client (already in `shared` iosMain), Ktor server, kotlinx.serialization.

## Global Constraints

- Kotlin 2.0.21; no new dependencies — every iOS API used ships with Kotlin/Native platform klibs.
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings Spanish. Branch: `claude/ios-signin` (already holds the three iOS build fixes). Commit here.
- New DTO fields MUST have defaults (installed-app forward compat).
- Fail-closed stays fail-closed: with NO audience configured the server must still reject every token (regression risk — there is an existing test for this).
- Never crash: every iOS failure path returns a `GoogleSignInResult` with a Spanish message.
- Android and web behavior must not change: their tokens carry the **web** `aud` and must keep working.
- iOS builds are simulator-only here (`DEVELOPMENT_TEAM` is empty); build with `CODE_SIGNING_ALLOWED=NO`.

## External setup (user-owned, blocks only the happy path)

In Google Cloud Console → Credentials, create an **iOS** OAuth client for bundle id `com.neovita.app`. Its "reversed client ID" (`com.googleusercontent.apps.<NNN>-<xxx>`) is the redirect scheme. Then set `GOOGLE_CLIENT_ID_IOS` on the server. Until then iOS shows "Google Sign-In no está configurado…" — exactly like Android before its client id existed.

---

### Task 1: Server — accept multiple audiences (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/neovita/server/services/GoogleAuthService.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/Application.kt`
- Modify: `server/src/main/resources/application.conf`, `.env.example`
- Test: `server/src/test/kotlin/com/neovita/server/services/GoogleAuthServiceTest.kt`

**Interfaces:**
- Produces: `GoogleAuthService(httpClient: HttpClient, allowedAudiences: Set<String> = emptySet())` — `verifyIdToken` accepts a token iff its `aud` is in the set; empty set still rejects everything.

- [ ] **Step 1: Write the failing tests**

In `GoogleAuthServiceTest.kt`, the existing tests construct `GoogleAuthService(client, clientId = "…")`. Update them to the new parameter and ADD these (keep every existing assertion intact — the fail-closed test especially):

```kotlin
    @Test fun `accepts a token minted for the ios client`() = runBlocking {
        val service = GoogleAuthService(
            clientReturning(HttpStatusCode.OK, realTokenInfoJson),
            allowedAudiences = setOf("ios-app.apps.googleusercontent.com", "1234.apps.googleusercontent.com")
        )
        assertEquals("ana@example.com", service.verifyIdToken("some-token")?.email)
    }

    @Test fun `still rejects an audience outside the allowed set`() = runBlocking {
        val service = GoogleAuthService(
            clientReturning(HttpStatusCode.OK, realTokenInfoJson),
            allowedAudiences = setOf("ios-app.apps.googleusercontent.com")
        )
        assertNull(service.verifyIdToken("some-token"))
    }
```

(`realTokenInfoJson` has `"aud": "1234.apps.googleusercontent.com"`, so the first test proves a multi-audience set matches on the *second* entry and the second proves a non-matching set still rejects.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.services.GoogleAuthServiceTest" --console=plain`
Expected: FAIL to compile — no `allowedAudiences` parameter

- [ ] **Step 3: Implement**

`GoogleAuthService.kt` — replace the `clientId` parameter and the check:

```kotlin
class GoogleAuthService(
    private val httpClient: HttpClient,
    // OAuth client IDs whose tokens we accept: the Web one (used by web and, via
    // setServerClientId, by Android) plus the iOS one — Google mints a different `aud`
    // for each client. Empty set = reject everything (fail closed on misconfiguration).
    private val allowedAudiences: Set<String> = emptySet()
) {
```

and inside `verifyIdToken`, replace the two audience lines:

```kotlin
        if (allowedAudiences.isEmpty()) return null
        ...
        if (info.aud !in allowedAudiences) return null
```

(keep the lenient `json`, the status check and the `runCatching` exactly as they are).

In `Application.kt`, replace the `googleService` construction:

```kotlin
    val googleClientIdIos = config.propertyOrNull("google.clientIdIos")?.getString()
    val googleService = GoogleAuthService(
        httpClient,
        allowedAudiences = setOfNotNull(
            googleClientId?.takeIf { it.isNotBlank() },
            googleClientIdIos?.takeIf { it.isNotBlank() }
        )
    )
```

In `application.conf`, inside the `google { }` block add:

```hocon
    clientIdIos = ${?GOOGLE_CLIENT_ID_IOS}   # OAuth iOS Client ID — Google emite otro `aud` para iOS
```

In `.env.example`, after the `GOOGLE_CLIENT_ID` line add:

```
GOOGLE_CLIENT_ID_IOS=1234567890-yyyy.apps.googleusercontent.com   # OAuth iOS Client ID (bundle com.neovita.app); su `aud` difiere del Web
```

- [ ] **Step 4: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL — including the pre-existing fail-closed and wrong-audience tests.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/neovita/server/services/GoogleAuthService.kt server/src/main/kotlin/com/neovita/server/Application.kt server/src/main/resources/application.conf .env.example server/src/test/kotlin/com/neovita/server/services/GoogleAuthServiceTest.kt
git commit -m "feat(server): accept both the web and iOS OAuth audiences"
```

---

### Task 2: Core/shared — serve the iOS client id and carry both ids to the platforms

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ConfigDto.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/routes/ConfigRoutes.kt`, `plugins/Routing.kt`, `Application.kt`
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/auth/GoogleSignIn.kt`
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/screens/login/LoginViewModel.kt`
- Modify: the three actuals: `shared/src/androidMain/.../GoogleSignIn.android.kt`, `shared/src/iosMain/.../GoogleSignIn.ios.kt`, `shared/src/wasmJsMain/.../GoogleSignIn.wasmJs.kt`
- Test: `core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt`

**Interfaces:**
- Produces: `WebConfigResponse.googleClientIdIos: String? = null`; `data class GoogleClientIds(val web: String? = null, val ios: String? = null)` in `com.neovita.app.auth`; `expect class GoogleSignInClient { suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult; suspend fun signOut() }`. Task 3 implements the iOS actual against this.

- [ ] **Step 1: TDD the config field**

In `ApiServiceTest.kt`, extend the `/config` mock content to include the new field and add a test:

```kotlin
    @Test fun `getConfig parses the ios client id`() = runTest {
        assertEquals("ios-client-id-456", apiService.getConfig().getOrNull()?.googleClientIdIos)
    }
```

(add `"googleClientIdIos":"ios-client-id-456"` to the `/config` mock JSON.)

Run it, see it fail, then add to `WebConfigResponse` (after `googleClientId`):

```kotlin
    val googleClientIdIos: String? = null,
```

- [ ] **Step 2: Serve it**

`ConfigRoutes.kt`: signature becomes `fun Route.configRoutes(googleClientId: String?, googleClientIdIos: String?, appConfig: AppRuntimeConfig)` and the response gains `googleClientIdIos = googleClientIdIos?.takeIf { it.isNotBlank() }`. Thread `googleClientIdIos` through `configureRouting` in `Routing.kt` (new parameter with default `null`, placed right after `googleClientId`) and pass it from `Application.kt` (the value already exists there from Task 1).

- [ ] **Step 3: Change the sign-in contract**

`GoogleSignIn.kt` becomes:

```kotlin
package com.neovita.app.auth

data class GoogleSignInResult(val idToken: String?, val error: String?)

// Google emite un OAuth client distinto por plataforma (y un `aud` distinto en el token),
// así que llevamos ambos ids y cada actual toma el suyo: sin detección de plataforma en common.
data class GoogleClientIds(val web: String? = null, val ios: String? = null)

expect class GoogleSignInClient() {
    suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult
    suspend fun signOut()
}
```

In `LoginViewModel.signInWithGoogle()`, replace the two lines that fetch the config and call `signIn`:

```kotlin
            val config = apiService.getConfig().getOrNull()
            val result = googleSignInClient.signIn(
                GoogleClientIds(web = config?.googleClientId, ios = config?.googleClientIdIos)
            )
```

(add the import for `GoogleClientIds`.)

- [ ] **Step 4: Update the three actuals**

Android (`GoogleSignIn.android.kt`): signature becomes `actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult`, and the first lines use the web id — Credential Manager's `setServerClientId` takes the **Web** id:

```kotlin
        val serverClientId = clients.web
```
(then leave the existing `serverClientId.isNullOrBlank()` check and the whole rest of the body untouched.)

wasm (`GoogleSignIn.wasmJs.kt`): signature becomes `actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult` and its first line becomes:

```kotlin
        val clientId = clients.web?.takeIf { it.isNotBlank() } ?: resolveClientId()
```
(rest of the body unchanged.)

iOS (`GoogleSignIn.ios.kt`): keep it a graceful stub for now — Task 3 replaces the body:

```kotlin
    actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult =
        GoogleSignInResult(idToken = null, error = "Google Sign-In aún no está disponible en iOS")
```

- [ ] **Step 5: Compile everything**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest :server:test :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/ConfigDto.kt core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt server/src/main/kotlin/com/neovita/server/routes/ConfigRoutes.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/main/kotlin/com/neovita/server/Application.kt shared/src/commonMain/kotlin/com/neovita/app/auth/GoogleSignIn.kt shared/src/commonMain/kotlin/com/neovita/app/screens/login/LoginViewModel.kt shared/src/androidMain/kotlin/com/neovita/app/auth/GoogleSignIn.android.kt shared/src/iosMain/kotlin/com/neovita/app/auth/GoogleSignIn.ios.kt shared/src/wasmJsMain/kotlin/com/neovita/app/auth/GoogleSignIn.wasmJs.kt
git commit -m "feat(auth): carry both web and iOS client ids through the sign-in contract"
```

---

### Task 3: iOS — ASWebAuthenticationSession + PKCE + code exchange

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/neovita/app/auth/GoogleSignIn.ios.kt` (full implementation)
- Modify: `shared/build.gradle.kts` (only if the Ktor Darwin dependency is missing from `iosMain`; check first — it should already be there)

**Interfaces:**
- Consumes: `GoogleClientIds` (Task 2), the server's iOS audience (Task 1).
- Produces: a real `id_token` for iOS, consumed unchanged by `ApiService.authenticateWithGoogle`.

- [ ] **Step 1: Implement**

`GoogleSignIn.ios.kt`:

```kotlin
package com.neovita.app.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.random.Random

// Sign-In nativo sin SDK de terceros: ASWebAuthenticationSession abre la hoja de consentimiento
// de Google y devuelve el callback por esquema (no hace falta registrar CFBundleURLTypes).
// Usamos el flujo de código con PKCE, que es el que Google exige para apps instaladas, y
// canjeamos el código por un id_token — el mismo que ya verifica el servidor.
private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

actual class GoogleSignInClient actual constructor() {

    private val http = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult {
        val clientId = clients.ios?.takeIf { it.isNotBlank() }
            ?: return GoogleSignInResult(
                idToken = null,
                error = "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID_IOS en el servidor)"
            )

        // El esquema de redirección de Google para iOS es el client id invertido.
        val scheme = reversedClientId(clientId)
        val redirectUri = "$scheme:/oauth2redirect"
        val verifier = randomCodeVerifier()
        val challenge = base64UrlSha256(verifier)

        val authUrl = "$AUTH_ENDPOINT" +
            "?client_id=$clientId" +
            "&redirect_uri=${redirectUri.encodeURLParameter()}" +
            "&response_type=code" +
            "&scope=${"openid email profile".encodeURLParameter()}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"

        val callback = presentAuthSession(authUrl, scheme)
            ?: return GoogleSignInResult(idToken = null, error = "Inicio de sesión cancelado")

        val code = queryValue(callback, "code")
            ?: return GoogleSignInResult(
                idToken = null,
                error = queryValue(callback, "error")?.let { "Google rechazó el inicio de sesión" }
                    ?: "Respuesta inesperada de Google"
            )

        return exchangeCodeForIdToken(code, verifier, clientId, redirectUri)
    }

    actual suspend fun signOut() {
        // El flujo web no deja sesión persistente en la app; no hay nada que limpiar.
    }

    private suspend fun exchangeCodeForIdToken(
        code: String, verifier: String, clientId: String, redirectUri: String
    ): GoogleSignInResult = runCatching {
        val response = http.post(TOKEN_ENDPOINT) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "code=${code.encodeURLParameter()}" +
                    "&client_id=$clientId" +
                    "&redirect_uri=${redirectUri.encodeURLParameter()}" +
                    "&grant_type=authorization_code" +
                    "&code_verifier=$verifier"
            )
        }
        val idToken = json.parseToJsonElement(response.bodyAsText())
            .jsonObject["id_token"]?.jsonPrimitive?.content
        if (idToken.isNullOrBlank()) {
            GoogleSignInResult(idToken = null, error = "Google no devolvió un token de sesión")
        } else {
            GoogleSignInResult(idToken = idToken, error = null)
        }
    }.getOrElse {
        GoogleSignInResult(idToken = null, error = "Error de conexión con Google")
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun presentAuthSession(url: String, scheme: String): String? =
        suspendCancellableCoroutine { cont ->
            val session = ASWebAuthenticationSession(
                uRL = NSURL(string = url),
                callbackURLScheme = scheme
            ) { callbackUrl, _ ->
                if (cont.isActive) cont.resume(callbackUrl?.absoluteString)
            }
            session.presentationContextProvider = AnchorProvider()
            session.prefersEphemeralWebBrowserSession = false
            if (!session.start()) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { session.cancel() }
        }
}

// ASWebAuthenticationSession exige una ventana donde presentarse.
private class AnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow!!
}

// "123-abc.apps.googleusercontent.com" -> "com.googleusercontent.apps.123-abc"
private fun reversedClientId(clientId: String): String =
    "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")

private fun randomCodeVerifier(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return (1..64).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}

@OptIn(ExperimentalForeignApi::class)
private fun base64UrlSha256(input: String): String {
    val bytes = input.encodeToByteArray()
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { inPin ->
        digest.usePinned { outPin ->
            CC_SHA256(inPin.addressOf(0), bytes.size.convert(), outPin.addressOf(0).reinterpret())
        }
    }
    val data = digest.usePinned { pin ->
        NSData.create(bytes = pin.addressOf(0), length = digest.size.convert())
    }
    return data.base64EncodedStringWithOptions(0u)
        .replace('+', '-').replace('/', '_').trimEnd('=')
}

private fun queryValue(url: String, name: String): String? =
    NSURLComponents(string = url)?.queryItems
        ?.firstOrNull { (it as? platform.Foundation.NSURLQueryItem)?.name == name }
        ?.let { (it as platform.Foundation.NSURLQueryItem).value }
```

**API-risk note (expected):** this is written against the Kotlin/Native platform klibs without having compiled. Signatures that commonly differ: the `ASWebAuthenticationSession` constructor label (`uRL`/`URL`), `CC_SHA256`'s pointer types (`reinterpret()` may be unnecessary or need `CPointer<UByteVar>`), `NSData.create(bytes=…, length=…)`, `keyWindow` deprecation, and `NSURLQueryItem` import placement. **Adapt minimally to the real API, keep the behavior identical, and report every adaptation.** If the flow cannot be made to compile after honest effort, report BLOCKED with the exact errors rather than inventing something that merely compiles.

- [ ] **Step 2: Compile the iOS target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Build the framework and the app**

Run:
```bash
export ANDROID_HOME=/usr/local/share/android-commandlinetools
./gradlew :shared:assembleComposeAppDebugXCFramework --console=plain
cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' -configuration Debug build CODE_SIGNING_ALLOWED=NO
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 4: Commit**

```bash
git add shared/src/iosMain/kotlin/com/neovita/app/auth/GoogleSignIn.ios.kt shared/build.gradle.kts
git commit -m "feat(ios): Google Sign-In via ASWebAuthenticationSession with PKCE"
```

---

### Task 4: E2E in the simulator (controller-run)

**Files:** none (verification only). Simulator `iPhone 16`, app installed from the Debug build.

- [ ] **Step 1: Not-configured path.** Server WITHOUT `GOOGLE_CLIENT_ID_IOS` → `/api/config` has no `googleClientIdIos`; tapping "Continuar con Google" in the simulator shows "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID_IOS en el servidor)" and the app stays alive.
- [ ] **Step 2: Sheet opens.** Server WITH a syntactically valid but fake `GOOGLE_CLIENT_ID_IOS` → tapping opens the `ASWebAuthenticationSession` consent sheet, which reaches Google and shows Google's own error page for the unknown client. This proves URL construction, scheme derivation and presentation work; it is the furthest the flow can go without a real client id.
- [ ] **Step 3: Cancel path.** Dismissing that sheet returns "Inicio de sesión cancelado" and the app stays alive.
- [ ] **Step 4: Server audience.** With `GOOGLE_CLIENT_ID_IOS` set, `curl -X POST /api/auth/google` with a bogus token still returns 401 `AUTH_INVALID_TOKEN` (multi-audience did not weaken verification), and with NO audiences configured it still rejects everything.
- [ ] **Step 5:** Real end-to-end login is documented as pending the user's iOS OAuth client id.
