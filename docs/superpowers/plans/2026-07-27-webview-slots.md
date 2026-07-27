# WebView Slots via SDUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Server-defined SDUI cards can open an in-app WebView (`OPEN_WEBVIEW` action), so new HTML screens (content, surveys, promos) deploy with a server push and appear inside the installed apps.

**Architecture:** Extends the SDUI taxonomy from PR #7 (`ScreenDto.kt`) with a third action type, `OPEN_WEBVIEW`, validated like the others (https absolute or same-origin relative path). A new `PlatformWebView` expect/actual (Android `WebView`, iOS `WKWebView`, wasm opens a new tab) renders inside a Voyager `WebContentScreen` pushed over the tabs. The session JWT is attached as an `Authorization` header on the initial request. Relative URLs resolve against a new Koin-provided `ServerOrigin` (baseUrl minus `/api`).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.7.3 (`androidx.compose.ui.viewinterop`), Voyager, Koin, Ktor. NO new dependencies.

**Spec deviation (approved by the user 2026-07-27):** the strategy spec's `webScreens[]` list in `/api/config` is replaced by the SDUI `OPEN_WEBVIEW` action — one server-driven-entries mechanism instead of two, reusing PR #7's screens infrastructure.

## Global Constraints

- Kotlin 2.0.21; no `java.*` in commonMain; no new dependencies.
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings are Spanish.
- Branch: `claude/webview-slots` (off current main). Commit here.
- Follow the SDUI forward-compat pattern exactly: old clients must strip an `OPEN_WEBVIEW` action (unknown type → `isValidAction` false → action stripped) without crashing — that already works, do not break it.
- Security: a relative `OPEN_WEBVIEW` target must be same-origin — accept `/path` but REJECT protocol-relative `//host` (it would resolve to an external origin).
- WebView slots are for secondary/content surfaces (spec scope guard) — core flows stay native.

---

### Task 1: Core — `OPEN_WEBVIEW` in the SDUI taxonomy (TDD)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt` (ScreenTaxonomy + isValidAction)
- Test: `core/src/commonTest/kotlin/com/neovita/shared/network/dto/ScreenDtoTest.kt`

**Interfaces:**
- Produces: `"OPEN_WEBVIEW"` in `ScreenTaxonomy.ACTION_TYPES`; `isValidAction` accepts `OPEN_WEBVIEW` with target `https://…` or same-origin relative (`/…` but not `//…`). Consumed by Task 3's renderer case.

- [ ] **Step 1: Write the failing test**

Append to `ScreenDtoTest.kt` (inside the class):

```kotlin
    @Test
    fun open_webview_actions_validate_https_and_same_origin_relative() {
        val def = ScreenDefinitionDto("s", 1, listOf(
            SectionDto(type = "CARD_LIST", cards = listOf(
                CardDto(title = "a", action = ActionDto("OPEN_WEBVIEW", "https://neovita.app/promo")), // válida
                CardDto(title = "b", action = ActionDto("OPEN_WEBVIEW", "/web/demo")),                  // válida (same-origin)
                CardDto(title = "c", action = ActionDto("OPEN_WEBVIEW", "//evil.com/x")),               // protocol-relative → fuera
                CardDto(title = "d", action = ActionDto("OPEN_WEBVIEW", "http://insecure")),            // http absoluto → fuera
                CardDto(title = "e", action = ActionDto("OPEN_WEBVIEW", "web/demo")),                   // relativa sin / → fuera
            )),
        ))
        val cards = renderableSections(def)[0].cards
        assertEquals("https://neovita.app/promo", cards[0].action?.target)
        assertEquals("/web/demo", cards[1].action?.target)
        assertNull(cards[2].action)
        assertNull(cards[3].action)
        assertNull(cards[4].action)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.network.dto.ScreenDtoTest" --console=plain`
Expected: FAIL — the three "válida" assertions fail because `OPEN_WEBVIEW` is stripped as unknown.

- [ ] **Step 3: Extend the taxonomy**

In `ScreenDto.kt`, change `ACTION_TYPES` to:

```kotlin
    val ACTION_TYPES = listOf("NAVIGATE", "OPEN_URL", "OPEN_WEBVIEW")
```

and add a branch to `isValidAction` (keep the existing two):

```kotlin
    // In-app WebView: https absoluto, o ruta same-origin ("/x" pero nunca "//host", que
    // resolvería a un origen externo).
    "OPEN_WEBVIEW" -> a.target.startsWith("https://") ||
        (a.target.startsWith("/") && !a.target.startsWith("//"))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, all pass (including the pre-existing strip tests)

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt core/src/commonTest/kotlin/com/neovita/shared/network/dto/ScreenDtoTest.kt
git commit -m "feat(core): OPEN_WEBVIEW action in SDUI taxonomy (https or same-origin relative)"
```

---

### Task 2: Shared — PlatformWebView (expect/actual) + WebContentScreen + ServerOrigin

**Files:**
- Create: `core/src/commonMain/kotlin/com/neovita/shared/di/ServerOrigin.kt`
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt` (register ServerOrigin)
- Create: `shared/src/commonMain/kotlin/com/neovita/app/ui/web/PlatformWebView.kt`
- Create: `shared/src/androidMain/kotlin/com/neovita/app/ui/web/PlatformWebView.android.kt`
- Create: `shared/src/iosMain/kotlin/com/neovita/app/ui/web/PlatformWebView.ios.kt`
- Create: `shared/src/wasmJsMain/kotlin/com/neovita/app/ui/web/PlatformWebView.wasmJs.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/app/screens/web/WebContentScreen.kt`

**Interfaces:**
- Consumes: `SessionManager.token` (core), Koin.
- Produces: `data class WebContentScreen(val title: String, val url: String) : Screen` (package `com.neovita.app.screens.web`) — Task 3 pushes it; `ServerOrigin(value: String)` Koin single.

There is no test infra in `:shared`; the gate is compiling all three targets.

- [ ] **Step 1: ServerOrigin in core**

`core/src/commonMain/kotlin/com/neovita/shared/di/ServerOrigin.kt`:

```kotlin
package com.neovita.shared.di

// Server origin (baseUrl without the /api suffix) for resolving same-origin relative
// URLs (e.g. SDUI OPEN_WEBVIEW targets like "/web/promo"). On the web target baseUrl
// is "/api", so the origin is "" and relative URLs stay same-origin naturally.
data class ServerOrigin(val value: String)
```

In `SharedModule.kt`, after `single { RemoteConfigRepository(get()) }` add (plus import `— nothing, same package`):

```kotlin
    single { ServerOrigin(baseUrl.removeSuffix("/api")) }
```

- [ ] **Step 2: The expect declaration**

`shared/src/commonMain/kotlin/com/neovita/app/ui/web/PlatformWebView.kt`:

```kotlin
package com.neovita.app.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// In-app web renderer for SDUI OPEN_WEBVIEW slots. The session JWT is attached as an
// Authorization header on the INITIAL request only (subresource requests don't carry it) —
// enough for server-rendered pages; SPAs served here must not rely on that header.
@Composable
expect fun PlatformWebView(url: String, modifier: Modifier)
```

- [ ] **Step 3: Android actual**

`PlatformWebView.android.kt`:

```kotlin
package com.neovita.app.ui.web

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.neovita.shared.session.SessionManager

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                // Keep navigation inside the WebView instead of launching the browser.
                webViewClient = WebViewClient()
                val headers = SessionManager.token
                    ?.let { mapOf("Authorization" to "Bearer $it") }
                    ?: emptyMap()
                loadUrl(url, headers)
            }
        }
    )
}
```

- [ ] **Step 4: iOS actual**

`PlatformWebView.ios.kt`:

```kotlin
package com.neovita.app.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.neovita.shared.session.SessionManager
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.WebKit.WKWebView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        factory = {
            val webView = WKWebView()
            NSURL.URLWithString(url)?.let { nsUrl ->
                val request = NSMutableURLRequest(uRL = nsUrl)
                SessionManager.token?.let {
                    request.setValue("Bearer $it", forHTTPHeaderField = "Authorization")
                }
                webView.loadRequest(request)
            }
            webView
        }
    )
}
```

(If `platform.Foundation.setValue` doesn't resolve, the method is `request.setValue(value, forHTTPHeaderField = ...)` directly on `NSMutableURLRequest` — drop the import and keep the call.)

- [ ] **Step 5: wasm actual (new tab — the web IS already the web)**

`PlatformWebView.wasmJs.kt`:

```kotlin
package com.neovita.app.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun openInNewTab(url: String): Unit = js("{ window.open(url, '_blank'); }")

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    // The wasm app already runs in a browser: open the page in a new tab (popup blockers
    // may require the explicit button if the automatic attempt is suppressed).
    LaunchedEffect(url) { openInNewTab(url) }
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Este contenido se abre en una pestaña nueva.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { openInNewTab(url) }) { Text("Abrir de nuevo") }
    }
}
```

- [ ] **Step 6: WebContentScreen**

`shared/src/commonMain/kotlin/com/neovita/app/screens/web/WebContentScreen.kt`:

```kotlin
package com.neovita.app.screens.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.ui.web.PlatformWebView
import com.neovita.shared.di.ServerOrigin
import org.koin.compose.koinInject

// A server-deployed HTML page rendered in-app: the "deploy web into the installed app"
// slot of the install-once strategy. Secondary surfaces only — core flows stay native.
data class WebContentScreen(val title: String, val url: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val origin = koinInject<ServerOrigin>().value
        val resolvedUrl = if (url.startsWith("http")) url else origin + url

        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
            PlatformWebView(resolvedUrl, Modifier.fillMaxWidth().weight(1f))
        }
    }
}
```

- [ ] **Step 7: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/di/ServerOrigin.kt core/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt shared/src/commonMain/kotlin/com/neovita/app/ui/web/PlatformWebView.kt shared/src/androidMain/kotlin/com/neovita/app/ui/web/PlatformWebView.android.kt shared/src/iosMain/kotlin/com/neovita/app/ui/web/PlatformWebView.ios.kt shared/src/wasmJsMain/kotlin/com/neovita/app/ui/web/PlatformWebView.wasmJs.kt shared/src/commonMain/kotlin/com/neovita/app/screens/web/WebContentScreen.kt
git commit -m "feat(app): in-app WebView (PlatformWebView + WebContentScreen) with session header"
```

---

### Task 3: Shared — wire OPEN_WEBVIEW through SduiRenderer and DashboardScreen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/ui/sdui/SduiRenderer.kt` (both `SduiRenderer` and private `SduiSection`)
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/screens/dashboard/DashboardScreen.kt` (the SDUI branch of `Content()`)

**Interfaces:**
- Consumes: Task 1's validated `OPEN_WEBVIEW` actions; Task 2's `WebContentScreen`.
- Produces: `SduiRenderer(..., onOpenWebView: (title: String, url: String) -> Unit)` — any future SDUI screen host must pass it.

- [ ] **Step 1: Add the callback to the renderer**

In `SduiRenderer.kt`:
- Add parameter `onOpenWebView: (String, String) -> Unit,` after `onOpenUrl` in BOTH `SduiRenderer` and `SduiSection`, and pass it through the `SduiSection(...)` call site.
- In the `onCardClick` lambda's `when (action.type)` add:

```kotlin
                    "OPEN_WEBVIEW" -> onOpenWebView(card.title, action.target)
```

- [ ] **Step 2: Wire the dashboard**

In `DashboardScreen.kt` `Content()`:
- Add `val navigator = LocalNavigator.currentOrThrow` next to the existing `val tabNavigator = ...` (imports: `cafe.adriel.voyager.navigator.LocalNavigator`, `cafe.adriel.voyager.navigator.currentOrThrow`, `com.neovita.app.screens.web.WebContentScreen`).
- In the `SduiRenderer(...)` call, after `onOpenUrl = ...` add:

```kotlin
                        onOpenWebView = { title, url ->
                            // Push over the tabs (same parent-navigator pattern as ProfileScreen).
                            navigator.parent?.push(WebContentScreen(title, url))
                        },
```

- [ ] **Step 3: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/neovita/app/ui/sdui/SduiRenderer.kt shared/src/commonMain/kotlin/com/neovita/app/screens/dashboard/DashboardScreen.kt
git commit -m "feat(app): OPEN_WEBVIEW cards push WebContentScreen from the SDUI dashboard"
```

---

### Task 4: Server — demo page under /web (TDD)

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/routes/WebRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/plugins/Routing.kt` (register OUTSIDE `/api`, before `staticResources`)
- Test: `server/src/test/kotlin/com/neovita/server/routes/WebRoutesTest.kt`

**Interfaces:**
- Produces: `GET /web/demo` — public HTML page (Spanish) that reports whether an `Authorization: Bearer` header arrived (proves the WebView header injection E2E).

- [ ] **Step 1: Write the failing test**

`WebRoutesTest.kt` (standalone routing test — no DB needed):

```kotlin
package com.neovita.server.routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebRoutesTest {

    @Test
    fun `demo page renders without session`() = testApplication {
        application { routing { webRoutes() } }
        val response = client.get("/web/demo")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("NeoVita"))
        assertTrue(body.contains("Sesión: no detectada"))
    }

    @Test
    fun `demo page detects the session header`() = testApplication {
        application { routing { webRoutes() } }
        val response = client.get("/web/demo") {
            header(HttpHeaders.Authorization, "Bearer some-jwt")
        }
        assertTrue(response.bodyAsText().contains("Sesión: activa"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.WebRoutesTest" --console=plain`
Expected: FAIL to compile — unresolved reference `webRoutes`

- [ ] **Step 3: Implement the route**

`WebRoutes.kt`:

```kotlin
package com.neovita.server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Server-rendered pages for the in-app WebView slots (SDUI OPEN_WEBVIEW). Deploying a
// new page here + pointing an SDUI card at it ships a "new screen" to installed apps
// with zero store releases. Public by design; pages needing the user read the
// Authorization header the WebView attaches on its initial request.
fun Route.webRoutes() {
    get("/web/demo") {
        val hasSession = call.request.headers[HttpHeaders.Authorization]
            ?.startsWith("Bearer ") == true
        val sessionLabel = if (hasSession) "Sesión: activa" else "Sesión: no detectada"
        call.respondText(
            """
            <!DOCTYPE html>
            <html lang="es"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>NeoVita — Demo</title>
            <style>
                body { font-family: system-ui, sans-serif; margin: 0; padding: 48px 24px;
                       background: #7A1F3D; color: #fff; text-align: center; }
                .card { background: #fff; color: #333; border-radius: 16px; padding: 32px 24px;
                        max-width: 420px; margin: 0 auto; }
                .badge { display: inline-block; margin-top: 16px; padding: 6px 14px;
                         border-radius: 999px; background: #F3E6EC; color: #7A1F3D; }
            </style></head>
            <body>
                <div class="card">
                    <h1>Hola desde la web 🎉</h1>
                    <p>Esta pantalla vive en el servidor de <strong>NeoVita</strong>:
                       se actualiza con un deploy, sin tocar las tiendas.</p>
                    <span class="badge">$sessionLabel</span>
                </div>
            </body></html>
            """.trimIndent(),
            ContentType.Text.Html
        )
    }
}
```

- [ ] **Step 4: Register the route**

In `Routing.kt`, inside `routing { ... }`, right after the `route("/api") { ... }` block closes (and before `staticResources`), add:

```kotlin
        // Server-rendered pages for in-app WebView slots — outside /api, before the
        // static catch-all.
        webRoutes()
```

- [ ] **Step 5: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all pass

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/com/neovita/server/routes/WebRoutes.kt server/src/test/kotlin/com/neovita/server/routes/WebRoutesTest.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt
git commit -m "feat(server): /web/demo page for in-app WebView slots"
```

---

### Task 5: E2E on the emulator (controller-run)

**Files:** none (verification only).

The SDUI dashboard requires a JWT (`/api/screens/dashboard` is authenticated), and real Google login is still blocked on the OAuth client ID. The dev-only path (no code changes):

- [ ] **Step 1:** Craft an HS256 JWT with the dev secret (`local-dev-secret-at-least-32-characters-long`, iss `neovita`, aud `neovita-app`, claims `userId`/`role`, future `exp`) via a Python script; insert a matching user row in Postgres if `/api/users/me` needs one.
- [ ] **Step 2:** Inject the token into the installed debug app's session store: multiplatform-settings on Android = default SharedPreferences → `adb shell run-as com.neovita.app` write `auth_token` into `shared_prefs/com.neovita.app_preferences.xml` (app force-stopped first).
- [ ] **Step 3:** Point an SDUI card at the demo page: `psql -d neovita` → `UPDATE screen_definitions SET sections_json = <json with an added CARD_ROW "Novedades" card action {"type":"OPEN_WEBVIEW","target":"/web/demo"}>, version = version + 1 WHERE slug = 'dashboard';`
- [ ] **Step 4:** Server up (base dev env vars), app launch → lands on MainScreen (token present) → SDUI dashboard shows the "Novedades" card → tap → `WebContentScreen` with top bar + back arrow renders the demo page showing **"Sesión: activa"** (header injection verified). Back arrow returns to the dashboard with tab state intact.
- [ ] **Step 5:** Negative check — old-client simulation: the `renderableSections` strip test (Task 1) already covers "client without OPEN_WEBVIEW support ignores the card"; no manual step needed.
