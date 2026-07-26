# Android Native Google Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `TODO()` in the Android `GoogleSignInClient` with a real Credential Manager sign-in that produces a Google id_token the existing server flow verifies.

**Architecture:** The OAuth Web Client ID flows from the server (`GET /api/config`, already implemented) → new `ApiService.getConfig()` → `LoginViewModel` → `signIn(serverClientId)` on each platform's `GoogleSignInClient`. Android uses androidx Credential Manager + the `googleid` library, which needs the foreground Activity (exposed via a small holder object registered by `MainActivity`).

**Tech Stack:** Kotlin Multiplatform, androidx.credentials 1.3.0, com.google.android.libraries.identity.googleid 1.1.1, Ktor client, MockEngine for tests.

## Global Constraints

- Kotlin 2.0.21; `jvmTarget = 17`; compileSdk 35, minSdk 26.
- All dependency versions go in `gradle/libs.versions.toml`; reference via `libs.*` (existing inline `androidx.activity` deps are legacy — don't imitate for new deps).
- No `java.*` / `System.currentTimeMillis()` in commonMain.
- User-facing strings are Spanish (match existing: "Inicio de sesión cancelado", "…no está configurado…").
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- The wasm target must keep compiling (`:webApp:compileKotlinWasmJs`) after every contract change.
- `GetGoogleIdOption.setServerClientId(...)` takes the **Web** client ID (the same one the server checks as `aud`) — never an Android-type client ID.

## External setup (user-owned, not a code task)

In Google Cloud Console → APIs & Services → Credentials, the project needs **two** OAuth client IDs:
1. **Web application** — its ID is what `GOOGLE_CLIENT_ID` (server env) and `setServerClientId` use.
2. **Android** — package `com.neovita.app` + debug SHA-1. Get the SHA-1 with:
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
   Without this, Credential Manager returns `NoCredentialException`/error 10 (DEVELOPER_ERROR) at runtime even though the code is correct.

---

### Task 1: `ApiService.getConfig()` (core)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt`
- Test: `core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt`

**Interfaces:**
- Consumes: `WebConfigResponse(googleClientId: String?)` — already exists in `core/.../network/dto/ConfigDto.kt`.
- Produces: `suspend fun getConfig(): Result<WebConfigResponse>` on `ApiService` — Task 2's `LoginViewModel` calls this.

- [ ] **Step 1: Write the failing test**

In `ApiServiceTest.kt`, add a route to the existing `MockEngine` `when` block and a test:

```kotlin
// inside the when (request.url.encodedPath) block, before the else branch:
"/config" -> respond(
    content = """{"googleClientId":"web-client-id-123"}""",
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json")
)
```

```kotlin
@Test fun `getConfig returns google client id`() = runTest {
    val result = apiService.getConfig()
    assertTrue(result.isSuccess)
    assertEquals("web-client-id-123", result.getOrNull()?.googleClientId)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.network.ApiServiceTest" --console=plain`
Expected: FAIL to compile — "unresolved reference: getConfig"

- [ ] **Step 3: Implement `getConfig`**

In `ApiService.kt`, after `authenticateWithGoogle` (the DTO import is covered by the existing `dto.*` wildcard):

```kotlin
suspend fun getConfig(): Result<WebConfigResponse> = safeCall {
    httpClient.get("$baseUrl/config").body()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.network.ApiServiceTest" --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt
git commit -m "feat(core): ApiService.getConfig() for server-provided web config"
```

---

### Task 2: Thread the client ID through the common contract

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/auth/GoogleSignIn.kt`
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/screens/login/LoginViewModel.kt:30-37`
- Modify: `shared/src/wasmJsMain/kotlin/com/neovita/app/auth/GoogleSignIn.wasmJs.kt` (the `signIn` signature + first lines)
- Modify: `shared/src/iosMain/kotlin/com/neovita/app/auth/GoogleSignIn.ios.kt`
- Modify: `shared/src/androidMain/kotlin/com/neovita/app/auth/GoogleSignIn.android.kt`

**Interfaces:**
- Consumes: `ApiService.getConfig(): Result<WebConfigResponse>` from Task 1.
- Produces: `expect class GoogleSignInClient { suspend fun signIn(serverClientId: String?): GoogleSignInResult; suspend fun signOut() }` — Task 4 implements the Android actual against exactly this signature.

There is no commonTest infra in `:shared`; this task's verification is compiling every target (which enforces expect/actual signature match).

- [ ] **Step 1: Change the expect declaration**

`GoogleSignIn.kt` becomes:

```kotlin
package com.neovita.app.auth

data class GoogleSignInResult(val idToken: String?, val error: String?)

expect class GoogleSignInClient() {
    // serverClientId: OAuth Web Client ID from /api/config; platforms that resolve it
    // themselves (wasm) use it as the preferred source and fall back to their own.
    suspend fun signIn(serverClientId: String?): GoogleSignInResult
    suspend fun signOut()
}
```

- [ ] **Step 2: Fetch the config in LoginViewModel**

In `LoginViewModel.signInWithGoogle()`, replace `val result = googleSignInClient.signIn()` with:

```kotlin
val clientId = apiService.getConfig().getOrNull()?.googleClientId
val result = googleSignInClient.signIn(clientId)
```

(A failed config fetch yields `null`; each platform then reports its "no está configurado" error.)

- [ ] **Step 3: Update the wasm actual**

In `GoogleSignIn.wasmJs.kt`, change the `signIn` signature and prefer the passed ID:

```kotlin
actual suspend fun signIn(serverClientId: String?): GoogleSignInResult {
    val clientId = serverClientId?.takeIf { it.isNotBlank() } ?: resolveClientId()
    if (clientId.isBlank()) {
        return GoogleSignInResult(
            idToken = null,
            error = "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID en el servidor)"
        )
    }
```

The rest of the function body (the `waitForGis()` check onward) stays exactly as is; keep `resolveClientId()` as the fallback.

- [ ] **Step 4: Update the iOS actual (graceful, no more TODO crash)**

`GoogleSignIn.ios.kt` becomes:

```kotlin
package com.neovita.app.auth

actual class GoogleSignInClient actual constructor() {
    // Native iOS sign-in is sub-project 1b (see the 2026-07-26 strategy spec).
    actual suspend fun signIn(serverClientId: String?): GoogleSignInResult =
        GoogleSignInResult(idToken = null, error = "Google Sign-In aún no está disponible en iOS")

    actual suspend fun signOut() {}
}
```

- [ ] **Step 5: Update the Android actual to compile (still unimplemented)**

`GoogleSignIn.android.kt` becomes (Task 4 replaces the body):

```kotlin
package com.neovita.app.auth

actual class GoogleSignInClient actual constructor() {
    actual suspend fun signIn(serverClientId: String?): GoogleSignInResult =
        GoogleSignInResult(idToken = null, error = "Google Sign-In aún no está implementado en Android")

    actual suspend fun signOut() {}
}
```

- [ ] **Step 6: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL (all three targets)

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/neovita/app/auth/GoogleSignIn.kt shared/src/commonMain/kotlin/com/neovita/app/screens/login/LoginViewModel.kt shared/src/wasmJsMain/kotlin/com/neovita/app/auth/GoogleSignIn.wasmJs.kt shared/src/iosMain/kotlin/com/neovita/app/auth/GoogleSignIn.ios.kt shared/src/androidMain/kotlin/com/neovita/app/auth/GoogleSignIn.android.kt
git commit -m "refactor(auth): thread server client ID through GoogleSignInClient.signIn"
```

---

### Task 3: Android scaffolding — dependencies, Activity holder, device-friendly server URL

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts:58-62` (androidMain dependencies)
- Create: `shared/src/androidMain/kotlin/com/neovita/app/auth/CurrentActivityHolder.kt`
- Modify: `androidApp/build.gradle.kts`
- Modify: `androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `CurrentActivityHolder.activity: Activity?` (set while MainActivity is alive) and the `androidx.credentials`/`googleid` classes on the classpath — Task 4 consumes both. Also `BuildConfig.SERVER_URL` in `com.neovita.app.android`.

- [ ] **Step 1: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml` under `[versions]`:

```toml
androidx-credentials = "1.3.0"
googleid = "1.1.1"
```

Under `[libraries]`:

```toml
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "androidx-credentials" }
androidx-credentials-play-services = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "androidx-credentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleid" }
```

- [ ] **Step 2: Add them to shared androidMain**

In `shared/build.gradle.kts`, inside `androidMain.dependencies {}`:

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services)
implementation(libs.googleid)
```

- [ ] **Step 3: Create the Activity holder**

`shared/src/androidMain/kotlin/com/neovita/app/auth/CurrentActivityHolder.kt`:

```kotlin
package com.neovita.app.auth

import android.app.Activity

// Credential Manager shows its account picker over the foreground Activity;
// MainActivity registers itself here (and clears on destroy).
object CurrentActivityHolder {
    @Volatile
    var activity: Activity? = null
}
```

- [ ] **Step 4: Make the server URL a BuildConfig field**

In `androidApp/build.gradle.kts`, inside `android { defaultConfig { ... } }` add:

```kotlin
// Emulator default; physical device: ./gradlew :androidApp:assembleDebug -PserverUrl=http://<LAN-IP>:8080
val serverUrl = (project.findProperty("serverUrl") as String?) ?: "http://10.0.2.2:8080"
buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
```

and inside `android { ... }` (sibling of `defaultConfig`):

```kotlin
buildFeatures { buildConfig = true }
```

- [ ] **Step 5: Register the Activity and use the BuildConfig URL**

`MainActivity.kt` becomes:

```kotlin
package com.neovita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.neovita.app.android.BuildConfig
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CurrentActivityHolder.activity = this
        val driver = AndroidSqliteDriver(NeoVitaDatabase.Schema, this, "neovita.db")
        val cache = SqlDelightLocalCache(NeoVitaDatabase(driver))
        setContent {
            App(baseUrl = BuildConfig.SERVER_URL + "/api", cache = cache)
        }
    }

    override fun onDestroy() {
        if (CurrentActivityHolder.activity === this) CurrentActivityHolder.activity = null
        super.onDestroy()
    }
}
```

- [ ] **Step 6: Allow cleartext HTTP for local-server testing**

In `AndroidManifest.xml`, add to the `<application>` tag (dev convenience; production uses the https Railway URL, where this flag is irrelevant):

```xml
android:usesCleartextTraffic="true"
```

- [ ] **Step 7: Build**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts shared/src/androidMain/kotlin/com/neovita/app/auth/CurrentActivityHolder.kt androidApp/build.gradle.kts androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt androidApp/src/main/AndroidManifest.xml
git commit -m "feat(android): Credential Manager deps, Activity holder, configurable server URL"
```

---

### Task 4: Android `GoogleSignInClient` with Credential Manager

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/neovita/app/auth/GoogleSignIn.android.kt`

**Interfaces:**
- Consumes: `CurrentActivityHolder.activity` (Task 3), `signIn(serverClientId: String?)` contract (Task 2).
- Produces: the working Android sign-in; `LoginViewModel` needs no further changes.

- [ ] **Step 1: Full implementation**

`GoogleSignIn.android.kt` becomes:

```kotlin
package com.neovita.app.auth

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

actual class GoogleSignInClient actual constructor() {

    actual suspend fun signIn(serverClientId: String?): GoogleSignInResult {
        val activity = CurrentActivityHolder.activity
            ?: return GoogleSignInResult(idToken = null, error = "No hay una pantalla activa")
        if (serverClientId.isNullOrBlank()) {
            return GoogleSignInResult(
                idToken = null,
                error = "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID en el servidor)"
            )
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleSignInResult(idToken = idToken, error = null)
            } else {
                GoogleSignInResult(idToken = null, error = "Credencial inesperada de Google")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult(idToken = null, error = "Inicio de sesión cancelado")
        } catch (e: NoCredentialException) {
            GoogleSignInResult(idToken = null, error = "No hay cuentas de Google en este dispositivo")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult(idToken = null, error = "No se pudo leer la credencial de Google")
        } catch (e: GetCredentialException) {
            GoogleSignInResult(idToken = null, error = "Error al iniciar sesión con Google")
        }
    }

    actual suspend fun signOut() {
        val activity = CurrentActivityHolder.activity ?: return
        CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
    }
}
```

- [ ] **Step 2: Build**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/androidMain/kotlin/com/neovita/app/auth/GoogleSignIn.android.kt
git commit -m "feat(android): Google Sign-In via Credential Manager"
```

---

### Task 5: On-device verification (manual E2E)

**Files:** none (verification only). Partially blocked on the real Web Client ID + Android OAuth client (see "External setup").

- [ ] **Step 1: Start the server with a client ID**

```bash
DB_URL="jdbc:postgresql://localhost:5432/neovita?user=carolinarestrepo" JWT_SECRET="local-dev-secret-at-least-32-characters-long" CLAUDE_API_KEY="sk-ant-dummy-local" GOOGLE_CLIENT_ID="<REAL-WEB-CLIENT-ID>" ./gradlew :server:run
```

- [ ] **Step 2: Build against the Mac's LAN IP and install**

```bash
export ANDROID_HOME=/usr/local/share/android-commandlinetools
LAN_IP=$(ipconfig getifaddr en0)
./gradlew :androidApp:assembleDebug -PserverUrl="http://$LAN_IP:8080"
$ANDROID_HOME/platform-tools/adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

(Emulator instead of device: omit `-PserverUrl`, default `10.0.2.2` applies. Requires phone and Mac on the same Wi-Fi.)

- [ ] **Step 3: Verify the flow**

1. Open NeoVita → "Continuar con Google" → the Google account picker sheet appears.
2. Pick an account → app navigates past login (new users → onboarding).
3. Negative check — server without `GOOGLE_CLIENT_ID`: tap shows "Google Sign-In no está configurado…".
4. Negative check — dismiss the picker: "Inicio de sesión cancelado".
5. Server log shows `POST /api/auth/google` → 200; a wrong-`aud` token → 401 AUTH_INVALID_TOKEN.

- [ ] **Step 4: Commit any fixes found; otherwise nothing to commit**

---

## Self-review notes

- Spec coverage: implements strategy sub-project 1 (Android half); iOS half is intentionally a follow-up plan — the iOS actual is downgraded from crash-on-tap to graceful error here.
- The `LoginViewModel` change adds one network call before sign-in; on config failure the flow degrades to the platform "no configurado" message rather than blocking.
- `filterByAuthorizedAccounts=false` deliberately shows all device accounts on first sign-in (simplest first-run UX; the two-step authorized-first dance is a later optimization).
