# NeoVita Infrastructure — Railway Deploy Design
**Date:** 2026-04-14
**Status:** Approved
**Scope:** Sub-project 1 of 4 — Deploy Ktor backend to Railway (staging + production)

---

## 1. Overview

Deploy the existing Ktor `:server` module to Railway using a multi-stage Dockerfile. Two environments: staging (auto-deploy on push to `main`) and production (manual trigger). Railway PostgreSQL plugin handles the database. Flyway manages schema migrations.

---

## 2. Railway Project Structure

```
Railway Project: neovita
├── staging environment
│   ├── Service: neovita-api   (Ktor, built from Dockerfile)
│   └── Service: neovita-db    (Railway PostgreSQL plugin)
└── production environment
    ├── Service: neovita-api   (same Dockerfile, different env vars)
    └── Service: neovita-db    (Railway PostgreSQL plugin)
```

**URLs (Railway-generated):**
- Staging: `neovita-api-staging.up.railway.app`
- Production: `neovita-api-production.up.railway.app`

---

## 3. Dockerfile

Multi-stage build targeting only the `:server` Gradle module. KMP modules (`shared`, `composeApp`) are excluded — they require Android SDK / Xcode and must not be part of the server build.

```dockerfile
# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle :server:installDist --no-daemon --no-configuration-cache

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/server/build/install/server .
EXPOSE 8080
CMD ["bin/server"]
```

**Key decisions:**
- `installDist` produces a self-contained distribution — no Gradle at runtime, ~200MB final image
- `eclipse-temurin:17-jre-alpine` — slim JRE, no full JDK in production
- Port `8080` matches existing `application.conf`

---

## 4. Health Check Endpoint

Add a `/ping` endpoint to the Ktor server returning `200 OK`. Railway uses this to confirm the service is healthy before routing traffic.

```kotlin
// In Routing.kt
get("/ping") {
    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
}
```

Railway health check config: path `/ping`, timeout 30s, interval 10s.

---

## 5. Environment Variables

Set per-environment in the Railway dashboard. Never committed to git.

| Variable | Source | Notes |
|---|---|---|
| `DATABASE_URL` | Railway auto-injected | From PostgreSQL plugin |
| `JWT_SECRET` | Manual | Min 32 chars |
| `CLAUDE_API_KEY` | Manual | From Anthropic console |
| `GOOGLE_CLIENT_ID` | Manual | From Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | Manual | From Google Cloud Console |

The existing `application.conf` reads these from environment variables — no changes needed.

---

## 6. Database Migrations (Flyway)

Replace `SchemaUtils.create()` calls in `DatabaseFactory.kt` with Flyway. Flyway runs migrations on startup before the app accepts traffic.

**Migration file location:**
```
server/src/main/resources/db/migration/
├── V1__initial_schema.sql     ← users, assessments, plans (current schema)
├── V2__add_companies.sql      ← added when B2B backend work starts
└── V3__add_content.sql        ← added when content system starts
```

**Flyway dependency added to** `server/build.gradle.kts`:
```kotlin
implementation("org.flywaydb:flyway-core:10.0.0")
implementation("org.flywaydb:flyway-database-postgresql:10.0.0")
```

**Behavior:**
- On deploy, Flyway checks `flyway_schema_history` and applies only new migrations
- Staging runs every migration on every deploy to `main` — catches issues early
- Production runs migrations on manual deploy — always verify staging first

---

## 7. Deployment Workflow

```
Push to main
    → Railway auto-builds Docker image
    → Flyway migrations run
    → Ktor starts, /ping returns 200
    → Railway routes traffic (staging)
    → Manual trigger in Railway dashboard
    → Same process for production
```

**Branch strategy:**
- `main` — always deployable, auto-deploys to staging
- Feature branches → PR → merge to `main`
- No `develop` branch — staging IS main

**Rollback:** Railway retains the previous deployment snapshot. One-click revert in the dashboard.

---

## 8. Out of Scope (deferred)

- GitHub Actions test runner — added when team grows
- Docker image registry — Railway builds from source directly
- Custom domain — 5-min config when needed
- HTTPS certificates — Railway handles automatically

---

## 9. Sub-project Roadmap

| # | Sub-project | Depends on |
|---|---|---|
| 1 | **Infrastructure (this spec)** | — |
| 2 | Backend API expansion | #1 live |
| 3 | Employer Portal — core (Next.js) | #2 |
| 4 | Employer Portal — content system | #3 |
