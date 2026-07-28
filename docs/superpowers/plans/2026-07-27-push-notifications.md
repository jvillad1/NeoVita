# Push Notifications (Android, server-activated) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship push notifications dormant in the Android binary: Firebase initializes at runtime from `/api/config`, the server registers device tokens and can send `{title, body, target}` pushes whose tap opens a `WebContentScreen` — all activatable later via Railway env vars with zero releases.

**Architecture:** The Firebase *client* values (apiKey/appId/projectId/senderId — public, normally baked into every APK) are served by `/api/config`; the app calls `FirebaseApp.initializeApp` at runtime when the config arrives AND the `push` feature flag (default off) is on. No `google-services.json`, no google-services Gradle plugin. Server side: `device_tokens` table + authenticated registration endpoint, `PushService` on firebase-admin (disabled with a clear log when `FIREBASE_SERVICE_ACCOUNT` is absent), and an EMPLOYER-only `POST /api/push/test`. Messages are **data-only** so our `FirebaseMessagingService` always builds the notification and the tap contract stays binary-stable.

**Tech Stack:** firebase-admin 9.3.0 (server), firebase-messaging 24.1.0 + androidx.core-ktx 1.13.1 (Android), Exposed/H2 test harness (mirrors ScreenRoutesTest), kotlinx.serialization.

## Global Constraints

- Kotlin 2.0.21; versions via `gradle/libs.versions.toml`; NO google-services Gradle plugin anywhere.
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings Spanish. Branch: `claude/push-notifications`. Commit here.
- New DTO fields MUST have defaults (installed-app forward compat).
- Dormant-safety: with no Firebase config served, or the `push` flag off, or invalid values — the app must behave exactly as today (no crash, no log spam beyond one warn).
- The `push` feature flag uses `isFeatureEnabled("push", default = false)` — ship-dormant semantics.
- Push `target` follows the OPEN_WEBVIEW-style rules: `/relative` or `https://` absolute; anything else is ignored (open app only).

## External setup (user-owned, later — NOT needed for this plan)

Create a Firebase project (console.firebase.google.com) with an Android app (`com.neovita.app`); then set Railway/local env vars: `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` (from project settings) and `FIREBASE_SERVICE_ACCOUNT` (the service-account JSON, one line). Delivery E2E is blocked until then; everything else verifies now.

---

### Task 1: Firebase client config served by /api/config (core + server, TDD)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ConfigDto.kt`
- Modify: `core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt` (the `/config` mock + a test)
- Modify: `server/src/main/kotlin/com/neovita/server/config/AppRuntimeConfig.kt`
- Test: `server/src/test/kotlin/com/neovita/server/config/AppRuntimeConfigTest.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/routes/ConfigRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/Application.kt`
- Modify: `server/src/main/resources/application.conf`
- Modify: `.env.example`

**Interfaces:**
- Produces: `FirebaseClientConfig(apiKey, appId, projectId, senderId)` (@Serializable, dto package) and `WebConfigResponse.firebase: FirebaseClientConfig? = null`; `fun firebaseConfigFrom(apiKey: String?, appId: String?, projectId: String?, senderId: String?): FirebaseClientConfig?` in `com.neovita.server.config`; `AppRuntimeConfig.firebase: FirebaseClientConfig?`. Task 4 consumes the DTO.

- [ ] **Step 1: Write the failing tests**

In `AppRuntimeConfigTest.kt` add (import `com.neovita.shared.network.dto.FirebaseClientConfig`):

```kotlin
    @Test fun `firebase config requires all four values`() {
        assertEquals(
            FirebaseClientConfig("k", "a", "p", "s"),
            firebaseConfigFrom("k", "a", "p", "s")
        )
        assertEquals(null, firebaseConfigFrom(null, "a", "p", "s"))
        assertEquals(null, firebaseConfigFrom("k", "", "p", "s"))
        assertEquals(null, firebaseConfigFrom("k", "a", "  ", "s"))
        assertEquals(null, firebaseConfigFrom(null, null, null, null))
    }
```

In `ApiServiceTest.kt`, change the `/config` mock response content to:

```kotlin
                content = """{"googleClientId":"web-client-id-123","firebase":{"apiKey":"AIza","appId":"1:2:android:3","projectId":"neovita-x","senderId":"99"}}""",
```

and add:

```kotlin
    @Test fun `getConfig parses firebase client config`() = runTest {
        val firebase = apiService.getConfig().getOrNull()?.firebase
        assertEquals("neovita-x", firebase?.projectId)
        assertEquals("99", firebase?.senderId)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.config.AppRuntimeConfigTest" --console=plain`
Expected: FAIL to compile — unresolved `firebaseConfigFrom` / `FirebaseClientConfig`

- [ ] **Step 3: Implement the DTO**

In `ConfigDto.kt`, add `val firebase: FirebaseClientConfig? = null` as the last `WebConfigResponse` field, and below `MinVersions`:

```kotlin
// Firebase *client* values (the same ones google-services.json bakes into every APK —
// public, not secrets). Served remotely so push can activate on installed apps with a
// Railway env change instead of a store release. Null = push stays dormant.
@Serializable
data class FirebaseClientConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val senderId: String
)
```

- [ ] **Step 4: Implement the server side**

In `AppRuntimeConfig.kt` add the field `val firebase: FirebaseClientConfig?` (last constructor param, import the dto) and:

```kotlin
// All four values or nothing: a partial Firebase config would fail at runtime on devices.
fun firebaseConfigFrom(apiKey: String?, appId: String?, projectId: String?, senderId: String?): FirebaseClientConfig? =
    if (apiKey.isNullOrBlank() || appId.isNullOrBlank() || projectId.isNullOrBlank() || senderId.isNullOrBlank()) null
    else FirebaseClientConfig(apiKey.trim(), appId.trim(), projectId.trim(), senderId.trim())
```

In `Application.kt`, extend the `AppRuntimeConfig(...)` construction with (import `com.neovita.server.config.firebaseConfigFrom`):

```kotlin
        firebase = firebaseConfigFrom(
            config.propertyOrNull("appConfig.firebaseApiKey")?.getString(),
            config.propertyOrNull("appConfig.firebaseAppId")?.getString(),
            config.propertyOrNull("appConfig.firebaseProjectId")?.getString(),
            config.propertyOrNull("appConfig.firebaseSenderId")?.getString()
        )
```

In `Routing.kt` the default value `AppRuntimeConfig(emptyMap(), 0, 0, false)` becomes `AppRuntimeConfig(emptyMap(), 0, 0, false, null)`.

In `ConfigRoutes.kt` add `firebase = appConfig.firebase` to the `WebConfigResponse(...)`.

In `application.conf`, inside the `appConfig { }` block add:

```hocon
    firebaseApiKey = ${?FIREBASE_API_KEY}         # Firebase client values (console → project settings);
    firebaseAppId = ${?FIREBASE_APP_ID}           # all four present => installed apps activate push,
    firebaseProjectId = ${?FIREBASE_PROJECT_ID}   # no release needed. Not secrets.
    firebaseSenderId = ${?FIREBASE_SENDER_ID}
```

In `.env.example`, after `MAINTENANCE_MODE` add:

```
FIREBASE_API_KEY=                     # Firebase client config (4 values, console → project settings → general);
FIREBASE_APP_ID=                      # all set => apps activate push at runtime. Not secrets.
FIREBASE_PROJECT_ID=
FIREBASE_SENDER_ID=
FIREBASE_SERVICE_ACCOUNT=             # service-account JSON (one line) — THIS one is a secret; enables sending
```

- [ ] **Step 5: Run both suites**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test :core:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, all pass

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/ConfigDto.kt core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt server/src/main/kotlin/com/neovita/server/config/AppRuntimeConfig.kt server/src/test/kotlin/com/neovita/server/config/AppRuntimeConfigTest.kt server/src/main/kotlin/com/neovita/server/routes/ConfigRoutes.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/main/kotlin/com/neovita/server/Application.kt server/src/main/resources/application.conf .env.example
git commit -m "feat(config): Firebase client values served by /api/config (runtime push activation)"
```

---

### Task 2: Device-token registration (server, TDD with the H2 harness)

**Files:**
- Create: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/PushDto.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/tables/DeviceTokensTable.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/repositories/DeviceTokenRepository.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/DeviceRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/db/DatabaseFactory.kt` (add table)
- Modify: `server/src/main/kotlin/com/neovita/server/plugins/Routing.kt` + `server/src/main/kotlin/com/neovita/server/Application.kt` (wire repo + route)
- Test: `server/src/test/kotlin/com/neovita/server/routes/DeviceRoutesTest.kt`

**Interfaces:**
- Produces: `RegisterDeviceRequest(token: String, platform: String)` (@Serializable, dto); `DeviceTokenRepository` with `upsert(token: String, userId: String, platform: String)`, `tokensForUser(userId: String): List<String>`, `allTokens(): List<String>`; `POST /api/devices/token` (auth, 204). Task 3 consumes the repository; Task 4 consumes the DTO/endpoint.

- [ ] **Step 1: Write the failing test**

`DeviceRoutesTest.kt` — mirror `ScreenRoutesTest`'s harness (same `testSecret`, `jwtService`, `testConfig(dbName)` helper copied verbatim; unique H2 db names `devices_test_*`):

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.tables.DeviceTokensTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret, issuer = "neovita", audience = "neovita-app", expirationMs = 3600_000L
    )

    private fun testConfig(dbName: String) = MapApplicationConfig(
        "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "database.driver" to "org.h2.Driver",
        "jwt.secret" to testSecret,
        "jwt.issuer" to "neovita",
        "jwt.audience" to "neovita-app",
        "jwt.expirationMs" to "3600000",
        "claude.apiKey" to "dummy-key",
        "claude.model" to "dummy-model",
    )

    @Test
    fun `registers and upserts a device token`() = testApplication {
        environment { config = testConfig("devices_test_upsert") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        repeat(2) {
            val response = client.post("/api/devices/token") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"token":"fcm-abc","platform":"android"}""")
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }
        val rows = transaction { DeviceTokensTable.selectAll().count() }
        assertEquals(1, rows)
    }

    @Test
    fun `rejects blank token`() = testApplication {
        environment { config = testConfig("devices_test_blank") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")
        val response = client.post("/api/devices/token") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"token":"  ","platform":"android"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `requires auth`() = testApplication {
        environment { config = testConfig("devices_test_401") }
        application { module() }
        val response = client.post("/api/devices/token")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.DeviceRoutesTest" --console=plain`
Expected: FAIL to compile — unresolved `DeviceTokensTable`

- [ ] **Step 3: Implement**

`PushDto.kt` (core dto):

```kotlin
package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(val token: String, val platform: String)
```

`DeviceTokensTable.kt`:

```kotlin
package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object DeviceTokensTable : Table("device_tokens") {
    val token = varchar("token", 512)
    val userId = varchar("user_id", 64)
    val platform = varchar("platform", 16)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(token)
}
```

`DeviceTokenRepository.kt`:

```kotlin
package com.neovita.server.db.repositories

import com.neovita.server.db.tables.DeviceTokensTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class DeviceTokenRepository {

    fun upsert(token: String, userId: String, platform: String) = transaction {
        val now = System.currentTimeMillis()
        val updated = DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
            it[DeviceTokensTable.userId] = userId
            it[DeviceTokensTable.platform] = platform
            it[updatedAt] = now
        }
        if (updated == 0) {
            DeviceTokensTable.insert {
                it[DeviceTokensTable.token] = token
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.platform] = platform
                it[updatedAt] = now
            }
        }
    }

    fun tokensForUser(userId: String): List<String> = transaction {
        DeviceTokensTable.selectAll().where { DeviceTokensTable.userId eq userId }
            .map { it[DeviceTokensTable.token] }
    }

    fun allTokens(): List<String> = transaction {
        DeviceTokensTable.selectAll().map { it[DeviceTokensTable.token] }
    }
}
```

`DeviceRoutes.kt`:

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.repositories.DeviceTokenRepository
import com.neovita.shared.network.dto.RegisterDeviceRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.deviceRoutes(repo: DeviceTokenRepository) {
    authenticate("jwt-auth") {
        // The app re-registers on every activation/token rotation; upsert keeps one row per device.
        post("/devices/token") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<RegisterDeviceRequest>()
            if (req.token.isBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            repo.upsert(req.token.trim(), userId, req.platform.trim().lowercase())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
```

`DatabaseFactory.kt`: add `DeviceTokensTable` to the imports and to `createMissingTablesAndColumns(...)`.

`Application.kt`: `val deviceTokenRepo = DeviceTokenRepository()` next to the other repos; pass as a new `configureRouting` argument. `Routing.kt`: add parameter `deviceTokenRepo: DeviceTokenRepository = DeviceTokenRepository()` and register `deviceRoutes(deviceTokenRepo)` inside the `/api` block after `screenRoutes(...)`.

- [ ] **Step 4: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all pass

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/PushDto.kt server/src/main/kotlin/com/neovita/server/db/tables/DeviceTokensTable.kt server/src/main/kotlin/com/neovita/server/db/repositories/DeviceTokenRepository.kt server/src/main/kotlin/com/neovita/server/routes/DeviceRoutes.kt server/src/main/kotlin/com/neovita/server/db/DatabaseFactory.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/main/kotlin/com/neovita/server/Application.kt server/src/test/kotlin/com/neovita/server/routes/DeviceRoutesTest.kt
git commit -m "feat(server): device-token registration (device_tokens + POST /api/devices/token)"
```

---

### Task 3: PushService (firebase-admin) + POST /api/push/test (server, TDD)

**Files:**
- Modify: `gradle/libs.versions.toml` (+ `server/build.gradle.kts`)
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/PushDto.kt` (add send DTOs)
- Create: `server/src/main/kotlin/com/neovita/server/services/PushService.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/PushRoutes.kt`
- Modify: `server/src/main/resources/application.conf`, `Application.kt`, `Routing.kt`
- Test: `server/src/test/kotlin/com/neovita/server/routes/PushRoutesTest.kt`

**Interfaces:**
- Consumes: `DeviceTokenRepository` (Task 2).
- Produces: `PushSendRequest(title, body, target: String? = null, userId: String? = null)` and `PushSendResponse(sent: Int)` (dto); `PushService(serviceAccountJson: String?)` with `val enabled: Boolean` and `fun send(tokens: List<String>, title: String, body: String, target: String?): Int`; `POST /api/push/test` (EMPLOYER; 503 `PUSH_DISABLED` when no credentials).

- [ ] **Step 1: Add the dependency**

`libs.versions.toml`: `firebase-admin = "9.3.0"` under `[versions]`; under `[libraries]`: `firebase-admin = { module = "com.google.firebase:firebase-admin", version.ref = "firebase-admin" }`. In `server/build.gradle.kts`: `implementation(libs.firebase.admin)`.

- [ ] **Step 2: Write the failing test**

`PushRoutesTest.kt` (same harness helper as `DeviceRoutesTest` — copy `testSecret`/`jwtService`/`testConfig` verbatim, db names `push_test_*`). Note: `requireRole` reads the role from the DB, so tests must create the user row and set the role directly:

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.db.tables.UsersTable
import com.neovita.server.module
import com.neovita.server.services.JwtService
import com.neovita.server.services.PushService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushRoutesTest {

    private val testSecret = "test-secret-that-is-long-enough-32chars"
    private val jwtService = JwtService(
        secret = testSecret, issuer = "neovita", audience = "neovita-app", expirationMs = 3600_000L
    )

    private fun testConfig(dbName: String) = MapApplicationConfig(
        "database.url" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "database.driver" to "org.h2.Driver",
        "jwt.secret" to testSecret,
        "jwt.issuer" to "neovita",
        "jwt.audience" to "neovita-app",
        "jwt.expirationMs" to "3600000",
        "claude.apiKey" to "dummy-key",
        "claude.model" to "dummy-model",
    )

    /** Creates a user and promotes it to EMPLOYER; returns its id. */
    private fun employer(): String {
        val user = UserRepository().upsert("admin@test.dev", "Admin")
        transaction { UsersTable.update({ UsersTable.id eq user.id }) { it[role] = "EMPLOYER" } }
        return user.id
    }

    @Test
    fun `push test returns 503 when sending is not configured`() = testApplication {
        environment { config = testConfig("push_test_disabled") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")
        val response = client.post("/api/push/test") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hola","body":"Prueba"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("PUSH_DISABLED"))
    }

    @Test
    fun `push test requires the EMPLOYER role`() = testApplication {
        environment { config = testConfig("push_test_role") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("user@test.dev", "User")
        val token = jwtService.generateToken(user.id, "USER")
        val response = client.post("/api/push/test") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hola","body":"Prueba"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `disabled service reports zero sends without touching the network`() {
        val service = PushService(serviceAccountJson = null)
        assertEquals(false, service.enabled)
        assertEquals(0, service.send(listOf("t1", "t2"), "Hola", "Prueba", null))
    }
}
```

(If `UsersTable`'s role column property or `UserRepository.upsert` signature differ, adapt the `employer()` helper to the actual API — check `server/.../db/tables/UsersTable.kt` and `UserRepository.kt` first; keep the intent: a persisted EMPLOYER user. If `requireRole` responds with a status other than 403 for non-admins, assert that actual status and note it in your report.)

- [ ] **Step 3: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.PushRoutesTest" --console=plain`
Expected: FAIL to compile — unresolved `PushService`

- [ ] **Step 4: Implement**

Add to `PushDto.kt`:

```kotlin
@Serializable
data class PushSendRequest(
    val title: String,
    val body: String,
    val target: String? = null,   // "/web/x" o "https://…" — abre WebContentScreen al tocar
    val userId: String? = null    // null = todos los dispositivos registrados
)

@Serializable
data class PushSendResponse(val sent: Int)
```

`PushService.kt`:

```kotlin
package com.neovita.server.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import org.slf4j.LoggerFactory

// Sends data-only FCM messages: the app's FirebaseMessagingService always builds the
// notification itself, so the tap contract {title, body, target} stays binary-stable.
// Without FIREBASE_SERVICE_ACCOUNT the service is disabled (routes answer PUSH_DISABLED).
class PushService(private val serviceAccountJson: String?) {

    private val log = LoggerFactory.getLogger(PushService::class.java)
    val enabled: Boolean = !serviceAccountJson.isNullOrBlank()

    private val messaging: FirebaseMessaging? by lazy {
        if (!enabled) null
        else runCatching {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountJson!!.byteInputStream()))
                .build()
            val app = FirebaseApp.getApps().firstOrNull { it.name == "neovita-push" }
                ?: FirebaseApp.initializeApp(options, "neovita-push")
            FirebaseMessaging.getInstance(app)
        }.onFailure { log.warn("Push deshabilitado: credenciales inválidas", it) }.getOrNull()
    }

    /** Sends to each token individually; returns how many were accepted by FCM. */
    fun send(tokens: List<String>, title: String, body: String, target: String?): Int {
        val fm = messaging ?: return 0
        var sent = 0
        tokens.forEach { token ->
            runCatching {
                val builder = Message.builder()
                    .setToken(token)
                    .putData("title", title)
                    .putData("body", body)
                target?.let { builder.putData("target", it) }
                fm.send(builder.build())
                sent++
            }.onFailure { log.warn("Push falló para un token: ${it.message}") }
        }
        return sent
    }
}
```

`PushRoutes.kt`:

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.repositories.DeviceTokenRepository
import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireRole
import com.neovita.server.services.PushService
import com.neovita.shared.network.dto.PushSendRequest
import com.neovita.shared.network.dto.PushSendResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pushRoutes(pushService: PushService, deviceRepo: DeviceTokenRepository, userRepo: UserRepository) {
    authenticate("jwt-auth") {
        // Test/ops sends only (EMPLOYER). Product-triggered pushes come later server-side.
        post("/push/test") {
            if (!call.requireRole(userRepo, "EMPLOYER")) return@post
            if (!pushService.enabled) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("code" to "PUSH_DISABLED",
                          "message" to "Configura FIREBASE_SERVICE_ACCOUNT para habilitar el envío")
                )
            }
            val req = call.receive<PushSendRequest>()
            if (req.title.isBlank() || req.body.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest)
            }
            val tokens = req.userId?.let { deviceRepo.tokensForUser(it) } ?: deviceRepo.allTokens()
            call.respond(PushSendResponse(sent = pushService.send(tokens, req.title, req.body, req.target)))
        }
    }
}
```

`application.conf` — add a sibling block after `appConfig { }`:

```hocon
push {
    serviceAccount = ${?FIREBASE_SERVICE_ACCOUNT}   # service-account JSON (secret); absent = sending disabled
}
```

`Application.kt`: `val pushService = PushService(config.propertyOrNull("push.serviceAccount")?.getString())`; pass to `configureRouting`. `Routing.kt`: parameter `pushService: PushService = PushService(null)`, register `pushRoutes(pushService, deviceTokenRepo, userRepo)` inside `/api` after `deviceRoutes(...)`.

- [ ] **Step 5: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all pass

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml server/build.gradle.kts core/src/commonMain/kotlin/com/neovita/shared/network/dto/PushDto.kt server/src/main/kotlin/com/neovita/server/services/PushService.kt server/src/main/kotlin/com/neovita/server/routes/PushRoutes.kt server/src/main/resources/application.conf server/src/main/kotlin/com/neovita/server/Application.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/test/kotlin/com/neovita/server/routes/PushRoutesTest.kt
git commit -m "feat(server): PushService (firebase-admin, env-gated) + POST /api/push/test"
```

---

### Task 4: Client — token registration API + dormant runtime activation

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt` (+ its test)
- Create: `shared/src/commonMain/kotlin/com/neovita/app/push/PushActivation.kt` (expect)
- Create: `shared/src/androidMain/kotlin/com/neovita/app/push/PushActivation.android.kt`
- Create: `shared/src/iosMain/kotlin/com/neovita/app/push/PushActivation.ios.kt`
- Create: `shared/src/wasmJsMain/kotlin/com/neovita/app/push/PushActivation.wasmJs.kt`
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/config/ConfigGate.kt`
- Modify: `gradle/libs.versions.toml` + `shared/build.gradle.kts` (firebase-messaging in androidMain)

**Interfaces:**
- Consumes: `WebConfigResponse.firebase` (Task 1), `POST /api/devices/token` (Task 2), `isFeatureEnabled`, `CurrentActivityHolder`.
- Produces: `ApiService.registerDeviceToken(token: String, platform: String): Result<Unit>`; `expect fun activatePush(config: WebConfigResponse?, apiService: ApiService)`; Android `object PushTokenUploader { fun upload(token: String) }` — Task 5's messaging service calls it on token rotation.

- [ ] **Step 1: TDD the ApiService method**

`ApiServiceTest.kt` — add mock route (before `else`):

```kotlin
            "/devices/token" -> respond("", HttpStatusCode.NoContent)
```

and test:

```kotlin
    @Test fun `registerDeviceToken posts and succeeds`() = runTest {
        assertTrue(apiService.registerDeviceToken("fcm-abc", "android").isSuccess)
    }
```

Run (expect compile failure), then implement in `ApiService.kt` after `getConfig`:

```kotlin
    suspend fun registerDeviceToken(token: String, platform: String): Result<Unit> = safeCall {
        httpClient.post("$baseUrl/devices/token") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(token, platform))
        }
        Unit
    }
```

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain` → PASS.

- [ ] **Step 2: Add the Android dependencies**

`libs.versions.toml`: `firebase-messaging = "24.1.0"`, `androidx-core = "1.13.1"` under `[versions]`; `[libraries]`: `firebase-messaging = { module = "com.google.firebase:firebase-messaging", version.ref = "firebase-messaging" }`, `androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }`. In `shared/build.gradle.kts` `androidMain.dependencies`: `implementation(libs.firebase.messaging)`. (androidx-core-ktx is consumed in Task 5 by androidApp.)

- [ ] **Step 3: The expect + no-op actuals**

`PushActivation.kt` (commonMain):

```kotlin
package com.neovita.app.push

import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse

// Runtime push activation: called whenever remote config changes. Idempotent. Activates
// ONLY when the server serves a Firebase client config AND the "push" flag (default off)
// is on — the binary ships dormant and Railway env vars light it up, no release needed.
expect fun activatePush(config: WebConfigResponse?, apiService: ApiService)
```

`PushActivation.ios.kt` and `PushActivation.wasmJs.kt` (identical bodies, ios package comment says push llega con el sub-proyecto 1b/iOS):

```kotlin
package com.neovita.app.push

import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse

actual fun activatePush(config: WebConfigResponse?, apiService: ApiService) {
    // Push aún no disponible en esta plataforma.
}
```

- [ ] **Step 4: The Android actual**

`PushActivation.android.kt`:

```kotlin
package com.neovita.app.push

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.shared.config.isFeatureEnabled
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Volatile
private var activated = false

actual fun activatePush(config: WebConfigResponse?, apiService: ApiService) {
    if (activated) return
    val firebase = config?.firebase ?: return
    if (!config.isFeatureEnabled("push", default = false)) return
    val context = CurrentActivityHolder.activity?.applicationContext ?: return
    activated = true
    runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(firebase.apiKey)
                    .setApplicationId(firebase.appId)
                    .setProjectId(firebase.projectId)
                    .setGcmSenderId(firebase.senderId)
                    .build()
            )
        }
        PushTokenUploader.apiService = apiService
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { PushTokenUploader.upload(it) }
            .addOnFailureListener { Log.w("NeoVitaPush", "No se pudo obtener el token FCM", it) }
    }.onFailure {
        Log.w("NeoVitaPush", "Activación de push falló (config inválida?)", it)
    }
}

// Also called by NeoVitaMessagingService.onNewToken (token rotation).
object PushTokenUploader {
    @Volatile
    var apiService: ApiService? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun upload(token: String) {
        val api = apiService ?: return
        scope.launch { api.registerDeviceToken(token, "android") }
    }
}
```

- [ ] **Step 5: Call it from ConfigGate**

In `ConfigGate.kt`, add after the ticker `LaunchedEffect` (imports `com.neovita.app.push.activatePush`, `com.neovita.shared.network.ApiService`; `val apiService = koinInject<ApiService>()` next to `repo`):

```kotlin
    // Dormant push: activates only when the server serves Firebase config + the flag.
    LaunchedEffect(config) { activatePush(config, apiService) }
```

- [ ] **Step 6: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt shared/src/commonMain/kotlin/com/neovita/app/push/PushActivation.kt shared/src/androidMain/kotlin/com/neovita/app/push/PushActivation.android.kt shared/src/iosMain/kotlin/com/neovita/app/push/PushActivation.ios.kt shared/src/wasmJsMain/kotlin/com/neovita/app/push/PushActivation.wasmJs.kt shared/src/commonMain/kotlin/com/neovita/app/config/ConfigGate.kt gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "feat(app): dormant push activation from remote config (Android)"
```

---

### Task 5: Android — messaging service, notification, tap → WebContentScreen, permission

**Files:**
- Create: `androidApp/src/main/kotlin/com/neovita/app/push/NeoVitaMessagingService.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/app/push/PushTargetHolder.kt`
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/navigation/AppNavigation.kt`
- Modify: `androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/build.gradle.kts` (core-ktx dep)

**Interfaces:**
- Consumes: `PushTokenUploader` (Task 4), `WebContentScreen` (existing).
- Produces: notification tap → `PushTargetHolder.target` → `AppNavigation` pushes `WebContentScreen`. Contract: intent extra `push_target` with `/relative` or `https://` value; anything else just opens the app.

- [ ] **Step 1: PushTargetHolder (commonMain — consumed by navigation, fed by Android)**

```kotlin
package com.neovita.app.push

import kotlinx.coroutines.flow.MutableStateFlow

// Pending deep target from a tapped push notification ("/web/x" or "https://…").
// Set by platform code (MainActivity intent extra), consumed once by AppNavigation.
object PushTargetHolder {
    val target = MutableStateFlow<String?>(null)
}
```

- [ ] **Step 2: Route it in AppNavigation**

In `AppNavigation.kt`, inside the `Navigator(startScreen) { navigator ->` block, next to the existing `LaunchedEffect(loggedIn)`, add (imports: `com.neovita.app.push.PushTargetHolder`, `com.neovita.app.screens.web.WebContentScreen`, `androidx.compose.runtime.collectAsState`):

```kotlin
        // A tapped push can carry a web target; same rules as SDUI OPEN_WEBVIEW.
        val pushTarget by PushTargetHolder.target.collectAsState()
        LaunchedEffect(pushTarget) {
            val target = pushTarget ?: return@LaunchedEffect
            PushTargetHolder.target.value = null
            if (target.startsWith("https://") || (target.startsWith("/") && !target.startsWith("//"))) {
                navigator.push(WebContentScreen(title = "NeoVita", url = target))
            }
        }
```

- [ ] **Step 3: The messaging service**

`androidApp/build.gradle.kts` dependencies: add `implementation(libs.androidx.core.ktx)`.

`NeoVitaMessagingService.kt`:

```kotlin
package com.neovita.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.neovita.app.MainActivity
import com.neovita.app.android.R

// Receives data-only FCM messages {title, body, target?} and builds the notification
// locally, so the tap contract stays stable across server changes (install-once spec).
class NeoVitaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTokenUploader.upload(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: return
        val body = message.data["body"] ?: ""
        val target = message.data["target"]

        val channelId = "neovita_general"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Recordatorios NeoVita", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            target?.let { putExtra("push_target", it) }
        }
        val pending = PendingIntent.getActivity(
            this, target.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(target.hashCode(), notification)
        } // sin permiso POST_NOTIFICATIONS notify lanza SecurityException en 33+: ignorar
    }
}
```

(If `com.neovita.app.android.R` doesn't resolve for `R.mipmap.ic_launcher`, check the androidApp namespace in its build.gradle.kts and use that; the launcher mipmap exists in androidApp resources.)

- [ ] **Step 4: MainActivity — intent extra + permission**

`MainActivity.kt` — add imports (`android.content.Intent`, `android.os.Build`, `androidx.activity.result.contract.ActivityResultContracts`, `com.neovita.app.push.PushTargetHolder`) and:

```kotlin
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional: ignorar */ }
```

In `onCreate`, after `CurrentActivityHolder.activity = this`:

```kotlin
        handlePushTarget(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
```

And add:

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushTarget(intent)
    }

    private fun handlePushTarget(intent: Intent?) {
        intent?.getStringExtra("push_target")?.let { PushTargetHolder.target.value = it }
    }
```

- [ ] **Step 5: Manifest**

`AndroidManifest.xml`: add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` next to INTERNET; on the activity add `android:launchMode="singleTop"`; inside `<application>` register:

```xml
        <service
            android:name="com.neovita.app.push.NeoVitaMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 6: Build**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :androidApp:assembleRelease --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/main/kotlin/com/neovita/app/push/NeoVitaMessagingService.kt shared/src/commonMain/kotlin/com/neovita/app/push/PushTargetHolder.kt shared/src/commonMain/kotlin/com/neovita/app/navigation/AppNavigation.kt androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt androidApp/src/main/AndroidManifest.xml androidApp/build.gradle.kts
git commit -m "feat(android): FCM messaging service, notification channel and push-tap web routing"
```

---

### Task 6: E2E on the emulator (controller-run)

**Files:** none (verification only). Uses AVD `Pixel_5_E2E`.

- [ ] **Step 1: Dormant checks.** Server WITHOUT Firebase env vars → app runs exactly as before (no crash, dashboard fine); `curl /api/config` has no `firebase` key. Server WITH the four `FIREBASE_*` client vars but flag off → still dormant. With flag `push=true` + fake values → app logs a `NeoVitaPush` warning, no crash.
- [ ] **Step 2: Tap-routing E2E (no Firebase needed).** With app installed and server running: `adb shell am start -n com.neovita.app/.MainActivity --es push_target "/web/demo"` → app opens and pushes `WebContentScreen` with the demo page ("Sesión: activa" if logged in). Warm-start variant (app already open) re-fires via `onNewIntent`.
- [ ] **Step 3: Registration E2E (no Firebase needed).** `curl -X POST /api/devices/token` with the dev JWT registers a row (`psql`: `SELECT * FROM device_tokens;`); `POST /api/push/test` with EMPLOYER JWT returns 503 `PUSH_DISABLED` (no service account).
- [ ] **Step 4: Delivery E2E** — documented as blocked on the user's Firebase project; checklist in "External setup" above.
