# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## App

**NeoVita** — "Coaching Integral con IA para la Longevidad". Full-stack Kotlin Multiplatform:
Android, iOS, Web (wasmJs) clients + a Ktor server that also serves the web app. Package:
`com.neovita.app` (UI) / `com.neovita.shared` (core lib) / `com.neovita.server` (backend).

## Project structure (2026-05 KMP split layout)

```
NeoVita/
├── core/         Pure KMP lib — ApiService, domain, repositories, SQLDelight DB. Package com.neovita.shared.*
├── shared/       Compose Multiplatform UI lib — android(lib)+ios+wasmJs. Produces ComposeApp XCFramework. api(:core)
├── androidApp/   com.android.application — MainActivity. deps :shared, :core, sqldelight-android-driver
├── webApp/       wasmJs executable — main() + index.html. moduleName/outputFileName = composeApp
├── server/       Ktor JVM (Netty) — REST under /api, serves wasm web from /, /health. Standalone (Exposed/Postgres).
└── iosApp/       Swift shell embedding ComposeApp.xcframework (PRODUCT_BUNDLE_IDENTIFIER = com.neovita.app)
```

Namespaces must differ per module: core=`com.neovita.shared`, shared(UI)=`com.neovita.app`,
androidApp=`com.neovita.app.android` (applicationId stays `com.neovita.app`; the manifest
declares the activity by its fully-qualified name `com.neovita.app.MainActivity`).

## SQLDelight ↔ wasmJs (important)

SQLDelight 2.0.2 has **no wasmJs variant**. To keep the web target buildable:
- `core` excludes the `app.cash.sqldelight` group from every `wasmJs*` configuration and moves
  the generated DB code from `commonMain` to `nonWasmMain` (android+ios only) via an
  `applyDefaultHierarchyTemplate { common { group("nonWasm") {...} } }` intermediate.
- Local caching is behind the `LocalCache` interface (`core/commonMain/.../data/cache/`).
  `SqlDelightLocalCache` (nonWasmMain) is the android/ios impl; the web passes `cache = null`.
- `App(baseUrl, cache: LocalCache? = null)` and `sharedModule(baseUrl, cache)` thread the cache;
  repos resolve it with Koin `getOrNull()`.

Do **not** put `System.currentTimeMillis()` / `java.*` in commonMain — use `kotlinx.datetime.Clock`.

## Commands

```bash
# Server (port 8080; /health, API under /api)
./gradlew :server:run
./gradlew :server:buildFatJar                 # → server/build/libs/server-all.jar

# Web (Compose/Wasm)
./gradlew :webApp:wasmJsBrowserDevelopmentRun
./gradlew :webApp:wasmJsBrowserDistribution   # production bundle (Docker uses this)

# Android (installDebug is unreliable headless — assemble + adb install)
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk

# iOS — build the framework before opening Xcode
./gradlew :shared:assembleComposeAppDebugXCFramework   # → shared/build/XCFrameworks/debug/ComposeApp.xcframework
open iosApp/iosApp.xcodeproj

# Tests
./gradlew :server:test
./gradlew :core:testDebugUnitTest
```

The machine has no JBR pin for NeoVita; JBR 21 or OpenJDK 21 both build it (`jvmTarget = 17`).
Android SDK at `$ANDROID_HOME`; AVDs `Pixel_8_Pro` / `Pixel_9_Pro` (Intel x86_64).

## Deploy (Railway)

`railway.toml` + `Dockerfile` (multi-stage, `gradle:8.10-jdk17`): build wasmJs distribution →
copy into `server/src/main/resources/static` → `:server:buildFatJar` → run on
`eclipse-temurin:17-jre-alpine`. Server binds `0.0.0.0:$PORT`, serves the web app at `/`, API
under `/api`, health at `/health`. Required service env vars: `DB_URL` (JDBC), `JWT_SECRET`,
`CLAUDE_API_KEY`, `GOOGLE_CLIENT_ID` (web/Android sign-in + aud check). Optional: `APP_FEATURES`,
`MIN_VERSION_ANDROID`, `MIN_VERSION_IOS`, `MAINTENANCE_MODE` (remote config gate),
`FIREBASE_API_KEY`/`APP_ID`/`PROJECT_ID`/`SENDER_ID` (client values, activate dormant push),
`FIREBASE_SERVICE_ACCOUNT` (secret, enables sending). Connect the GitHub repo in Railway for
auto-deploy on push.

## Conventions

- New REST endpoints: `fun Route.xxx(...)` in `server/.../routes/`, registered inside the
  `route("/api") { ... }` block in `plugins/Routing.kt`.
- New shared wire models: `core/commonMain/.../network/dto/` (`@Serializable`).
- Platform Ktor engine + SQLDelight driver wiring lives in each module's platform source set,
  never in commonMain.
- All dependency versions go in `gradle/libs.versions.toml`; reference via `libs.*`.
- Always run `:shared:assembleComposeAppDebugXCFramework` before building the iOS app.
