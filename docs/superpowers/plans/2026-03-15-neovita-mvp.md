# NeoVita MVP Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin Multiplatform MVP of NeoVita — a longevity coaching app with AI — running on Android, iOS and Web with a shared Compose Multiplatform UI and a Ktor backend.

**Architecture:** Gradle monorepo with three modules: `:server` (Ktor + PostgreSQL), `:shared` (KMP domain/data/network layers), `:composeApp` (Compose Multiplatform UI for Android/iOS/Web). The server acts as a secure proxy for the Claude API, issues JWTs after Google OAuth verification, and persists user data.

**Tech Stack:** Kotlin 2.0 · Compose Multiplatform 1.7 · Ktor 3.0 (server + client) · Exposed ORM · PostgreSQL · SQLDelight 2.0 · Koin 4.0 · Voyager 1.1 · Kotlinx Serialization · Kotlinx DateTime · Coil 3

**Spec:** `docs/superpowers/specs/2026-03-15-neovita-mvp-design.md`

> **Test commands reference:**
> - `:server` tests: `./gradlew :server:test`
> - `:shared` tests: `./gradlew :shared:allTests` (runs all KMP targets)
> - `:composeApp` tests: `./gradlew :composeApp:allTests`

---

## File Map

```
neovita/
├── gradle/libs.versions.toml              # Version catalog — single source of truth for all deps
├── build.gradle.kts                        # Root build: common plugins, no dependencies
├── settings.gradle.kts                     # Declares all modules
├── gradle.properties                       # KMP flags, JVM config
│
├── server/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/neovita/server/
│       ├── Application.kt                  # main() + embeddedServer, installs all plugins
│       ├── plugins/
│       │   ├── Routing.kt                  # Mounts all route modules
│       │   ├── Authentication.kt           # JWT bearer + Google token verifier
│       │   ├── Serialization.kt            # Kotlinx JSON content negotiation
│       │   └── Database.kt                 # Exposed + PostgreSQL connection pool
│       ├── routes/
│       │   ├── AuthRoutes.kt               # POST /auth/google
│       │   ├── UserRoutes.kt               # GET /users/me, PATCH /users/me
│       │   ├── AssessmentRoutes.kt         # POST /assessments, GET /assessments/latest
│       │   ├── PlanRoutes.kt               # GET /plans/current, POST /plans/generate (SSE)
│       │   ├── ChatRoutes.kt               # POST /chat (SSE streaming)
│       │   └── B2BRoutes.kt                # GET /b2b/team (EMPLOYER only)
│       ├── services/
│       │   ├── GoogleAuthService.kt        # HTTP call to tokeninfo endpoint
│       │   ├── JwtService.kt               # Sign + verify JWT with HMAC-SHA256
│       │   ├── ClaudeService.kt            # Anthropic client, streams SSE back to caller
│       │   └── ScoreService.kt             # Pure function: Assessment → PillarScores
│       └── db/
│           ├── DatabaseFactory.kt          # createDatabase(), runs Exposed SchemaUtils
│           ├── tables/
│           │   ├── UsersTable.kt           # Exposed Table object
│           │   ├── AssessmentsTable.kt
│           │   └── PlansTable.kt
│           └── repositories/
│               ├── UserRepository.kt
│               ├── AssessmentRepository.kt
│               └── PlanRepository.kt
│
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/neovita/shared/
│       │   ├── domain/
│       │   │   ├── model/                  # Pure data classes (User, Assessment, etc.)
│       │   │   ├── repository/             # Interfaces only
│       │   │   └── usecase/                # CalculateScoresUseCase, GeneratePlanUseCase, etc.
│       │   ├── data/
│       │   │   ├── repository/             # Implementations delegating to network + cache
│       │   │   └── mapper/                 # DTO ↔ domain model converters
│       │   └── network/
│       │       ├── ApiService.kt           # Ktor Client wrapper, all HTTP calls
│       │       ├── dto/                    # @Serializable request/response DTOs
│       │       └── error/NetworkError.kt   # Sealed class for typed errors
│       ├── commonTest/kotlin/com/neovita/shared/
│       │   ├── domain/usecase/CalculateScoresUseCaseTest.kt
│       │   └── network/ApiServiceTest.kt   # Uses Ktor MockEngine
│       ├── androidMain/  → provides Android SQLDelight driver
│       ├── iosMain/      → provides Native SQLDelight driver
│       └── wasmJsMain/   → provides Web Worker SQLDelight driver
│
└── composeApp/
    ├── build.gradle.kts
    └── src/
        ├── commonMain/kotlin/com/neovita/app/
        │   ├── App.kt                      # KoinApplication + AppNavigation entry
        │   ├── navigation/AppNavigation.kt # Voyager TabNavigator, guards JWT check
        │   ├── auth/GoogleSignIn.kt        # expect class GoogleSignInClient
        │   ├── ui/theme/                   # Color.kt, Type.kt, Theme.kt (NeoVita brand)
        │   ├── ui/components/              # ScoreRing, NeoProgressBar, ChatBubble, ErrorBanner
        │   └── screens/
        │       ├── login/    LoginScreen + LoginViewModel
        │       ├── onboarding/ OnboardingScreen + OnboardingViewModel
        │       ├── assessment/ AssessmentScreen + AssessmentViewModel
        │       ├── results/   ResultsScreen + ResultsViewModel
        │       ├── dashboard/ DashboardScreen + DashboardViewModel
        │       ├── plan/      PlanScreen + PlanViewModel
        │       ├── chat/      ChatScreen + ChatViewModel
        │       ├── b2b/       B2BScreen + B2BViewModel
        │       └── profile/   ProfileScreen + ProfileViewModel
        ├── androidMain/ → MainActivity, GoogleSignIn.android.kt
        ├── iosMain/     → MainViewController, GoogleSignIn.ios.kt
        └── wasmJsMain/  → main.kt, GoogleSignIn.wasmJs.kt
```

---

## Chunk 1: Project Scaffolding

### Task 1: Initialize Gradle Monorepo

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Generate KMP project skeleton**

  Go to https://kmp.jetbrains.com/, configure:
  - Name: `NeoVita`, package: `com.neovita`
  - Targets: Android, iOS, Web (Wasm)
  - Include: Compose Multiplatform UI
  - Download and unzip into the project directory.

  Alternatively run via Android Studio: **File → New → Kotlin Multiplatform App**.

- [ ] **Step 2: Replace `gradle/libs.versions.toml` with the full version catalog**

```toml
[versions]
kotlin = "2.0.21"
compose-multiplatform = "1.7.3"
ktor = "3.0.3"
exposed = "0.56.0"
sqldelight = "2.0.2"
koin = "4.0.0"
voyager = "1.1.0"
postgresql-driver = "42.7.4"
kotlinx-coroutines = "1.9.0"
kotlinx-serialization = "1.7.3"
kotlinx-datetime = "0.6.1"
coil = "3.0.4"
turbine = "1.2.0"
logback = "1.5.12"
java-jwt = "4.4.0"

[libraries]
# Ktor Server
ktor-server-core = { module = "io.ktor:ktor-server-core-jvm", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty-jvm", version.ref = "ktor" }
ktor-server-auth = { module = "io.ktor:ktor-server-auth-jvm", version.ref = "ktor" }
ktor-server-auth-jwt = { module = "io.ktor:ktor-server-auth-jwt-jvm", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation-jvm", version.ref = "ktor" }
ktor-server-sse = { module = "io.ktor:ktor-server-sse-jvm", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
# Ktor Client
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
# Exposed ORM
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-dao = { module = "org.jetbrains.exposed:exposed-dao", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-kotlin-datetime = { module = "org.jetbrains.exposed:exposed-kotlin-datetime", version.ref = "exposed" }
postgresql-driver = { module = "org.postgresql:postgresql", version.ref = "postgresql-driver" }
# SQLDelight
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-web-driver = { module = "app.cash.sqldelight:web-worker-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-ktor = { module = "io.insert-koin:koin-ktor", version.ref = "koin" }
# Voyager
voyager-navigator = { module = "cafe.adriel.voyager:voyager-navigator", version.ref = "voyager" }
voyager-tab-navigator = { module = "cafe.adriel.voyager:voyager-tab-navigator", version.ref = "voyager" }
voyager-koin = { module = "cafe.adriel.voyager:voyager-koin", version.ref = "voyager" }
# Kotlinx
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
# Compose
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
# Test
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
java-jwt = { module = "com.auth0:java-jwt", version.ref = "java-jwt" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
android-application = { id = "com.android.application", version = "8.7.3" }
```

- [ ] **Step 3: Update `settings.gradle.kts`**

```kotlin
rootProject.name = "NeoVita"
include(":composeApp", ":server", ":shared")
```

- [ ] **Step 4: Set `gradle.properties`**

```properties
kotlin.code.style=official
android.useAndroidX=true
kotlin.mpp.enableCInteropCommonization=true
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

- [ ] **Step 5: Verify the project syncs**

```bash
./gradlew projects
```
Expected: lists `:composeApp`, `:server`, `:shared` with no errors.

- [ ] **Step 6: Commit**

```bash
git init
git add gradle/ build.gradle.kts settings.gradle.kts gradle.properties
git commit -m "feat: initialize KMP monorepo scaffold"
```

---

### Task 2: Configure Module Build Files

**Files:**
- Create: `server/build.gradle.kts`
- Create: `shared/build.gradle.kts`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Write `server/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.neovita.server.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql.driver)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback)
    implementation(libs.java.jwt)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}
```

- [ ] **Step 2: Write `shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget()
    iosX64(); iosArm64(); iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.web.driver)
        }
        val nonJsMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(nonJsMain)
        iosMain.get().dependsOn(nonJsMain)
        val nonJsNativeMain by creating { dependsOn(nonJsMain) }
        iosMain.get().dependsOn(nonJsNativeMain)
        val appleMain by getting
        appleMain.dependencies { implementation(libs.ktor.client.darwin) }
        val jvmAndAndroidMain by creating { dependsOn(nonJsMain) }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmAndAndroidMain.dependencies { implementation(libs.ktor.client.cio) }
    }
}

sqldelight {
    databases {
        create("NeoVitaDatabase") {
            packageName.set("com.neovita.shared.db")
        }
    }
}
```

- [ ] **Step 3: Sync and verify both modules compile**

```bash
./gradlew :server:compileKotlin :shared:compileKotlinMetadata
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add server/build.gradle.kts shared/build.gradle.kts composeApp/build.gradle.kts
git commit -m "feat: configure module build files with version catalog"
```

---

## Chunk 2: Server — Auth, Database & Core

### Task 3: Database Setup (Exposed + PostgreSQL)

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/db/DatabaseFactory.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/tables/UsersTable.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/tables/AssessmentsTable.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/tables/PlansTable.kt`
- Create: `server/src/main/resources/application.conf`

- [ ] **Step 1: Write `application.conf`**

```hocon
ktor {
    deployment { port = 8080 }
    application { modules = [ com.neovita.server.ApplicationKt.module ] }
}

database {
    url = ${?DB_URL}           # postgres://user:pass@host:5432/neovita
    driver = "org.postgresql.Driver"
}

jwt {
    secret = ${?JWT_SECRET}
    issuer = "neovita"
    audience = "neovita-app"
    expirationMs = 86400000   # 24h
}

claude {
    apiKey = ${?CLAUDE_API_KEY}
    model = "claude-sonnet-4-6"
}
```

- [ ] **Step 2: Write Exposed table objects**

`UsersTable.kt`:
```kotlin
package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = varchar("id", 36)           // UUID
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val age = integer("age").default(0)
    val role = varchar("role", 20).default("USER")  // USER | EMPLOYER
    val companyId = varchar("company_id", 36).nullable()
    override val primaryKey = PrimaryKey(id)
}
```

`AssessmentsTable.kt`:
```kotlin
object AssessmentsTable : Table("assessments") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val createdAt = long("created_at")          // epochMilliseconds
    val exerciseFrequency = varchar("exercise_frequency", 50)
    val exerciseType = varchar("exercise_type", 100)
    val sleepHours = varchar("sleep_hours", 10) // "4-6"|"6-8"|"8+"
    val sleepQuality = integer("sleep_quality") // 1-10
    val mainGoal = varchar("main_goal", 255)
    override val primaryKey = PrimaryKey(id)
}
```

`PlansTable.kt`:
```kotlin
object PlansTable : Table("plans") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val generatedAt = long("generated_at")
    val nutritionJson = text("nutrition_json")
    val sleepJson = text("sleep_json")
    val exerciseJson = text("exercise_json")
    val scoresJson = text("scores_json")
    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 3: Write `DatabaseFactory.kt`**

```kotlin
package com.neovita.server.db

import com.neovita.server.db.tables.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(app: Application) {
        val url = app.environment.config.property("database.url").getString()
        val driver = app.environment.config.property("database.driver").getString()
        Database.connect(url, driver)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, AssessmentsTable, PlansTable)
        }
    }
}
```

- [ ] **Step 4: Write `plugins/Database.kt`**

```kotlin
package com.neovita.server.plugins

import com.neovita.server.db.DatabaseFactory
import io.ktor.server.application.*

fun Application.configureDatabase() {
    DatabaseFactory.init(this)
}
```

- [ ] **Step 5: Start a local PostgreSQL instance and verify schema creates**

```bash
# Using Docker:
docker run -d --name neovita-db \
  -e POSTGRES_DB=neovita \
  -e POSTGRES_USER=neovita \
  -e POSTGRES_PASSWORD=secret \
  -p 5432:5432 postgres:16

export DB_URL="jdbc:postgresql://localhost:5432/neovita?user=neovita&password=secret"
export JWT_SECRET="dev-secret-change-in-prod"
export CLAUDE_API_KEY="your-key-here"
./gradlew :server:run
```
Expected: server starts, tables `users`, `assessments`, `plans` created in PostgreSQL.

- [ ] **Step 6: Commit**

```bash
git add server/src/
git commit -m "feat(server): database setup with Exposed ORM and PostgreSQL schema"
```

---

### Task 4: JWT + Google Auth Service

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/services/JwtService.kt`
- Create: `server/src/main/kotlin/com/neovita/server/services/GoogleAuthService.kt`
- Create: `server/src/main/kotlin/com/neovita/server/plugins/Authentication.kt`
- Create: `server/src/test/kotlin/com/neovita/server/services/JwtServiceTest.kt`

- [ ] **Step 1: Write the failing JWT test**

```kotlin
// JwtServiceTest.kt
class JwtServiceTest {
    private val service = JwtService(
        secret = "test-secret",
        issuer = "neovita",
        audience = "neovita-app",
        expirationMs = 3600_000L
    )

    @Test fun `generates token and verifies user id`() {
        val token = service.generateToken(userId = "user-123", role = "USER")
        val principal = service.verify(token)
        assertEquals("user-123", principal?.userId)
    }

    @Test fun `returns null for tampered token`() {
        val token = service.generateToken("user-123", "USER") + "tampered"
        assertNull(service.verify(token))
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew :server:test --tests "*.JwtServiceTest"
```
Expected: compilation error — `JwtService` not defined.

- [ ] **Step 3: Implement `JwtService.kt`**

```kotlin
package com.neovita.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

data class JwtPrincipal(val userId: String, val role: String)

class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expirationMs: Long
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: String, role: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationMs))
            .sign(algorithm)

    fun verify(token: String): JwtPrincipal? = runCatching {
        val decoded = JWT.require(algorithm).withIssuer(issuer).build().verify(token)
        JwtPrincipal(
            userId = decoded.getClaim("userId").asString(),
            role = decoded.getClaim("role").asString()
        )
    }.getOrNull()
}
```

> **Note:** Add `implementation("com.auth0:java-jwt:4.4.0")` to `server/build.gradle.kts`.

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :server:test --tests "*.JwtServiceTest"
```

- [ ] **Step 5: Implement `GoogleAuthService.kt`**

```kotlin
package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class GoogleUserInfo(val sub: String, val email: String, val name: String)

class GoogleAuthService(private val httpClient: HttpClient) {
    // Calls Google's tokeninfo endpoint to verify the ID token
    suspend fun verifyIdToken(idToken: String): GoogleUserInfo? = runCatching {
        httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }.body<GoogleUserInfo>()
    }.getOrNull()
}
```

- [ ] **Step 6: Write `plugins/Authentication.kt`**

```kotlin
package com.neovita.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuthentication(secret: String, issuer: String) {
    val algorithm = Algorithm.HMAC256(secret)
    install(Authentication) {
        jwt("jwt-auth") {
            verifier(JWT.require(algorithm).withIssuer(issuer).build())
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId != null) UserIdPrincipal(userId) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized,
                    mapOf("code" to "AUTH_EXPIRED", "message" to "Token inválido o expirado"))
            }
        }
    }
}
```

> **Note:** The `verifier` receives a `JWTVerifier` directly — Ktor's JWT plugin calls `.verify(token)` internally. `UserIdPrincipal.name` will contain the userId claim. See: https://ktor.io/docs/server-jwt.html

- [ ] **Step 7: Commit**

```bash
git add server/src/
git commit -m "feat(server): JWT service and Google OAuth token verification"
```

---

### Task 5: Auth Route — POST /auth/google

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/routes/AuthRoutes.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/repositories/UserRepository.kt`
- Create: `server/src/test/kotlin/com/neovita/server/routes/AuthRoutesTest.kt`

- [ ] **Step 1: Write the failing route test**

```kotlin
// AuthRoutesTest.kt
class AuthRoutesTest {
    @Test fun `POST auth-google returns 401 for invalid token`() = testApplication {
        application { configureTestApp() }
        val response = client.post("/auth/google") {
            contentType(ContentType.Application.Json)
            setBody("""{"idToken":"invalid-token"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew :server:test --tests "*.AuthRoutesTest"
```

- [ ] **Step 3: Implement `UserRepository.kt`**

```kotlin
package com.neovita.server.db.repositories

import com.neovita.server.db.tables.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class UserEntity(val id: String, val email: String, val name: String,
                      val age: Int, val role: String, val companyId: String?)

class UserRepository {
    fun findByEmail(email: String): UserEntity? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email }
            .singleOrNull()?.toEntity()
    }

    fun upsert(email: String, name: String): UserEntity = transaction {
        val existing = findByEmail(email)
        if (existing != null) return@transaction existing
        val id = UUID.randomUUID().toString()
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[UsersTable.name] = name
        }
        findByEmail(email)!!
    }

    fun findById(id: String): UserEntity? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toEntity()
    }

    fun findByCompany(companyId: String): List<UserEntity> = transaction {
        UsersTable.selectAll().where { UsersTable.companyId eq companyId }.map { it.toEntity() }
    }

    fun update(id: String, name: String? = null, age: Int? = null): UserEntity? = transaction {
        UsersTable.update({ UsersTable.id eq id }) {
            name?.let { n -> it[UsersTable.name] = n }
            age?.let { a -> it[UsersTable.age] = a }
        }
        findById(id)
    }

    private fun ResultRow.toEntity() = UserEntity(
        id = this[UsersTable.id], email = this[UsersTable.email],
        name = this[UsersTable.name], age = this[UsersTable.age],
        role = this[UsersTable.role], companyId = this[UsersTable.companyId]
    )
}
```

- [ ] **Step 4: Implement `AuthRoutes.kt`**

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.services.GoogleAuthService
import com.neovita.server.services.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class GoogleAuthRequest(val idToken: String)
@Serializable data class AuthResponse(val token: String, val isNewUser: Boolean)

fun Route.authRoutes(
    googleAuthService: GoogleAuthService,
    jwtService: JwtService,
    userRepository: UserRepository
) {
    post("/auth/google") {
        val request = call.receive<GoogleAuthRequest>()
        val googleUser = googleAuthService.verifyIdToken(request.idToken)
            ?: return@post call.respond(HttpStatusCode.Unauthorized,
                mapOf("code" to "AUTH_INVALID_TOKEN",
                      "message" to "El token de Google es inválido o ha expirado"))

        val isNew = userRepository.findByEmail(googleUser.email) == null
        val user = userRepository.upsert(googleUser.email, googleUser.name)
        val token = jwtService.generateToken(user.id, user.role)
        call.respond(AuthResponse(token = token, isNewUser = isNew))
    }
}
```

- [ ] **Step 5: Run test — expect PASS**

```bash
./gradlew :server:test --tests "*.AuthRoutesTest"
```

- [ ] **Step 6: Commit**

```bash
git add server/src/
git commit -m "feat(server): POST /auth/google route with Google token verification"
```

---

### Task 6: User, Assessment & Score Routes

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/routes/UserRoutes.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/AssessmentRoutes.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/repositories/AssessmentRepository.kt`
- Create: `server/src/main/kotlin/com/neovita/server/services/ScoreService.kt`
- Create: `server/src/test/kotlin/com/neovita/server/services/ScoreServiceTest.kt`

- [ ] **Step 1: Write failing ScoreService test (critical business logic)**

```kotlin
class ScoreServiceTest {
    @Test fun `max exercise score for daily workouts with strength`() {
        val scores = ScoreService.calculate(
            exerciseFrequency = "Todos los días",
            exerciseType = "Pesas o resistencia",
            sleepHours = "7-8 horas",
            sleepQuality = 9
        )
        assertTrue(scores.exercise >= 90)
        assertTrue(scores.sleep >= 85)
    }

    @Test fun `low score for no exercise and poor sleep`() {
        val scores = ScoreService.calculate(
            exerciseFrequency = "Nunca",
            exerciseType = "No hago ejercicio",
            sleepHours = "Menos de 5 horas",
            sleepQuality = 2
        )
        assertTrue(scores.exercise <= 15)
        assertTrue(scores.sleep <= 20)
        assertTrue(scores.overall < 50)
    }

    @Test fun `overall is weighted average of pillars`() {
        val scores = ScoreService.calculate("2-3 veces", "Cardio", "6-8 horas", 6)
        val expectedOverall = (scores.exercise + scores.sleep + scores.nutrition) / 3
        assertEquals(expectedOverall, scores.overall)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew :server:test --tests "*.ScoreServiceTest"
```

- [ ] **Step 3: Implement `ScoreService.kt`**

```kotlin
package com.neovita.server.services

import kotlinx.serialization.Serializable

@Serializable
data class PillarScores(val overall: Int, val exercise: Int, val sleep: Int, val nutrition: Int)

object ScoreService {
    fun calculate(
        exerciseFrequency: String,
        exerciseType: String,
        sleepHours: String,
        sleepQuality: Int
    ): PillarScores {
        val exerciseFreqScore = when (exerciseFrequency) {
            "Todos los días" -> 100
            "4-5 veces" -> 85
            "2-3 veces" -> 65
            "1 vez" -> 40
            else -> 10  // "Nunca"
        }
        val exerciseTypeBonus = when (exerciseType) {
            "Pesas o resistencia" -> 5
            "Yoga o pilates" -> 3
            else -> 0
        }
        val exerciseScore = (exerciseFreqScore + exerciseTypeBonus).coerceAtMost(100)

        val sleepHoursScore = when (sleepHours) {
            "7-8 horas", "8+" -> 90
            "6-7 horas", "6-8 horas" -> 70
            "5-6 horas" -> 45
            else -> 15  // "Menos de 5 horas"
        }
        val sleepQualityScore = ((sleepQuality.toFloat() / 10f) * 100).toInt()
        val sleepScore = ((sleepHoursScore + sleepQualityScore) / 2)

        // Nutrition is not assessed yet — default to 60 as neutral baseline for MVP
        val nutritionScore = 60

        val overall = (exerciseScore + sleepScore + nutritionScore) / 3
        return PillarScores(overall, exerciseScore, sleepScore, nutritionScore)
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :server:test --tests "*.ScoreServiceTest"
```

- [ ] **Step 5: Implement `AssessmentRepository.kt`**

```kotlin
package com.neovita.server.db.repositories

import com.neovita.server.db.tables.AssessmentsTable
import com.neovita.server.services.PillarScores
import com.neovita.server.services.ScoreService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class AssessmentEntity(
    val id: String, val userId: String, val createdAt: Long,
    val exerciseFrequency: String, val exerciseType: String,
    val sleepHours: String, val sleepQuality: Int, val mainGoal: String,
    val scores: PillarScores
)

class AssessmentRepository {
    fun save(userId: String, frequency: String, type: String,
             sleepHours: String, sleepQuality: Int, goal: String): AssessmentEntity {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val scores = ScoreService.calculate(frequency, type, sleepHours, sleepQuality)
        transaction {
            AssessmentsTable.insert {
                it[AssessmentsTable.id] = id
                it[AssessmentsTable.userId] = userId
                it[createdAt] = now
                it[exerciseFrequency] = frequency
                it[exerciseType] = type
                it[AssessmentsTable.sleepHours] = sleepHours
                it[AssessmentsTable.sleepQuality] = sleepQuality
                it[mainGoal] = goal
            }
        }
        return AssessmentEntity(id, userId, now, frequency, type, sleepHours, sleepQuality, goal, scores)
    }

    fun findLatest(userId: String): AssessmentEntity? = transaction {
        AssessmentsTable.selectAll()
            .where { AssessmentsTable.userId eq userId }
            .orderBy(AssessmentsTable.createdAt, SortOrder.DESC)
            .limit(1).singleOrNull()?.toEntity()
    }

    private fun ResultRow.toEntity(): AssessmentEntity {
        val freq = this[AssessmentsTable.exerciseFrequency]
        val type = this[AssessmentsTable.exerciseType]
        val sh = this[AssessmentsTable.sleepHours]
        val sq = this[AssessmentsTable.sleepQuality]
        return AssessmentEntity(
            id = this[AssessmentsTable.id],
            userId = this[AssessmentsTable.userId],
            createdAt = this[AssessmentsTable.createdAt],
            exerciseFrequency = freq, exerciseType = type,
            sleepHours = sh, sleepQuality = sq,
            mainGoal = this[AssessmentsTable.mainGoal],
            scores = ScoreService.calculate(freq, type, sh, sq)
        )
    }
}
```

- [ ] **Step 5b: Implement `PlanRepository.kt`**

```kotlin
package com.neovita.server.db.repositories

import com.neovita.server.db.tables.PlansTable
import com.neovita.server.services.PillarScores
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class PlanEntity(val id: String, val userId: String, val generatedAt: Long,
                      val planContent: String, val scores: PillarScores)

class PlanRepository {
    fun save(userId: String, scores: PillarScores, planContent: String): PlanEntity {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        transaction {
            PlansTable.insert {
                it[PlansTable.id] = id
                it[PlansTable.userId] = userId
                it[generatedAt] = now
                it[nutritionJson] = planContent   // Stores full JSON plan from Claude
                it[sleepJson] = ""
                it[exerciseJson] = ""
                it[scoresJson] = Json.encodeToString(scores)
            }
        }
        return PlanEntity(id, userId, now, planContent, scores)
    }

    fun findCurrent(userId: String): PlanEntity? = transaction {
        PlansTable.selectAll()
            .where { PlansTable.userId eq userId }
            .orderBy(PlansTable.generatedAt, SortOrder.DESC)
            .limit(1).singleOrNull()?.let {
                PlanEntity(
                    id = it[PlansTable.id],
                    userId = it[PlansTable.userId],
                    generatedAt = it[PlansTable.generatedAt],
                    planContent = it[PlansTable.nutritionJson],
                    scores = Json.decodeFromString(it[PlansTable.scoresJson])
                )
            }
    }
}
```

- [ ] **Step 5c: Add mapper extensions (`server/.../db/Mappers.kt`)**

```kotlin
package com.neovita.server.db

import com.neovita.server.db.repositories.*
import com.neovita.server.services.PillarScores

// DTO types used in route responses
fun UserEntity.toDto() = mapOf(
    "id" to id, "email" to email, "name" to name,
    "age" to age, "role" to role, "companyId" to companyId
)

fun AssessmentEntity.toDto() = mapOf(
    "id" to id, "userId" to userId, "createdAt" to createdAt,
    "exerciseFrequency" to exerciseFrequency, "exerciseType" to exerciseType,
    "sleepHours" to sleepHours, "sleepQuality" to sleepQuality,
    "mainGoal" to mainGoal,
    "scores" to mapOf("overall" to scores.overall, "exercise" to scores.exercise,
                      "sleep" to scores.sleep, "nutrition" to scores.nutrition)
)

fun PlanEntity.toDto() = mapOf(
    "id" to id, "userId" to userId, "generatedAt" to generatedAt,
    "planContent" to planContent,
    "scores" to mapOf("overall" to scores.overall, "exercise" to scores.exercise,
                      "sleep" to scores.sleep, "nutrition" to scores.nutrition)
)
```

- [ ] **Step 6: Implement `UserRoutes.kt` and `AssessmentRoutes.kt`**

`UserRoutes.kt`:
```kotlin
fun Route.userRoutes(userRepository: UserRepository) {
    authenticate("jwt-auth") {
        get("/users/me") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val user = userRepository.findById(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user.toDto())
        }
        patch("/users/me") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            @Serializable data class PatchRequest(val name: String? = null, val age: Int? = null)
            val req = call.receive<PatchRequest>()
            val updated = userRepository.update(userId, req.name, req.age)
            call.respond(updated?.toDto() ?: HttpStatusCode.NotFound)
        }
    }
}
```

`AssessmentRoutes.kt`:
```kotlin
fun Route.assessmentRoutes(repo: AssessmentRepository) {
    authenticate("jwt-auth") {
        post("/assessments") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            @Serializable data class AssessmentRequest(
                val exerciseFrequency: String, val exerciseType: String,
                val sleepHours: String, val sleepQuality: Int, val mainGoal: String
            )
            val req = call.receive<AssessmentRequest>()
            val entity = repo.save(userId, req.exerciseFrequency, req.exerciseType,
                                   req.sleepHours, req.sleepQuality, req.mainGoal)
            call.respond(HttpStatusCode.Created, entity.toDto())
        }
        get("/assessments/latest") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val entity = repo.findLatest(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(entity.toDto())
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add server/src/
git commit -m "feat(server): user + assessment routes with score calculation"
```

---

### Task 7: Plan Generation & Chat Routes (Claude API + SSE)

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/services/ClaudeService.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/PlanRoutes.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/ChatRoutes.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/B2BRoutes.kt`
- Create: `server/src/main/kotlin/com/neovita/server/plugins/Routing.kt`
- Create: `server/src/main/kotlin/com/neovita/server/Application.kt`

- [ ] **Step 1: Implement `ClaudeService.kt`**

```kotlin
package com.neovita.server.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class ClaudeMessage(val role: String, val content: String)

class ClaudeService(private val client: HttpClient, private val apiKey: String, private val model: String) {

    private val systemPrompt = """
        Eres el coach de longevidad personal de NeoVita, una IA especializada en salud y bienestar
        para adultos mayores de 45 años en Colombia. Responde siempre en español con referencias
        culturales colombianas. Sé empático, práctico y motivador. Limita respuestas a 150-200 palabras.
    """.trimIndent()

    fun streamChat(messages: List<ClaudeMessage>): Flow<String> = flow {
        client.preparePost("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(buildJsonBody(messages, stream = true))
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") return@execute
                    runCatching {
                        val json = Json.parseToJsonElement(data)
                        val text = json.jsonObject["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        if (text != null) emit(text)
                    }
                }
            }
        }
    }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int = 1024,
        val system: String,
        val messages: List<ClaudeMessage>,
        val stream: Boolean
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildJsonBody(messages: List<ClaudeMessage>, stream: Boolean): String =
        json.encodeToString(ClaudeRequest(
            model = model, system = systemPrompt, messages = messages, stream = stream
        ))
}
```

- [ ] **Step 2: Implement `PlanRoutes.kt`**

```kotlin
fun Route.planRoutes(claudeService: ClaudeService, assessmentRepo: AssessmentRepository,
                     planRepo: PlanRepository) {
    authenticate("jwt-auth") {
        get("/plans/current") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val plan = planRepo.findCurrent(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(plan.toDto())
        }
        post("/plans/generate") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val assessment = assessmentRepo.findLatest(userId)
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    mapOf("code" to "NO_ASSESSMENT", "message" to "Completa la evaluación primero"))

            val prompt = buildPlanPrompt(assessment)
            call.response.header(HttpHeaders.ContentType, "text/event-stream")
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val buffer = StringBuilder()
                try {
                    claudeService.streamChat(listOf(ClaudeMessage("user", prompt)))
                        .collect { chunk ->
                            buffer.append(chunk)
                            write("data: $chunk\n\n")
                            flush()
                        }
                    // Persist only when stream completed successfully
                    if (buffer.isNotEmpty()) planRepo.save(userId, assessment.scores, buffer.toString())
                    write("data: [DONE]\n\n")
                } catch (e: Exception) {
                    write("data: [ERROR]\n\n")
                } finally {
                    flush()
                }
            }
        }
    }
}

private fun buildPlanPrompt(a: AssessmentEntity) = """
    Genera un plan de longevidad estructurado en JSON con claves "nutrition", "sleep", "exercise".
    Cada clave debe tener una lista de 3 recomendaciones concretas y accionables.
    Perfil: ejercicio ${a.exerciseFrequency}, tipo ${a.exerciseType},
    sueño ${a.sleepHours}h (calidad ${a.sleepQuality}/10), objetivo: ${a.mainGoal}.
    Responde SOLO con el JSON, sin texto adicional.
""".trimIndent()
```

- [ ] **Step 3: Implement `ChatRoutes.kt`**

```kotlin
fun Route.chatRoutes(claudeService: ClaudeService) {
    authenticate("jwt-auth") {
        post("/chat") {
            @Serializable data class ChatRequest(val messages: List<ClaudeMessage>)
            val request = call.receive<ChatRequest>()
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                claudeService.streamChat(request.messages).collect { chunk ->
                    write("data: $chunk\n\n")
                    flush()
                }
                write("data: [DONE]\n\n")
                flush()
            }
        }
    }
}
```

- [ ] **Step 4: Implement `B2BRoutes.kt`**

```kotlin
fun Route.b2bRoutes(userRepository: UserRepository, assessmentRepo: AssessmentRepository) {
    authenticate("jwt-auth") {
        get("/b2b/team") {
            val principal = call.principal<UserIdPrincipal>()!!
            val user = userRepository.findById(principal.name)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            if (user.role != "EMPLOYER") return@get call.respond(HttpStatusCode.Forbidden,
                mapOf("code" to "FORBIDDEN", "message" to "Se requiere rol EMPLOYER"))
            val team = userRepository.findByCompany(user.companyId!!)
            val teamData = team.map { member ->
                val scores = assessmentRepo.findLatest(member.id)?.scores
                mapOf("userId" to member.id, "name" to member.name,
                      "email" to member.email, "scores" to scores)
            }
            call.respond(mapOf("team" to teamData, "avgScore" to
                teamData.mapNotNull { (it["scores"] as? PillarScores)?.overall }.average().toInt()))
        }
    }
}
```

- [ ] **Step 5: Wire everything in `Application.kt`**

```kotlin
package com.neovita.server

import com.neovita.server.db.DatabaseFactory
import com.neovita.server.db.repositories.*
import com.neovita.server.plugins.*
import com.neovita.server.routes.*
import com.neovita.server.services.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    val config = environment.config
    val httpClient = HttpClient(CIO)
    val userRepo = UserRepository()
    val assessmentRepo = AssessmentRepository()
    val planRepo = PlanRepository()
    val jwtService = JwtService(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
        expirationMs = config.property("jwt.expirationMs").getString().toLong()
    )
    val googleService = GoogleAuthService(httpClient)
    val claudeService = ClaudeService(
        client = httpClient,
        apiKey = config.property("claude.apiKey").getString(),
        model = config.property("claude.model").getString()
    )

    configureDatabase()
    configureSerialization()
    configureAuthentication(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString()
    )
    routing {
        authRoutes(googleService, jwtService, userRepo)
        userRoutes(userRepo)
        assessmentRoutes(assessmentRepo)
        planRoutes(claudeService, assessmentRepo, planRepo)
        chatRoutes(claudeService)
        b2bRoutes(userRepo, assessmentRepo)
    }
}
```

- [ ] **Step 6: Run server and smoke test**

```bash
./gradlew :server:run
# In another terminal:
curl -X POST http://localhost:8080/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"invalid"}'
# Expected: 401 {"code":"AUTH_INVALID_TOKEN",...}
```

- [ ] **Step 7: Commit**

```bash
git add server/src/
git commit -m "feat(server): plan generation and chat routes with Claude API SSE streaming"
```

---

## Chunk 3: Shared Module — Domain, Network & Data

### Task 8: Domain Models & CalculateScoresUseCase

**Files:**
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/domain/model/User.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/domain/model/Assessment.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/domain/model/LongevityPlan.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/domain/model/ChatMessage.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/domain/usecase/CalculateScoresUseCase.kt`
- Create: `shared/src/commonTest/kotlin/com/neovita/shared/domain/usecase/CalculateScoresUseCaseTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// CalculateScoresUseCaseTest.kt
class CalculateScoresUseCaseTest {
    private val useCase = CalculateScoresUseCase()

    @Test fun `high scores for ideal inputs`() {
        val scores = useCase("Todos los días", "Pesas o resistencia", "7-8 horas", 9)
        assertTrue(scores.exercise >= 90, "exercise was ${scores.exercise}")
        assertTrue(scores.sleep >= 80, "sleep was ${scores.sleep}")
        assertTrue(scores.overall >= 75, "overall was ${scores.overall}")
    }

    @Test fun `low scores for sedentary poor-sleep inputs`() {
        val scores = useCase("Nunca", "No hago ejercicio", "Menos de 5 horas", 2)
        assertTrue(scores.exercise <= 15)
        assertTrue(scores.sleep <= 25)
    }

    @Test fun `overall is integer average of all three pillars`() {
        val scores = useCase("2-3 veces", "Cardio", "6-8 horas", 6)
        assertEquals((scores.exercise + scores.sleep + scores.nutrition) / 3, scores.overall)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew :shared:testDebugUnitTest --tests "*.CalculateScoresUseCaseTest"
```

- [ ] **Step 3: Define domain models**

`User.kt`:
```kotlin
package com.neovita.shared.domain.model

enum class UserRole { USER, EMPLOYER }

data class User(val id: String, val name: String, val email: String,
                val age: Int, val role: UserRole, val companyId: String?)
```

`Assessment.kt`:
```kotlin
data class PillarScores(val overall: Int, val exercise: Int, val sleep: Int, val nutrition: Int)

data class Assessment(val id: String, val userId: String, val createdAt: Long,
                      val exerciseFrequency: String, val exerciseType: String,
                      val sleepHours: String,    // "4-6" | "6-8" | "8+"
                      val sleepQuality: Int,      // 1-10
                      val mainGoal: String, val scores: PillarScores)
```

`LongevityPlan.kt`:
```kotlin
data class LongevityPlan(val id: String, val userId: String, val generatedAt: Long,
                         val nutrition: List<String>, val sleep: List<String>,
                         val exercise: List<String>, val scores: PillarScores)
```

`ChatMessage.kt`:
```kotlin
enum class MessageRole { USER, ASSISTANT }
data class ChatMessage(val id: String, val role: MessageRole,
                       val content: String, val timestamp: Long)
```

- [ ] **Step 4: Implement `CalculateScoresUseCase.kt`**

```kotlin
package com.neovita.shared.domain.usecase

import com.neovita.shared.domain.model.PillarScores

class CalculateScoresUseCase {
    operator fun invoke(exerciseFrequency: String, exerciseType: String,
                        sleepHours: String, sleepQuality: Int): PillarScores {
        val exerciseFreqScore = when (exerciseFrequency) {
            "Todos los días" -> 100; "4-5 veces" -> 85; "2-3 veces" -> 65
            "1 vez" -> 40; else -> 10
        }
        val exerciseTypeBonus = if (exerciseType == "Pesas o resistencia") 5 else 0
        val exercise = (exerciseFreqScore + exerciseTypeBonus).coerceAtMost(100)

        val sleepHoursScore = when (sleepHours) {
            "7-8 horas", "8+" -> 90; "6-7 horas", "6-8 horas" -> 70
            "5-6 horas" -> 45; else -> 15
        }
        val sleep = ((sleepHoursScore + (sleepQuality * 10)) / 2)
        val nutrition = 60  // Baseline — not assessed in MVP
        return PillarScores(overall = (exercise + sleep + nutrition) / 3,
                            exercise = exercise, sleep = sleep, nutrition = nutrition)
    }
}
```

- [ ] **Step 5: Run test — expect PASS**

```bash
./gradlew :shared:testDebugUnitTest --tests "*.CalculateScoresUseCaseTest"
```

- [ ] **Step 6: Commit**

```bash
git add shared/src/
git commit -m "feat(shared): domain models and CalculateScoresUseCase with tests"
```

---

### Task 9: Network Layer — ApiService + DTOs

**Files:**
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/network/dto/` (all DTO files)
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/network/error/NetworkError.kt`
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt`
- Create: `shared/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt`

- [ ] **Step 1: Write failing ApiService test**

```kotlin
// ApiServiceTest.kt
class ApiServiceTest {
    private val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/auth/google" -> respond(
                content = """{"token":"jwt-abc","isNewUser":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
            else -> respond("Not Found", HttpStatusCode.NotFound)
        }
    }

    private val apiService = ApiService(
        baseUrl = "http://localhost",
        httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
    )

    @Test fun `authenticateWithGoogle returns token`() = runTest {
        val result = apiService.authenticateWithGoogle("any-token")
        assertTrue(result.isSuccess)
        assertEquals("jwt-abc", result.getOrNull()?.token)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew :shared:testDebugUnitTest --tests "*.ApiServiceTest"
```

- [ ] **Step 3: Define DTOs**

```kotlin
// AuthDto.kt
@Serializable data class GoogleAuthRequest(val idToken: String)
@Serializable data class AuthResponse(val token: String, val isNewUser: Boolean)

// UserDto.kt
@Serializable data class UserDto(val id: String, val name: String, val email: String,
                                  val age: Int, val role: String, val companyId: String? = null)
@Serializable data class PatchUserRequest(val name: String? = null, val age: Int? = null)

// AssessmentDto.kt
@Serializable data class AssessmentRequest(val exerciseFrequency: String, val exerciseType: String,
                                            val sleepHours: String, val sleepQuality: Int, val mainGoal: String)
@Serializable data class PillarScoresDto(val overall: Int, val exercise: Int, val sleep: Int, val nutrition: Int)
@Serializable data class AssessmentResponse(val id: String, val userId: String, val createdAt: Long,
                                             val exerciseFrequency: String, val exerciseType: String,
                                             val sleepHours: String, val sleepQuality: Int,
                                             val mainGoal: String, val scores: PillarScoresDto)

// ChatDto.kt
@Serializable data class ChatMessageDto(val role: String, val content: String)
@Serializable data class ChatRequest(val messages: List<ChatMessageDto>)

// ErrorDto.kt
@Serializable data class ApiError(val code: String, val message: String)
```

- [ ] **Step 4: Implement `NetworkError.kt`**

```kotlin
package com.neovita.shared.network.error

sealed class NetworkError : Exception() {
    data object Unauthorized : NetworkError()
    data object NotFound : NetworkError()
    data class ServerError(val code: String, val msg: String) : NetworkError()
    data class Unknown(override val cause: Throwable?) : NetworkError()
}
```

- [ ] **Step 5: Implement `ApiService.kt`**

```kotlin
package com.neovita.shared.network

import com.neovita.shared.network.dto.*
import com.neovita.shared.network.error.NetworkError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ApiService(private val baseUrl: String, private val httpClient: HttpClient) {

    private var token: String? = null
    fun setToken(t: String) { token = t }

    suspend fun authenticateWithGoogle(idToken: String): Result<AuthResponse> = safeCall {
        httpClient.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleAuthRequest(idToken))
        }.body()
    }

    suspend fun getMe(): Result<UserDto> = safeCall {
        httpClient.get("$baseUrl/users/me") { bearerAuth() }.body()
    }

    suspend fun patchMe(name: String? = null, age: Int? = null): Result<UserDto> = safeCall {
        httpClient.patch("$baseUrl/users/me") {
            bearerAuth(); contentType(ContentType.Application.Json)
            setBody(PatchUserRequest(name, age))
        }.body()
    }

    suspend fun saveAssessment(req: AssessmentRequest): Result<AssessmentResponse> = safeCall {
        httpClient.post("$baseUrl/assessments") {
            bearerAuth(); contentType(ContentType.Application.Json); setBody(req)
        }.body()
    }

    suspend fun getLatestAssessment(): Result<AssessmentResponse> = safeCall {
        httpClient.get("$baseUrl/assessments/latest") { bearerAuth() }.body()
    }

    fun streamPlanGeneration(): Flow<String> = sseFlow("$baseUrl/plans/generate", method = HttpMethod.Post)

    fun streamChat(messages: List<ChatMessageDto>): Flow<String> =
        sseFlow("$baseUrl/chat", method = HttpMethod.Post, body = ChatRequest(messages))

    private fun sseFlow(url: String, method: HttpMethod, body: Any? = null): Flow<String> = flow {
        httpClient.prepareRequest(url) {
            this.method = method
            bearerAuth()
            body?.let { setBody(it) }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ") && line != "data: [DONE]") {
                    emit(line.removePrefix("data: "))
                }
            }
        }
    }

    private fun HttpRequestBuilder.bearerAuth() {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = runCatching { block() }
        .recoverCatching { e ->
            when {
                e.message?.contains("401") == true -> throw NetworkError.Unauthorized
                e.message?.contains("404") == true -> throw NetworkError.NotFound
                else -> throw NetworkError.Unknown(e)
            }
        }
}
```

- [ ] **Step 6: Run test — expect PASS**

```bash
./gradlew :shared:testDebugUnitTest --tests "*.ApiServiceTest"
```

- [ ] **Step 7: Commit**

```bash
git add shared/src/
git commit -m "feat(shared): network layer with ApiService, DTOs and typed NetworkError"
```

---

### Task 10: Repository Implementations & SQLDelight Cache

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/neovita/shared/db/NeoVita.sq`
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/data/repository/` (all 4 impls)
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/data/mapper/` (mappers)
- Create: `shared/src/commonMain/kotlin/com/neovita/shared/di/SharedModule.kt`

- [ ] **Step 1: Write SQLDelight schema**

`shared/src/commonMain/sqldelight/com/neovita/shared/db/NeoVita.sq`:
```sql
CREATE TABLE CachedAssessment (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    exerciseFrequency TEXT NOT NULL,
    exerciseType TEXT NOT NULL,
    sleepHours TEXT NOT NULL,
    sleepQuality INTEGER NOT NULL,
    mainGoal TEXT NOT NULL,
    scoresJson TEXT NOT NULL
);

insertAssessment:
INSERT OR REPLACE INTO CachedAssessment VALUES (?,?,?,?,?,?,?,?,?);

getLatestAssessment:
SELECT * FROM CachedAssessment WHERE userId = ? ORDER BY createdAt DESC LIMIT 1;

CREATE TABLE CachedPlan (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    generatedAt INTEGER NOT NULL,
    planJson TEXT NOT NULL
);

insertPlan:
INSERT OR REPLACE INTO CachedPlan VALUES (?,?,?,?);

getCurrentPlan:
SELECT * FROM CachedPlan WHERE userId = ? ORDER BY generatedAt DESC LIMIT 1;
```

- [ ] **Step 2: Generate SQLDelight code**

```bash
./gradlew :shared:generateCommonMainNeoVitaDatabaseInterface
```

- [ ] **Step 3: Implement `AssessmentRepositoryImpl.kt`**

```kotlin
package com.neovita.shared.data.repository

import com.neovita.shared.db.NeoVitaDatabase
import com.neovita.shared.domain.model.Assessment
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.AssessmentRequest
import com.neovita.shared.data.mapper.toDomain

class AssessmentRepositoryImpl(
    private val apiService: ApiService,
    private val db: NeoVitaDatabase
) : AssessmentRepository {

    override suspend fun saveAssessment(exerciseFrequency: String, exerciseType: String,
                                        sleepHours: String, sleepQuality: Int, mainGoal: String): Result<Assessment> {
        val req = AssessmentRequest(exerciseFrequency, exerciseType, sleepHours, sleepQuality, mainGoal)
        return apiService.saveAssessment(req).map { dto ->
            db.neoVitaDatabaseQueries.insertAssessment(
                dto.id, dto.userId, dto.createdAt, dto.exerciseFrequency, dto.exerciseType,
                dto.sleepHours, dto.sleepQuality.toLong(), dto.mainGoal,
                dto.scores.let { """{"overall":${it.overall},"exercise":${it.exercise},"sleep":${it.sleep},"nutrition":${it.nutrition}}""" }
            )
            dto.toDomain()
        }
    }

    override suspend fun getLatestAssessment(userId: String): Assessment? {
        // Try network first, fallback to cache
        val network = apiService.getLatestAssessment().getOrNull()
        if (network != null) return network.toDomain()
        return db.neoVitaDatabaseQueries.getLatestAssessment(userId).executeAsOneOrNull()?.toDomain()
    }
}
```

- [ ] **Step 3b: Define remaining repository interfaces and implementations**

`shared/src/commonMain/kotlin/com/neovita/shared/domain/repository/ChatRepository.kt`:
```kotlin
package com.neovita.shared.domain.repository

import com.neovita.shared.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun sendMessage(history: List<ChatMessage>): Flow<String>
}
```

`shared/src/commonMain/kotlin/com/neovita/shared/data/repository/ChatRepositoryImpl.kt`:
```kotlin
package com.neovita.shared.data.repository

import com.neovita.shared.domain.model.ChatMessage
import com.neovita.shared.domain.model.MessageRole
import com.neovita.shared.domain.repository.ChatRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.ChatMessageDto
import kotlinx.coroutines.flow.Flow

class ChatRepositoryImpl(private val apiService: ApiService) : ChatRepository {
    override fun sendMessage(history: List<ChatMessage>): Flow<String> {
        val dtos = history.map { ChatMessageDto(
            role = if (it.role == MessageRole.USER) "user" else "assistant",
            content = it.content
        )}
        return apiService.streamChat(dtos)
    }
}
```

Also define `UserRepository` interface and `PlanRepository` interface in domain layer:

`domain/repository/UserRepository.kt`:
```kotlin
interface UserRepository {
    suspend fun getMe(): Result<UserDto>
    suspend fun updateMe(name: String? = null, age: Int? = null): Result<UserDto>
}
```

`domain/repository/PlanRepository.kt`:
```kotlin
interface PlanRepository {
    suspend fun getCurrent(): Result<LongevityPlan?>
    fun streamGenerate(): Flow<String>
}
```

- [ ] **Step 4: Write `SharedModule.kt` (Koin DI)**

```kotlin
package com.neovita.shared.di

import com.neovita.shared.data.repository.*
import com.neovita.shared.domain.repository.*
import com.neovita.shared.domain.usecase.CalculateScoresUseCase
import com.neovita.shared.network.ApiService
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.dsl.module

fun sharedModule(baseUrl: String) = module {
    single {
        HttpClient {
            install(ContentNegotiation) { json() }
        }
    }
    single { ApiService(baseUrl, get()) }
    single<AssessmentRepository> { AssessmentRepositoryImpl(get(), get()) }
    single<PlanRepository> { PlanRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
    factory { CalculateScoresUseCase() }
}
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/
git commit -m "feat(shared): SQLDelight cache, repository implementations and Koin DI module"
```

---

## Chunk 4: ComposeApp — Theme, Navigation & Auth

### Task 11: NeoVita Theme & Shared Components

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/theme/Color.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/theme/Type.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/theme/Theme.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/ScoreRing.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/NeoProgressBar.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/ChatBubble.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/ErrorBanner.kt`

- [ ] **Step 1: Define brand colors and typography**

`Color.kt`:
```kotlin
package com.neovita.app.ui.theme

import androidx.compose.ui.graphics.Color

val NeoTeal900 = Color(0xFF095F55)
val NeoTeal700 = Color(0xFF0D7B6E)
val NeoTeal500 = Color(0xFF14A090)
val NeoTeal200 = Color(0xFFB2DED9)
val NeoNavy  = Color(0xFF1A3A4A)
val NeoAmber = Color(0xFFF59E0B)
val NeoRed   = Color(0xFFEF4444)
val NeoBg    = Color(0xFFF0F4F3)
val NeoSurface = Color(0xFFFFFFFF)
```

`Theme.kt`:
```kotlin
@Composable
fun NeoVitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NeoTeal700,
            onPrimary = Color.White,
            background = NeoBg,
            surface = NeoSurface,
            secondary = NeoNavy
        ),
        typography = NeoTypography,
        content = content
    )
}
```

- [ ] **Step 2: Implement `ScoreRing.kt`**

```kotlin
@Composable
fun ScoreRing(score: Int, size: Dp = 96.dp, label: String = "", modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> NeoTeal700
        score >= 60 -> NeoAmber
        else -> NeoRed
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.1f
            drawArc(Color.LightGray.copy(alpha = 0.3f), -90f, 360f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke))
            drawArc(color, -90f, 360f * score / 100f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.titleLarge,
                color = color, fontWeight = FontWeight.Bold)
            if (label.isNotEmpty())
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
```

- [ ] **Step 3: Implement `ErrorBanner.kt`**

```kotlin
@Composable
fun ErrorBanner(message: String, onDismiss: (() -> Unit)? = null,
                onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message.isNotEmpty(), modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().background(NeoNavy).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = Color.White, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            onRetry?.let {
                TextButton(onClick = it) { Text("Reintentar", color = NeoTeal200) }
            }
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/neovita/app/ui/
git commit -m "feat(ui): NeoVita theme and shared components (ScoreRing, ErrorBanner)"
```

---

### Task 12: Google Sign-In (expect/actual) & App Navigation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/auth/GoogleSignIn.kt`
- Create: `composeApp/src/androidMain/kotlin/com/neovita/app/auth/GoogleSignIn.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/neovita/app/auth/GoogleSignIn.ios.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/com/neovita/app/auth/GoogleSignIn.wasmJs.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/AppNavigation.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/App.kt`

- [ ] **Step 1: Define expect interface for Google Sign-In**

`auth/GoogleSignIn.kt` (commonMain):
```kotlin
package com.neovita.app.auth

data class GoogleSignInResult(val idToken: String?, val error: String?)

expect class GoogleSignInClient {
    suspend fun signIn(): GoogleSignInResult
    suspend fun signOut()
}
```

- [ ] **Step 2: Android actual implementation**

`auth/GoogleSignIn.android.kt`:
```kotlin
actual class GoogleSignInClient {
    // Uses Google Sign-In for Android SDK (com.google.android.gms:play-services-auth)
    // Add to androidMain dependencies: implementation("com.google.android.gms:play-services-auth:21.2.0")
    actual suspend fun signIn(): GoogleSignInResult {
        // Launch Google sign-in intent, await result, extract ID token
        // Full implementation uses Activity result API or rememberLauncherForActivityResult
        TODO("Implement with play-services-auth")
    }
    actual suspend fun signOut(): Unit = TODO("signOut via GoogleSignIn.getClient(...).signOut()")
}
```

> See: https://developers.google.com/identity/sign-in/android/start-integrating

- [ ] **Step 3: iOS actual implementation**

`auth/GoogleSignIn.ios.kt`:
```kotlin
actual class GoogleSignInClient {
    // Uses GoogleSignIn-iOS SDK (added via CocoaPods in iosApp/Podfile)
    // pod 'GoogleSignIn', '~> 7.0'
    actual suspend fun signIn(): GoogleSignInResult = TODO("GIDSignIn.sharedInstance.signIn")
    actual suspend fun signOut(): Unit = TODO("GIDSignIn.sharedInstance.signOut()")
}
```

> See: https://developers.google.com/identity/sign-in/ios/start-integrating

- [ ] **Step 4: Web actual implementation**

`auth/GoogleSignIn.wasmJs.kt`:
```kotlin
actual class GoogleSignInClient {
    // Uses Google Identity Services JS library via JS interop
    // Add <script src="https://accounts.google.com/gsi/client"> to index.html
    actual suspend fun signIn(): GoogleSignInResult = TODO("google.accounts.id.prompt()")
    actual suspend fun signOut(): GoogleSignInResult = GoogleSignInResult(null, null)
}
```

- [ ] **Step 5: Implement `AppNavigation.kt`**

```kotlin
package com.neovita.app.navigation

import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.*
import com.neovita.app.screens.login.LoginScreen
import com.neovita.app.screens.dashboard.DashboardTab
import com.neovita.app.screens.plan.PlanTab
import com.neovita.app.screens.chat.ChatTab
import com.neovita.app.screens.b2b.B2BTab
import com.neovita.app.screens.profile.ProfileTab

@Composable
fun AppNavigation(isLoggedIn: Boolean, isNewUser: Boolean, isEmployer: Boolean) {
    if (!isLoggedIn) {
        Navigator(LoginScreen())
        return
    }
    if (isNewUser) {
        Navigator(OnboardingScreen())
        return
    }
    val tabs = buildList {
        add(DashboardTab); add(PlanTab); add(ChatTab)
        if (isEmployer) add(B2BTab)
        add(ProfileTab)
    }
    TabNavigator(tabs.first()) {
        Scaffold(bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = LocalTabNavigator.current.current == tab,
                        onClick = { LocalTabNavigator.current.current = tab },
                        icon = { Icon(tab.options.icon!!, tab.options.title) },
                        label = { Text(tab.options.title) }
                    )
                }
            }
        }) { CurrentTab() }
    }
}
```

- [ ] **Step 6: Write `App.kt`**

```kotlin
package com.neovita.app

import com.neovita.app.navigation.AppNavigation
import com.neovita.app.ui.theme.NeoVitaTheme
import com.neovita.shared.di.sharedModule
import org.koin.compose.KoinApplication

@Composable
fun App(baseUrl: String = "http://localhost:8080") {
    KoinApplication(application = { modules(sharedModule(baseUrl), appModule()) }) {
        NeoVitaTheme {
            // AppViewModel reads stored JWT from SQLDelight to determine initial state
            val appVm = koinViewModel<AppViewModel>()
            val state by appVm.state.collectAsState()
            AppNavigation(
                isLoggedIn = state.isLoggedIn,
                isNewUser = state.isNewUser,
                isEmployer = state.isEmployer
            )
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/
git commit -m "feat(app): Google Sign-In expect/actual and Voyager navigation structure"
```

---

## Chunk 5: ComposeApp — All Screens

### Task 13: Login & Onboarding Screens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/login/LoginViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/login/LoginScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/onboarding/OnboardingViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Implement `LoginViewModel.kt`**

```kotlin
class LoginViewModel(
    private val apiService: ApiService,
    private val googleSignInClient: GoogleSignInClient
) : ScreenModel {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signInWithGoogle() {
        _state.update { it.copy(isLoading = true, error = null) }
        screenModelScope.launch {
            val result = googleSignInClient.signIn()
            if (result.idToken == null) {
                _state.update { it.copy(isLoading = false, error = result.error ?: "Error al iniciar sesión") }
                return@launch
            }
            apiService.authenticateWithGoogle(result.idToken)
                .onSuccess { auth ->
                    apiService.setToken(auth.token)
                    _state.update { it.copy(isLoading = false, success = auth) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = "Error de conexión") }
                }
        }
    }
}

data class LoginState(val isLoading: Boolean = false,
                      val success: AuthResponse? = null,
                      val error: String? = null)
```

- [ ] **Step 2: Implement `LoginScreen.kt`**

```kotlin
class LoginScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<LoginViewModel>()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state.success) {
            state.success?.let { auth ->
                navigator.replace(if (auth.isNewUser) OnboardingScreen() else DashboardTab)
            }
        }

        Column(Modifier.fillMaxSize().background(NeoBg).padding(32.dp),
               horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.Center) {
            // Logo
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                .background(brush = Brush.linearGradient(listOf(NeoTeal900, NeoTeal500))),
                contentAlignment = Alignment.Center) {
                Text("🌿", style = MaterialTheme.typography.displaySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text("NeoVita", style = MaterialTheme.typography.headlineLarge,
                color = NeoTeal900, fontWeight = FontWeight.Bold)
            Text("Coaching Integral con IA para la Longevidad",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))

            if (state.isLoading) {
                CircularProgressIndicator(color = NeoTeal700)
            } else {
                Button(onClick = vm::signInWithGoogle,
                       modifier = Modifier.fillMaxWidth().height(52.dp),
                       colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)) {
                    Text("Continuar con Google", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
```

- [ ] **Step 3: Implement `OnboardingViewModel.kt`**

```kotlin
data class OnboardingState(val name: String = "", val age: String = "",
                            val isLoading: Boolean = false, val error: String? = null,
                            val done: Boolean = false)

class OnboardingViewModel(private val userRepo: UserRepository) : ScreenModel {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onAgeChange(v: String) = _state.update { it.copy(age = v) }

    fun save() {
        val age = _state.value.age.toIntOrNull()
        if (_state.value.name.isBlank() || age == null || age < 18) {
            _state.update { it.copy(error = "Ingresa un nombre y edad válida (mínimo 18 años)") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        screenModelScope.launch {
            userRepo.updateMe(name = _state.value.name, age = age)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { _state.update { it.copy(isLoading = false, error = "Error al guardar") } }
        }
    }
}
```

`OnboardingScreen.kt`:
```kotlin
class OnboardingScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<OnboardingViewModel>()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state.done) { if (state.done) navigator.replace(AssessmentScreen()) }

        Column(Modifier.fillMaxSize().background(NeoBg).padding(32.dp),
               verticalArrangement = Arrangement.Center) {
            Text("¡Hola! Cuéntanos sobre ti", style = MaterialTheme.typography.headlineMedium,
                 color = NeoNavy, fontWeight = FontWeight.Bold)
            Text("Solo lo básico para personalizar tu experiencia",
                 style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(value = state.name, onValueChange = vm::onNameChange,
                label = { Text("Tu nombre") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = state.age, onValueChange = vm::onAgeChange,
                label = { Text("Tu edad") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true)
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(32.dp))
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp),
                   enabled = !state.isLoading,
                   colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)) {
                if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Continuar →", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/
git commit -m "feat(screens): Login and Onboarding screens with ViewModels"
```

---

### Task 14: Assessment & Results Screens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/assessment/AssessmentViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/assessment/AssessmentScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/results/ResultsViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/results/ResultsScreen.kt`

- [ ] **Step 1: Implement `AssessmentViewModel.kt`**

```kotlin
data class AssessmentState(
    val currentQuestion: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: AssessmentResponse? = null
)

val QUESTIONS = listOf(
    Question("exercise_frequency", "¿Cuántas veces a la semana haces ejercicio?",
        listOf("Todos los días","4-5 veces","2-3 veces","1 vez","Nunca")),
    Question("exercise_type", "¿Qué tipo de ejercicio haces principalmente?",
        listOf("Cardio (caminar, correr, ciclismo)","Pesas o resistencia","Yoga o pilates",
               "Deportes de equipo","No hago ejercicio")),
    Question("sleep_hours", "¿Cuántas horas duermes por noche?",
        listOf("8+ horas","7-8 horas","6-7 horas","5-6 horas","Menos de 5 horas")),
    Question("sleep_quality", "¿Cómo calificarías la calidad de tu sueño? (1-10)", type = "slider"),
    Question("main_goal", "¿Cuál es tu principal objetivo de longevidad?",
        listOf("Aumentar energía y vitalidad","Mejorar memoria y función cognitiva",
               "Reducir riesgo de enfermedades","Bajar de peso saludablemente",
               "Manejar el estrés y bienestar mental"))
)

class AssessmentViewModel(private val assessmentRepo: AssessmentRepository) : ScreenModel {
    private val _state = MutableStateFlow(AssessmentState())
    val state = _state.asStateFlow()

    fun answer(questionId: String, value: String) {
        val newAnswers = _state.value.answers + (questionId to value)
        val nextQuestion = _state.value.currentQuestion + 1
        if (nextQuestion >= QUESTIONS.size) {
            submitAssessment(newAnswers)
        } else {
            _state.update { it.copy(answers = newAnswers, currentQuestion = nextQuestion) }
        }
    }

    private fun submitAssessment(answers: Map<String, String>) {
        _state.update { it.copy(isLoading = true) }
        screenModelScope.launch {
            assessmentRepo.saveAssessment(
                exerciseFrequency = answers["exercise_frequency"] ?: "",
                exerciseType = answers["exercise_type"] ?: "",
                sleepHours = answers["sleep_hours"] ?: "",
                sleepQuality = answers["sleep_quality"]?.toIntOrNull() ?: 5,
                mainGoal = answers["main_goal"] ?: ""
            ).onSuccess { assessment ->
                _state.update { it.copy(isLoading = false, completed = assessment) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = "Error al guardar evaluación") }
            }
        }
    }
}
```

- [ ] **Step 2: Implement `AssessmentScreen.kt`** — shows one question at a time with animated progress, option pills for single-select, a slider for sleep quality

- [ ] **Step 3: Implement `ResultsScreen.kt`** — displays `ScoreRing` for overall + each pillar, `NeoProgressBar` for each, and a "Ver mi plan" CTA button

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/
git commit -m "feat(screens): Assessment and Results screens"
```

---

### Task 15: Dashboard, Plan & Chat Screens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/dashboard/`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/plan/`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/chat/`

- [ ] **Step 1: Implement `DashboardViewModel.kt`**

```kotlin
data class DashboardState(val user: UserDto? = null, val plan: LongevityPlan? = null,
                          val isLoading: Boolean = false, val error: String? = null)

class DashboardViewModel(private val userRepo: UserRepository,
                          private val planRepo: PlanRepository) : ScreenModel {
    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init { load() }

    private fun load() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val user = userRepo.getMe().getOrNull()
            val plan = planRepo.getCurrent().getOrNull()
            _state.update { it.copy(user = user, plan = plan, isLoading = false) }
        }
    }
}
```

- [ ] **Step 2: Implement `DashboardScreen.kt`** — greeting with user name, large ScoreRing for overall score, quick-access cards for the 3 pillars, today's task list from the plan

- [ ] **Step 3: Implement `ChatViewModel.kt`**

```kotlin
data class ChatState(val messages: List<ChatMessage> = emptyList(),
                     val inputText: String = "",
                     val isStreaming: Boolean = false,
                     val error: String? = null)

class ChatViewModel(private val chatRepo: ChatRepository) : ScreenModel {
    private val _state = MutableStateFlow(ChatState(
        messages = listOf(ChatMessage("init", MessageRole.ASSISTANT,
            "¡Hola! Soy tu coach de longevidad NeoVita. ¿En qué puedo ayudarte hoy?",
            System.currentTimeMillis()))
    ))
    val state = _state.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return
        val userMsg = ChatMessage(uuid(), MessageRole.USER, text, System.currentTimeMillis())
        val assistantMsg = ChatMessage(uuid(), MessageRole.ASSISTANT, "", System.currentTimeMillis())
        _state.update { it.copy(
            messages = it.messages + userMsg + assistantMsg,
            inputText = "",
            isStreaming = true,
            error = null
        )}
        screenModelScope.launch {
            chatRepo.sendMessage(_state.value.messages.dropLast(1))
                .catch { e ->
                    _state.update { s -> s.copy(isStreaming = false,
                        error = "Coach no disponible, intenta más tarde") }
                }
                .collect { chunk ->
                    _state.update { s ->
                        val updated = s.messages.dropLast(1) +
                            assistantMsg.copy(content = assistantMsg.content + chunk)
                        s.copy(messages = updated)
                    }
                }
            _state.update { it.copy(isStreaming = false) }
        }
    }

    fun updateInput(text: String) = _state.update { it.copy(inputText = text) }
}
```

- [ ] **Step 4: Implement `ChatScreen.kt`** — scrollable message list with `ChatBubble`, suggestion chips (Nutrición, Ejercicio, Sueño), multiline text input, send button disabled while streaming, error banner

- [ ] **Step 5: Implement `PlanScreen.kt`** — shows plan sections (nutrition/sleep/exercise) as expandable cards with bullet recommendations. Has a "Regenerar plan" button that triggers SSE streaming and shows a loading indicator while generating.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/
git commit -m "feat(screens): Dashboard, Chat and Plan screens with streaming support"
```

---

### Task 16: B2B & Profile Screens + Platform Entry Points

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/b2b/`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/profile/`
- Create: `composeApp/src/androidMain/kotlin/com/neovita/app/MainActivity.kt`
- Create: `composeApp/src/iosMain/kotlin/com/neovita/app/MainViewController.kt`
- Create: `composeApp/src/wasmJsMain/kotlin/com/neovita/app/main.kt`

- [ ] **Step 1: Implement `B2BScreen.kt`**

Shows employer dashboard:
- Summary stats: average team score, % active users, total employees
- Employee list with name, email, and color-coded score badge (green ≥80, amber 60-79, red <60)
- Sorting by score ascending to highlight at-risk employees

- [ ] **Step 2: Implement `ProfileScreen.kt`**

Shows: avatar, user name/email, age, re-assessment CTA, past assessments list (date + overall score), logout button.

- [ ] **Step 3: Platform entry points**

`MainActivity.kt` (Android):
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(baseUrl = BuildConfig.SERVER_URL) }
    }
}
```

`MainViewController.kt` (iOS):
```kotlin
fun MainViewController() = ComposeUIViewController { App(baseUrl = "http://localhost:8080") }
```

`main.kt` (Web/WASM):
```kotlin
fun main() {
    onWasmReady {
        CanvasBasedWindow("NeoVita") { App(baseUrl = js("window.SERVER_URL") as? String ?: "/api") }
    }
}
```

- [ ] **Step 4: Run on Android emulator**

```bash
./gradlew :composeApp:installDebug
# Or in Android Studio: Run → Run 'composeApp'
```
Expected: app launches, shows NeoVita login screen with Google Sign-In button.

- [ ] **Step 5: Run on iOS Simulator**

```bash
./gradlew :composeApp:iosSimulatorArm64Test
# Or open iosApp/iosApp.xcworkspace in Xcode → Run
```

- [ ] **Step 6: Run Web**

```bash
./gradlew :composeApp:wasmJsBrowserRun
```
Expected: opens browser at `http://localhost:8080` with the app.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/
git commit -m "feat(screens): B2B, Profile screens and platform entry points"
```

---

## Chunk 6: Integration & Polish

### Task 17: End-to-End Integration Test

**Files:**
- Create: `server/src/test/kotlin/com/neovita/server/integration/FullFlowTest.kt`

- [ ] **Step 1: Write integration test for critical path**

```kotlin
// Tests: auth → assessment → plan generation (mocked Claude)
class FullFlowTest {
    @Test fun `user can complete full onboarding flow`() = testApplication {
        application { module() }  // Uses in-memory H2 for tests
        // 1. Auth with mocked Google token
        val authResp = client.post("/auth/google") {
            setBody("""{"idToken":"test-token"}""")
        }
        assertEquals(200, authResp.status.value)
        val token = Json.parseToJsonElement(authResp.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        // 2. Save assessment
        val assessResp = client.post("/assessments") {
            bearerAuth(token)
            setBody("""{"exerciseFrequency":"2-3 veces","exerciseType":"Cardio",
                        "sleepHours":"6-8 horas","sleepQuality":7,"mainGoal":"Aumentar energía"}""")
        }
        assertEquals(201, assessResp.status.value)
        val scores = Json.parseToJsonElement(assessResp.bodyAsText())
            .jsonObject["scores"]!!.jsonObject
        assertTrue(scores["exercise"]!!.jsonPrimitive.int > 0)
    }
}
```

- [ ] **Step 2: Run integration test**

```bash
./gradlew :server:test --tests "*.FullFlowTest"
```

- [ ] **Step 3: Fix any integration issues found**

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "test(server): full-flow integration test for critical auth+assessment path"
```

---

### Task 18: Environment Config & README

**Files:**
- Create: `.env.example`
- Create: `README.md` (only if it doesn't exist)
- Create: `.gitignore`

- [ ] **Step 1: Write `.env.example`**

```bash
# Server
DB_URL=jdbc:postgresql://localhost:5432/neovita?user=neovita&password=secret
JWT_SECRET=change-this-in-production-use-32-char-minimum
CLAUDE_API_KEY=sk-ant-...

# App (Android buildConfig / iOS Info.plist / Web window.SERVER_URL)
SERVER_URL=http://10.0.2.2:8080   # Android emulator → localhost
```

- [ ] **Step 2: Write `.gitignore`**

```gitignore
.gradle/
build/
*.local.properties
.env
local.properties
.DS_Store
*.xcworkspace/xcuserdata/
Pods/
.superpowers/
```

- [ ] **Step 3: Write quick-start in README**

```markdown
## Quick Start

### Prerequisites
- JDK 17+, Android Studio Hedgehog+, Xcode 15+ (for iOS)
- Docker (for PostgreSQL)
- Claude API key from console.anthropic.com

### Run backend
cp .env.example .env  # fill in your values
docker compose up -d  # starts PostgreSQL
./gradlew :server:run

### Run Android
./gradlew :composeApp:installDebug

### Run iOS
open iosApp/iosApp.xcworkspace  # → Run in Xcode

### Run Web
./gradlew :composeApp:wasmJsBrowserRun
```

- [ ] **Step 4: Final commit**

```bash
git add .env.example .gitignore README.md
git commit -m "chore: environment config, gitignore and quick-start README"
```

---

## Summary

| Chunk | Tasks | Key Output |
|-------|-------|------------|
| 1 | 1-2 | Gradle monorepo, version catalog, 3 modules |
| 2 | 3-7 | Full Ktor server: auth, DB, all routes, Claude SSE |
| 3 | 8-10 | KMP shared layer: domain, network, SQLDelight cache |
| 4 | 11-12 | Theme, components, navigation, Google Sign-In |
| 5 | 13-16 | All 9 screens on Android + iOS + Web |
| 6 | 17-18 | Integration test, env config |

**Total commits:** ~18 focused commits, one per task.
**Running order:** Chunk 1 → 2 → 3 → 4 → 5 → 6. Chunks 2 and 3 can be partially parallelized (server and shared don't depend on each other).
