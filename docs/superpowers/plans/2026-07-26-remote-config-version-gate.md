# Remote Config + Version Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `GET /api/config` with feature flags, per-platform minimum version, and maintenance mode, and make the clients obey it — hide flag-off features, show a full-screen "Actualiza la app" / maintenance gate — with safe defaults so a config failure never bricks the app.

**Architecture:** Server sources the values from env vars (same pattern as `GOOGLE_CLIENT_ID` in `application.conf`). The client fetches config through a small `RemoteConfigRepository` (in-memory last-good cache) at startup and every 5 minutes; a pure `evaluateGate()` function in core decides NORMAL / UPDATE_REQUIRED / MAINTENANCE and a `ConfigGate` composable wraps the app's navigation. Platform identity/version reach the gate via a `ClientInfo` value passed from each entry point.

**Tech Stack:** Kotlin Multiplatform, Ktor, kotlinx.serialization, Koin (koinInject), Compose Multiplatform, MockEngine tests.

**Spec deviation (accepted):** the spec says refresh "on each return to foreground"; Compose Multiplatform has no common lifecycle hook in this project's versions, so the plan uses startup + a 5-minute ticker + a manual "Reintentar" on the maintenance screen. Same effect, no new dependency.

## Global Constraints

- Kotlin 2.0.21; no `java.*` in commonMain; versions via `gradle/libs.versions.toml` (this plan adds NO new dependencies).
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings are Spanish.
- Branch: `claude/remote-config` (stacked on `claude/neovita-cdeac2`). Commit here.
- Backward/forward compat is the point of this feature: every new DTO field MUST have a default value, and the client's JSON parsing MUST ignore unknown keys (Task 1 fixes this).
- Web (wasm) is never version-gated (`minVersion` applies to android/ios only) — the web is always the latest deploy.
- Safe defaults on failure: no config (null) → NORMAL, everything shipped stays on. The gate only triggers on an explicit server statement.

---

### Task 1: Core — extended DTO, lenient client JSON, pure gate logic

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ConfigDto.kt`
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt:30` (the `json()` install)
- Create: `core/src/commonMain/kotlin/com/neovita/shared/config/RemoteConfigGate.kt`
- Test: `core/src/commonTest/kotlin/com/neovita/shared/config/RemoteConfigGateTest.kt`

**Interfaces:**
- Consumes: existing `WebConfigResponse(googleClientId: String?)`.
- Produces (later tasks rely on these exact signatures):
  - `WebConfigResponse(googleClientId: String? = null, features: Map<String, Boolean> = emptyMap(), minVersion: MinVersions = MinVersions(), maintenance: Boolean = false)` and `MinVersions(android: Int = 0, ios: Int = 0)` (both `@Serializable`, in the dto package)
  - `enum class AppPlatform { ANDROID, IOS, WEB }`, `data class ClientInfo(val platform: AppPlatform, val versionCode: Int)`, `enum class GateState { NORMAL, UPDATE_REQUIRED, MAINTENANCE }`
  - `fun evaluateGate(config: WebConfigResponse?, client: ClientInfo): GateState`
  - `fun WebConfigResponse?.isFeatureEnabled(key: String, default: Boolean): Boolean`
  (all in package `com.neovita.shared.config`)

- [ ] **Step 1: Write the failing tests**

`core/src/commonTest/kotlin/com/neovita/shared/config/RemoteConfigGateTest.kt`:

```kotlin
package com.neovita.shared.config

import com.neovita.shared.network.dto.MinVersions
import com.neovita.shared.network.dto.WebConfigResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteConfigGateTest {
    private val android5 = ClientInfo(AppPlatform.ANDROID, versionCode = 5)

    @Test fun `null config is safe - NORMAL`() {
        assertEquals(GateState.NORMAL, evaluateGate(null, android5))
    }

    @Test fun `maintenance wins over version gate`() {
        val cfg = WebConfigResponse(maintenance = true, minVersion = MinVersions(android = 99))
        assertEquals(GateState.MAINTENANCE, evaluateGate(cfg, android5))
    }

    @Test fun `older android build gets UPDATE_REQUIRED`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(android = 6))
        assertEquals(GateState.UPDATE_REQUIRED, evaluateGate(cfg, android5))
    }

    @Test fun `equal version passes`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(android = 5))
        assertEquals(GateState.NORMAL, evaluateGate(cfg, android5))
    }

    @Test fun `web is never version gated`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(android = 99, ios = 99))
        assertEquals(GateState.NORMAL, evaluateGate(cfg, ClientInfo(AppPlatform.WEB, 0)))
    }

    @Test fun `ios gate uses the ios minimum`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(ios = 3))
        assertEquals(GateState.UPDATE_REQUIRED, evaluateGate(cfg, ClientInfo(AppPlatform.IOS, 2)))
    }

    @Test fun `feature default applies when key absent or config null`() {
        val cfg = WebConfigResponse(features = mapOf("healthSync" to true))
        assertTrue(cfg.isFeatureEnabled("chat", default = true))
        assertFalse(cfg.isFeatureEnabled("newThing", default = false))
        assertTrue(cfg.isFeatureEnabled("healthSync", default = false))
        assertFalse((null as WebConfigResponse?).isFeatureEnabled("dormant", default = false))
        assertTrue((null as WebConfigResponse?).isFeatureEnabled("chat", default = true))
    }

    @Test fun `feature false hides a shipped feature`() {
        val cfg = WebConfigResponse(features = mapOf("chat" to false))
        assertFalse(cfg.isFeatureEnabled("chat", default = true))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.config.RemoteConfigGateTest" --console=plain`
Expected: FAIL to compile — unresolved references (`ClientInfo`, `evaluateGate`, …)

- [ ] **Step 3: Extend the DTO**

`ConfigDto.kt` becomes:

```kotlin
package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

// Public (non-secret) runtime config the server exposes to clients. Every field has a
// default: already-installed apps must keep parsing when the server adds fields.
@Serializable
data class WebConfigResponse(
    val googleClientId: String? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val minVersion: MinVersions = MinVersions(),
    val maintenance: Boolean = false
)

@Serializable
data class MinVersions(val android: Int = 0, val ios: Int = 0)
```

- [ ] **Step 4: Implement the gate logic**

`core/src/commonMain/kotlin/com/neovita/shared/config/RemoteConfigGate.kt`:

```kotlin
package com.neovita.shared.config

import com.neovita.shared.network.dto.WebConfigResponse

enum class AppPlatform { ANDROID, IOS, WEB }

data class ClientInfo(val platform: AppPlatform, val versionCode: Int)

enum class GateState { NORMAL, UPDATE_REQUIRED, MAINTENANCE }

// Pure gating decision. Safe by default: no config (fetch failed / cold start) never
// gates; only an explicit server statement does. Maintenance outranks the version gate.
// The web target is always the latest deploy, so it is never version-gated.
fun evaluateGate(config: WebConfigResponse?, client: ClientInfo): GateState {
    if (config == null) return GateState.NORMAL
    if (config.maintenance) return GateState.MAINTENANCE
    val min = when (client.platform) {
        AppPlatform.ANDROID -> config.minVersion.android
        AppPlatform.IOS -> config.minVersion.ios
        AppPlatform.WEB -> 0
    }
    return if (min > client.versionCode) GateState.UPDATE_REQUIRED else GateState.NORMAL
}

// `default` is per-feature: shipped features pass default = true (stay on when the server
// says nothing); dormant features pass default = false (off until the server enables them).
fun WebConfigResponse?.isFeatureEnabled(key: String, default: Boolean): Boolean =
    this?.features?.get(key) ?: default
```

- [ ] **Step 5: Make the client JSON lenient**

In `core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt`, replace `install(ContentNegotiation) { json() }` with:

```kotlin
// ignoreUnknownKeys: installed apps must keep working when the server (which deploys
// far more often) adds response fields — the core of the install-once strategy.
install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
```

and add the import `kotlinx.serialization.json.Json`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL (new gate tests + existing ApiServiceTest all pass)

- [ ] **Step 7: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/ConfigDto.kt core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt core/src/commonMain/kotlin/com/neovita/shared/config/RemoteConfigGate.kt core/src/commonTest/kotlin/com/neovita/shared/config/RemoteConfigGateTest.kt
git commit -m "feat(core): remote-config DTO, lenient client JSON, pure gate logic"
```

---

### Task 2: Server — env-sourced app config served by /api/config

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/config/AppRuntimeConfig.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/routes/ConfigRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/Application.kt` (wiring)
- Modify: `server/src/main/kotlin/com/neovita/server/plugins/Routing.kt` (signature)
- Modify: `server/src/main/resources/application.conf`
- Modify: `.env.example`
- Test: `server/src/test/kotlin/com/neovita/server/config/AppRuntimeConfigTest.kt`

**Interfaces:**
- Consumes: Task 1's `WebConfigResponse` / `MinVersions`.
- Produces: `data class AppRuntimeConfig(features: Map<String, Boolean>, minVersionAndroid: Int, minVersionIos: Int, maintenance: Boolean)` and `fun parseFeatures(raw: String): Map<String, Boolean>` in `com.neovita.server.config`; `configRoutes(googleClientId: String?, appConfig: AppRuntimeConfig)`.

- [ ] **Step 1: Write the failing test**

`server/src/test/kotlin/com/neovita/server/config/AppRuntimeConfigTest.kt`:

```kotlin
package com.neovita.server.config

import kotlin.test.Test
import kotlin.test.assertEquals

class AppRuntimeConfigTest {
    @Test fun `parses feature csv with spaces`() {
        assertEquals(
            mapOf("chat" to true, "healthSync" to false),
            parseFeatures("chat=true, healthSync=false")
        )
    }

    @Test fun `blank yields empty map`() {
        assertEquals(emptyMap(), parseFeatures(""))
        assertEquals(emptyMap(), parseFeatures("   "))
    }

    @Test fun `malformed entries are skipped`() {
        assertEquals(mapOf("a" to true), parseFeatures("a=true,garbage,b=,=false,c=yes"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.config.AppRuntimeConfigTest" --console=plain`
Expected: FAIL to compile — unresolved reference `parseFeatures`

- [ ] **Step 3: Implement AppRuntimeConfig**

`server/src/main/kotlin/com/neovita/server/config/AppRuntimeConfig.kt`:

```kotlin
package com.neovita.server.config

// Runtime app config sourced from env vars (see application.conf `appConfig` block).
// Changing any of these is a Railway env edit + restart — never a client release.
data class AppRuntimeConfig(
    val features: Map<String, Boolean>,
    val minVersionAndroid: Int,
    val minVersionIos: Int,
    val maintenance: Boolean
)

// "chat=true, healthSync=false" → {chat=true, healthSync=false}; malformed entries dropped.
fun parseFeatures(raw: String): Map<String, Boolean> =
    raw.split(',').mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val key = parts[0].trim()
        val value = parts[1].trim().lowercase()
        if (key.isEmpty() || value !in setOf("true", "false")) null
        else key to (value == "true")
    }.toMap()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.config.AppRuntimeConfigTest" --console=plain`
Expected: PASS

- [ ] **Step 5: Serve it from the route**

`ConfigRoutes.kt` becomes:

```kotlin
package com.neovita.server.routes

import com.neovita.server.config.AppRuntimeConfig
import com.neovita.shared.network.dto.MinVersions
import com.neovita.shared.network.dto.WebConfigResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.configRoutes(googleClientId: String?, appConfig: AppRuntimeConfig) {
    // Public config for clients (nothing here is a secret).
    get("/config") {
        call.respond(
            WebConfigResponse(
                googleClientId = googleClientId?.takeIf { it.isNotBlank() },
                features = appConfig.features,
                minVersion = MinVersions(
                    android = appConfig.minVersionAndroid,
                    ios = appConfig.minVersionIos
                ),
                maintenance = appConfig.maintenance
            )
        )
    }
}
```

- [ ] **Step 6: Wire config values in Application.kt and Routing.kt**

In `application.conf`, after the `google { ... }` block add:

```hocon
appConfig {
    features = ""             # e.g. "chat=true,healthSync=false" — see server/config/AppRuntimeConfig.kt
    features = ${?APP_FEATURES}
    minVersionAndroid = 0     # versionCode below this → "Actualiza la app" gate
    minVersionAndroid = ${?MIN_VERSION_ANDROID}
    minVersionIos = 0
    minVersionIos = ${?MIN_VERSION_IOS}
    maintenance = false
    maintenance = ${?MAINTENANCE_MODE}
}
```

In `Application.kt`, right after the `googleClientId` line, add (plus imports `com.neovita.server.config.AppRuntimeConfig` and `com.neovita.server.config.parseFeatures`):

```kotlin
val appConfig = AppRuntimeConfig(
    features = parseFeatures(config.propertyOrNull("appConfig.features")?.getString() ?: ""),
    minVersionAndroid = config.propertyOrNull("appConfig.minVersionAndroid")?.getString()?.toIntOrNull() ?: 0,
    minVersionIos = config.propertyOrNull("appConfig.minVersionIos")?.getString()?.toIntOrNull() ?: 0,
    maintenance = config.propertyOrNull("appConfig.maintenance")?.getString()?.toBoolean() ?: false
)
```

and pass it as the last argument: `configureRouting(..., googleClientId, appConfig)`.

In `Routing.kt`, add the parameter and pass it through (plus import `com.neovita.server.config.AppRuntimeConfig`):

```kotlin
    googleClientId: String? = null,
    appConfig: AppRuntimeConfig = AppRuntimeConfig(emptyMap(), 0, 0, false)
```

and change the route registration to `configRoutes(googleClientId, appConfig)`.

In `.env.example`, after the `GOOGLE_CLIENT_ID` line add:

```
APP_FEATURES=chat=true                # feature flags "k=v,k=v"; omit = defaults (shipped features on)
MIN_VERSION_ANDROID=0                 # versionCode below this shows the update gate
MIN_VERSION_IOS=0
MAINTENANCE_MODE=false                # true = maintenance screen on all clients
```

- [ ] **Step 7: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/com/neovita/server/config/AppRuntimeConfig.kt server/src/test/kotlin/com/neovita/server/config/AppRuntimeConfigTest.kt server/src/main/kotlin/com/neovita/server/routes/ConfigRoutes.kt server/src/main/kotlin/com/neovita/server/Application.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/main/resources/application.conf .env.example
git commit -m "feat(server): env-sourced feature flags, min versions and maintenance in /api/config"
```

---

### Task 3: Core — RemoteConfigRepository (last-good in-memory cache)

**Files:**
- Create: `core/src/commonMain/kotlin/com/neovita/shared/config/RemoteConfigRepository.kt`
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt` (register in Koin)
- Test: `core/src/commonTest/kotlin/com/neovita/shared/config/RemoteConfigRepositoryTest.kt`

**Interfaces:**
- Consumes: `ApiService.getConfig(): Result<WebConfigResponse>`.
- Produces: `class RemoteConfigRepository(apiService: ApiService)` with `val config: StateFlow<WebConfigResponse?>` and `suspend fun refresh()`; Koin `single { RemoteConfigRepository(get()) }`.

- [ ] **Step 1: Write the failing test**

`core/src/commonTest/kotlin/com/neovita/shared/config/RemoteConfigRepositoryTest.kt`:

```kotlin
package com.neovita.shared.config

import com.neovita.shared.network.ApiService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteConfigRepositoryTest {
    private var failNow = false
    private val engine = MockEngine { _ ->
        if (failNow) respond("boom", HttpStatusCode.InternalServerError)
        else respond(
            """{"maintenance":true,"unknownFutureField":123}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    private val repo = RemoteConfigRepository(
        ApiService(
            baseUrl = "http://localhost",
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
    )

    @Test fun `starts empty and stores fetched config`() = runTest {
        assertNull(repo.config.value)
        repo.refresh()
        assertTrue(repo.config.value!!.maintenance)
    }

    @Test fun `failed refresh keeps last good config`() = runTest {
        repo.refresh()
        val good = repo.config.value
        failNow = true
        repo.refresh()
        assertEquals(good, repo.config.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.config.RemoteConfigRepositoryTest" --console=plain`
Expected: FAIL to compile — unresolved reference `RemoteConfigRepository`

- [ ] **Step 3: Implement the repository**

`core/src/commonMain/kotlin/com/neovita/shared/config/RemoteConfigRepository.kt`:

```kotlin
package com.neovita.shared.config

import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteConfigRepository(private val apiService: ApiService) {
    private val _config = MutableStateFlow<WebConfigResponse?>(null)
    val config: StateFlow<WebConfigResponse?> = _config.asStateFlow()

    // Failure keeps the last good config (null on a cold start, which evaluateGate treats
    // as NORMAL) — a network error must never gate the app.
    suspend fun refresh() {
        apiService.getConfig().onSuccess { _config.value = it }
    }
}
```

- [ ] **Step 4: Register in Koin**

In `SharedModule.kt`, after `single { ApiService(baseUrl, get()) }` add (plus import `com.neovita.shared.config.RemoteConfigRepository`):

```kotlin
single { RemoteConfigRepository(get()) }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, all pass

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/config/RemoteConfigRepository.kt core/src/commonTest/kotlin/com/neovita/shared/config/RemoteConfigRepositoryTest.kt core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt
git commit -m "feat(core): RemoteConfigRepository with last-good in-memory cache"
```

---

### Task 4: Shared UI — ConfigGate, gate screens, ClientInfo from every entry point

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/App.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/app/config/ConfigGate.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/app/config/GateScreens.kt`
- Modify: `androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt` (one line)
- Modify: `shared/src/iosMain/kotlin/com/neovita/app/MainViewController.kt`

**Interfaces:**
- Consumes: `ClientInfo`, `AppPlatform`, `GateState`, `evaluateGate` (Task 1); `RemoteConfigRepository` via Koin (Task 3).
- Produces: `App(baseUrl: String, cache: LocalCache? = null, clientInfo: ClientInfo = ClientInfo(AppPlatform.WEB, 0))` — Task 5 relies on the gate being active inside Koin context.

There is no test infra in `:shared`; the gates here are compiling all three targets plus Task 5's on-emulator verification.

- [ ] **Step 1: Wrap navigation in the gate**

`App.kt` becomes:

```kotlin
package com.neovita.app

import androidx.compose.runtime.Composable
import com.neovita.app.config.ConfigGate
import com.neovita.app.navigation.AppNavigation
import com.neovita.app.ui.theme.NeoVitaTheme
import com.neovita.app.di.appModule
import com.neovita.shared.config.AppPlatform
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.di.sharedModule
import org.koin.compose.KoinApplication

@Composable
fun App(
    baseUrl: String = "http://localhost:8080",
    cache: LocalCache? = null,
    clientInfo: ClientInfo = ClientInfo(AppPlatform.WEB, 0)
) {
    KoinApplication(application = { modules(sharedModule(baseUrl, cache), appModule) }) {
        NeoVitaTheme {
            // Remote-config gate: maintenance / forced-update screens take over the UI
            // when the server says so; otherwise normal navigation.
            ConfigGate(clientInfo) {
                // Start screen + forced-logout handling derive from the persisted session.
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 2: The gate composable**

`shared/src/commonMain/kotlin/com/neovita/app/config/ConfigGate.kt`:

```kotlin
package com.neovita.app.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.config.GateState
import com.neovita.shared.config.RemoteConfigRepository
import com.neovita.shared.config.evaluateGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.minutes

@Composable
fun ConfigGate(clientInfo: ClientInfo, content: @Composable () -> Unit) {
    val repo = koinInject<RemoteConfigRepository>()
    val config by repo.config.collectAsState()
    val scope = rememberCoroutineScope()

    // Startup fetch + 5-minute ticker (no common lifecycle hook in this stack; the
    // ticker also picks up "maintenance over" without user action).
    LaunchedEffect(Unit) {
        while (true) {
            repo.refresh()
            delay(5.minutes)
        }
    }

    when (evaluateGate(config, clientInfo)) {
        GateState.MAINTENANCE -> MaintenanceScreen(onRetry = { scope.launch { repo.refresh() } })
        GateState.UPDATE_REQUIRED -> UpdateRequiredScreen()
        GateState.NORMAL -> content()
    }
}
```

- [ ] **Step 3: The gate screens**

`shared/src/commonMain/kotlin/com/neovita/app/config/GateScreens.kt`:

```kotlin
package com.neovita.app.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun UpdateRequiredScreen() {
    GateScaffold(
        title = "Actualiza la app",
        message = "Hay una nueva versión de NeoVita. Actualízala desde la tienda para continuar."
    )
}

@Composable
internal fun MaintenanceScreen(onRetry: () -> Unit) {
    GateScaffold(
        title = "Estamos en mantenimiento",
        message = "Volvemos en unos minutos. Gracias por tu paciencia."
    ) {
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun GateScaffold(title: String, message: String, extra: @Composable () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            extra()
        }
    }
}
```

- [ ] **Step 4: Android entry point passes its version**

In `MainActivity.kt`, change the `App(...)` call to (plus imports `com.neovita.app.android.BuildConfig` — already imported — `com.neovita.shared.config.AppPlatform`, `com.neovita.shared.config.ClientInfo`):

```kotlin
App(
    baseUrl = BuildConfig.SERVER_URL + "/api",
    cache = cache,
    clientInfo = ClientInfo(AppPlatform.ANDROID, BuildConfig.VERSION_CODE)
)
```

- [ ] **Step 5: iOS entry point passes its version**

`MainViewController.kt` becomes:

```kotlin
package com.neovita.app

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.neovita.shared.config.AppPlatform
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase
import platform.Foundation.NSBundle

fun MainViewController() = ComposeUIViewController {
    val driver = NativeSqliteDriver(NeoVitaDatabase.Schema, "neovita.db")
    val cache = SqlDelightLocalCache(NeoVitaDatabase(driver))
    // CFBundleVersion is the iOS build number (int by convention in this project).
    val buildNumber = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
        ?.toIntOrNull() ?: 0
    App(
        baseUrl = "http://localhost:8080/api",
        cache = cache,
        clientInfo = ClientInfo(AppPlatform.IOS, buildNumber)
    )
}
```

(The wasm entry point needs no change: the `App` default is `ClientInfo(AppPlatform.WEB, 0)`.)

- [ ] **Step 6: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/neovita/app/App.kt shared/src/commonMain/kotlin/com/neovita/app/config/ConfigGate.kt shared/src/commonMain/kotlin/com/neovita/app/config/GateScreens.kt androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt shared/src/iosMain/kotlin/com/neovita/app/MainViewController.kt
git commit -m "feat(app): remote-config gate with update-required and maintenance screens"
```

---

### Task 5: Feature-flag demo (Chat tab) + emulator verification

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/screens/main/MainScreen.kt` (the `NeoBottomBar` nav items)

**Interfaces:**
- Consumes: `RemoteConfigRepository` (Koin), `isFeatureEnabled` (Task 1).

- [ ] **Step 1: Gate the Chat tab behind the "chat" flag**

In `MainScreen.kt`, inside `NeoBottomBar()`, replace the `navItems` list with (plus imports `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue`, `com.neovita.shared.config.RemoteConfigRepository`, `com.neovita.shared.config.isFeatureEnabled`, `org.koin.compose.koinInject`):

```kotlin
    // "chat" is a shipped feature: default true (visible unless the server disables it).
    val config by koinInject<RemoteConfigRepository>().config.collectAsState()
    val navItems = buildList {
        add(NavItem(HomeTab, "Inicio", Icons.Filled.Home))
        if (config.isFeatureEnabled("chat", default = true)) {
            add(NavItem(ChatTab, "Coach", Icons.Filled.MailOutline))
        }
        add(NavItem(PlanTab, "Plan", Icons.Filled.DateRange))
        add(NavItem(ProfileTab, "Perfil", Icons.Filled.Person))
    }
```

- [ ] **Step 2: Build and install on the emulator**

```bash
export ANDROID_HOME=/usr/local/share/android-commandlinetools
./gradlew :androidApp:assembleDebug --console=plain
$ANDROID_HOME/platform-tools/adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Expected: BUILD SUCCESSFUL, install Success. (Emulator `Pixel_8_Pro` must be booted; boot takes ~60s.)

- [ ] **Step 3: Verify the three gates end-to-end**

For each scenario: start the server with the env vars shown, `adb shell am force-stop com.neovita.app`, `adb shell am start -n com.neovita.app/com.neovita.app.MainActivity`, screenshot with `adb exec-out screencap -p > /tmp/gate.png`, kill the server between scenarios. Base env for every run: `DB_URL="jdbc:postgresql://localhost:5432/neovita?user=carolinarestrepo" JWT_SECRET="local-dev-secret-at-least-32-characters-long" CLAUDE_API_KEY="sk-ant-dummy-local"`.

1. `MAINTENANCE_MODE=true ./gradlew :server:run` → app shows "Estamos en mantenimiento" + Reintentar.
2. `MIN_VERSION_ANDROID=2 ./gradlew :server:run` → app shows "Actualiza la app" (installed versionCode is 1).
3. `APP_FEATURES="chat=false" ./gradlew :server:run` → tap "Ahora no" on login → bottom bar shows Inicio/Plan/Perfil, NO "Coach" tab.
4. No extra env vars → app behaves exactly as before (login flow, all 4 tabs).

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/neovita/app/screens/main/MainScreen.kt
git commit -m "feat(app): gate Chat tab behind the chat feature flag"
```

---

## Self-review notes

- Spec coverage: features/minVersion/maintenance served and obeyed; safe defaults (null config → NORMAL, per-feature defaults); web never gated; startup+ticker replaces foreground-refresh (documented deviation). `webScreens` from the spec's payload example is sub-project 3, intentionally absent here.
- The `ignoreUnknownKeys` client fix (Task 1 Step 5) is load-bearing for the whole install-once strategy: without it, every future server-side DTO addition breaks installed binaries.
- `LoginViewModel` keeps calling `ApiService.getConfig()` directly (not the repository): it wants a fresh fetch at tap-time and predates this plan; unifying is a later cleanup, out of scope.
