# NeoVita

Coaching Integral con IA para la Longevidad — Kotlin Multiplatform MVP (Android · iOS · Web)

## Quick Start

### Prerequisites
- JDK 17+, Android Studio Hedgehog+, Xcode 15+ (for iOS)
- Docker (for PostgreSQL)
- Claude API key from console.anthropic.com

### Run backend
```bash
cp .env.example .env  # fill in your values
docker run -d --name neovita-db \
  -e POSTGRES_DB=neovita \
  -e POSTGRES_USER=neovita \
  -e POSTGRES_PASSWORD=secret \
  -p 5432:5432 postgres:16
export DB_URL="jdbc:postgresql://localhost:5432/neovita?user=neovita&password=secret"
export JWT_SECRET="dev-secret-change-in-prod-min-32-chars"
export CLAUDE_API_KEY="your-key-here"
./gradlew :server:run
```

### Run Android
```bash
./gradlew :composeApp:installDebug
```

### Run iOS
```bash
open iosApp/iosApp.xcworkspace  # → Run in Xcode
```

### Run Web
```bash
./gradlew :composeApp:wasmJsBrowserRun
```

## Architecture

```
neovita/
├── server/          # Ktor backend (JVM) — REST API + Claude SSE proxy
├── shared/          # KMP shared logic — domain, network, SQLDelight cache
└── composeApp/      # Compose Multiplatform UI (Android/iOS/Web)
```

## Tech Stack

- Kotlin 2.0 · Compose Multiplatform 1.7 · Ktor 3.0
- Exposed ORM · PostgreSQL · SQLDelight 2.0
- Koin 4.0 · Voyager 1.1 · Kotlinx Serialization

## Test commands

```bash
./gradlew :server:test                     # Server unit + route tests
./gradlew :shared:testDebugUnitTest        # Shared KMP tests
```
