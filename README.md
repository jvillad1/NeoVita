# NeoVita

Coaching Integral con IA para la Longevidad — Kotlin Multiplatform (Android · iOS · Web · Server).

NeoVita follows the **2026-05 KMP default structure** (JetBrains): a pure Kotlin library
(`:core`) is split from the per-platform application modules, so no multiplatform module
applies `com.android.application` (required by AGP 9.0). The web app is built to wasmJs and
served by the Ktor backend, deployed on **Railway** from the `Dockerfile`.

## Project structure

```
NeoVita/
├── core/         Pure KMP library — network (ApiService), domain, repositories, SQLDelight.
│                 The SQLDelight cache lives in nonWasmMain (android + ios only).
├── shared/       Compose Multiplatform UI library — Android (lib), iOS, Web (wasmJs).
│                 Produces the ComposeApp XCFramework for iOS.
├── androidApp/   Android application (com.android.application) — MainActivity host.
├── webApp/       wasmJs browser application — main() + index.html.
├── server/       Ktor JVM backend — REST API (/api/*) + serves the wasm web app from /.
└── iosApp/       Swift shell embedding the ComposeApp XCFramework (built from :shared).
```

### Module dependency graph

```
shared      ──▶ core   (api)
androidApp  ──▶ shared, core
webApp      ──▶ shared, core (transitively)
iosApp      ──▶ shared   (ComposeApp XCFramework)
server      ──▶ (standalone — its own Exposed/Postgres layer)
```

> **Naming note:** the Gradle module is `:core`, but its Kotlin package stayed
> `com.neovita.shared.*` (and the SQLDelight DB package is `com.neovita.shared.db`). Only the
> Gradle module/dir was renamed `shared`→`core`; packages were left untouched.

> **SQLDelight + wasmJs:** SQLDelight 2.0.2 publishes no wasmJs variant, so it is excluded from
> the web build and the generated DB code lives in `core/nonWasmMain`. Local caching is hidden
> behind the `LocalCache` interface (commonMain); `SqlDelightLocalCache` is the android/ios
> implementation, while the web runs with `cache = null` (no offline cache).

## Quick Start

### Prerequisites
- JDK 17+ (the build pins `jvmTarget = 17`), Android Studio, Xcode 16+ (for iOS)
- Docker (for local PostgreSQL)
- Claude API key from console.anthropic.com

### Run backend (local)
```bash
cp .env.example .env  # fill in your values
docker run -d --name neovita-db \
  -e POSTGRES_DB=neovita -e POSTGRES_USER=neovita -e POSTGRES_PASSWORD=secret \
  -p 5432:5432 postgres:16
export DB_URL="jdbc:postgresql://localhost:5432/neovita?user=neovita&password=secret"
export JWT_SECRET="dev-secret-change-in-prod-min-32-chars"
export ANTHROPIC_API_KEY="your-key-here"
./gradlew :server:run        # http://localhost:8080  (health: /health, API: /api/*)
```

### Run Web (dev)
```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

### Run Android
```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### Run iOS
```bash
./gradlew :shared:assembleComposeAppDebugXCFramework   # build the framework first
open iosApp/iosApp.xcodeproj                            # → Run in Xcode
```

## Deploy (Railway)

Railway builds the `Dockerfile` (multi-stage): it compiles the wasmJs production bundle,
copies it into `server/src/main/resources/static`, builds the server fat JAR, and runs it.
The server reads `$PORT` and serves the web app at `/` with the API under `/api`.

1. Create a Railway project and connect this GitHub repo (it auto-detects `railway.toml`).
2. Add a PostgreSQL plugin and set service variables: `DB_URL` (JDBC URL), `JWT_SECRET`,
   `ANTHROPIC_API_KEY`.
3. Push to the deployment branch — Railway builds & deploys. Health check: `/health`.

```bash
./gradlew :webApp:wasmJsBrowserDistribution   # what the Docker build runs
./gradlew :server:buildFatJar                 # → server/build/libs/server-all.jar
```

## Tech Stack

- Kotlin 2.0 · Compose Multiplatform 1.7 · Ktor 3.0
- Exposed ORM · PostgreSQL · SQLDelight 2.0 (android/ios only)
- Koin 4.0 · Voyager 1.1.0-beta03 (wasmJs-capable) · Kotlinx Serialization

## Test commands

```bash
./gradlew :server:test                 # Server unit tests
./gradlew :core:testDebugUnitTest      # Core KMP tests
```
