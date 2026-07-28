# Health Connect → Longevity Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android reads steps / sleep / resting heart rate from Health Connect (user-initiated, behind the dormant `healthSync` flag), uploads daily aggregates to the server, and the Longevity Score's exercise and sleep pillars switch from self-reported answers to measured data when it exists.

**Architecture:** `CalculateScoresUseCase` (core, already shared by the server) gains an optional `HealthSummary` parameter — absent it behaves exactly as today, so nothing regresses. The server stores one `health_metrics` row per user per day (upsert), derives a 7-day summary, and feeds it into scoring on assessment read/save. Android uses `androidx.health.connect:connect-client:1.1.0`; the permission flow is launched from a `HealthPermissionLauncher` holder registered by MainActivity (same pattern as `CurrentActivityHolder`), triggered from a Profile entry that only appears when the server enables `healthSync`.

**Tech Stack:** Kotlin Multiplatform, androidx.health.connect 1.1.0, Exposed/H2 test harness, Compose Multiplatform, Koin, kotlinx.serialization + kotlinx.datetime.

## Global Constraints

- Kotlin 2.0.21; versions via `gradle/libs.versions.toml`; no `java.*` / `System.currentTimeMillis()` in commonMain (use `kotlinx.datetime.Clock`).
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings Spanish. Branch: `claude/health-connect`. Commit here.
- New DTO fields MUST have defaults (installed-app forward compat).
- **Backwards compatibility is a hard gate**: with no health data, `CalculateScoresUseCase` must return byte-identical results to today. The existing `CalculateScoresUseCaseTest` must pass unchanged.
- Dormant-safety: with `healthSync` off (default) or Health Connect unavailable, the app behaves exactly as today — no Profile entry, no permission dialog, no crash.
- Health data is only ever read after an explicit user action ("Conectar datos de salud"), never on startup.
- Health Connect API risk: if a signature in this plan doesn't compile against 1.1.0, adapt minimally to the real API and **note the adaptation in your report** — do not redesign.

## External setup (user-owned, before Play Store release — NOT needed for this plan)

Google requires a Play Console **health-permissions declaration + privacy policy URL** for apps requesting Health Connect read permissions. Debug builds and emulator testing work without it; store submission does not. Out of scope here, tracked as a release blocker.

---

### Task 1: Core — HealthSummary + health-aware scoring (TDD)

**Files:**
- Create: `core/src/commonMain/kotlin/com/neovita/shared/domain/model/HealthSummary.kt`
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/domain/usecase/CalculateScoresUseCase.kt`
- Test: `core/src/commonTest/kotlin/com/neovita/shared/domain/usecase/CalculateScoresUseCaseTest.kt`

**Interfaces:**
- Produces: `data class HealthSummary(val avgDailySteps: Int? = null, val avgSleepMinutes: Int? = null, val restingHeartRate: Int? = null)` in `com.neovita.shared.domain.model`; `CalculateScoresUseCase.invoke(exerciseFrequency, exerciseType, sleepHours, sleepQuality, health: HealthSummary? = null): PillarScores`. Task 2's server passes the summary.

- [ ] **Step 1: Write the failing tests**

Append to `CalculateScoresUseCaseTest.kt` (imports `com.neovita.shared.domain.model.HealthSummary`):

```kotlin
    @Test fun `null health summary preserves questionnaire behaviour`() {
        val withoutArg = useCase("2-3 veces", "Cardio", "6-8 horas", 6)
        val withNull = useCase("2-3 veces", "Cardio", "6-8 horas", 6, health = null)
        assertEquals(withoutArg, withNull)
    }

    @Test fun `measured steps override a modest questionnaire exercise score`() {
        val declared = useCase("1 vez", "Cardio", "6-8 horas", 6)
        val measured = useCase("1 vez", "Cardio", "6-8 horas", 6,
            HealthSummary(avgDailySteps = 12000))
        assertTrue(measured.exercise > declared.exercise,
            "measured ${measured.exercise} should beat declared ${declared.exercise}")
        assertEquals(100, measured.exercise)
    }

    @Test fun `few measured steps lower an optimistic questionnaire score`() {
        val declared = useCase("Todos los días", "Pesas o resistencia", "6-8 horas", 6)
        val measured = useCase("Todos los días", "Pesas o resistencia", "6-8 horas", 6,
            HealthSummary(avgDailySteps = 1500))
        assertTrue(measured.exercise < declared.exercise)
    }

    @Test fun `measured sleep replaces declared hours but keeps quality`() {
        val eightHours = useCase("2-3 veces", "Cardio", "Menos de 5 horas", 8,
            HealthSummary(avgSleepMinutes = 480))
        val fourHours = useCase("2-3 veces", "Cardio", "7-8 horas", 8,
            HealthSummary(avgSleepMinutes = 240))
        assertTrue(eightHours.sleep > fourHours.sleep)
    }

    @Test fun `partial health summary only overrides the pillar it measures`() {
        val declared = useCase("Todos los días", "Cardio", "7-8 horas", 8)
        val stepsOnly = useCase("Todos los días", "Cardio", "7-8 horas", 8,
            HealthSummary(avgDailySteps = 9000))
        assertEquals(declared.sleep, stepsOnly.sleep)   // sueño sin medir → cuestionario
    }

    @Test fun `overall stays the average of the three pillars with health data`() {
        val s = useCase("2-3 veces", "Cardio", "6-8 horas", 6,
            HealthSummary(avgDailySteps = 8000, avgSleepMinutes = 430))
        assertEquals((s.exercise + s.sleep + s.nutrition) / 3, s.overall)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.domain.usecase.CalculateScoresUseCaseTest" --console=plain`
Expected: FAIL to compile — unresolved `HealthSummary`

- [ ] **Step 3: Create HealthSummary**

`core/src/commonMain/kotlin/com/neovita/shared/domain/model/HealthSummary.kt`:

```kotlin
package com.neovita.shared.domain.model

/**
 * Medias de los últimos días medidas por el dispositivo (Health Connect / HealthKit).
 * Todo es opcional: cada pilar cae al cuestionario cuando no hay dato medido.
 */
data class HealthSummary(
    val avgDailySteps: Int? = null,
    val avgSleepMinutes: Int? = null,
    val restingHeartRate: Int? = null
)
```

- [ ] **Step 4: Make scoring health-aware**

`CalculateScoresUseCase.kt` becomes:

```kotlin
package com.neovita.shared.domain.usecase

import com.neovita.shared.domain.model.HealthSummary
import com.neovita.shared.domain.model.PillarScores

class CalculateScoresUseCase {
    // [health] son datos medidos (Health Connect/HealthKit). Cuando existen, sustituyen a la
    // respuesta declarada del pilar correspondiente; con health = null el resultado es
    // idéntico al del cuestionario de siempre.
    operator fun invoke(
        exerciseFrequency: String, exerciseType: String,
        sleepHours: String, sleepQuality: Int,
        health: HealthSummary? = null
    ): PillarScores {
        val exerciseFreqScore = when (exerciseFrequency) {
            "Todos los días" -> 100; "4-5 veces" -> 85; "2-3 veces" -> 65
            "1 vez" -> 40; else -> 10
        }
        val exerciseTypeBonus = when (exerciseType) {
            "Pesas o resistencia" -> 5; "Yoga o pilates" -> 3; else -> 0
        }
        val declaredExercise = (exerciseFreqScore + exerciseTypeBonus).coerceAtMost(100)
        // 10.000 pasos/día = 100; escala lineal, con el bonus por tipo de ejercicio intacto.
        val exercise = health?.avgDailySteps?.let { steps ->
            ((steps * 100) / 10_000).coerceIn(0, 100 - exerciseTypeBonus) + exerciseTypeBonus
        } ?: declaredExercise

        val declaredSleepHoursScore = when (sleepHours) {
            "7-8 horas", "8+" -> 90; "6-7 horas", "6-8 horas" -> 70
            "5-6 horas" -> 45; else -> 15
        }
        // Mismo baremo que el cuestionario, pero con las horas realmente dormidas.
        val sleepHoursScore = health?.avgSleepMinutes?.let { minutes ->
            when {
                minutes >= 420 -> 90      // 7 h o más
                minutes >= 360 -> 70      // 6-7 h
                minutes >= 300 -> 45      // 5-6 h
                else -> 15
            }
        } ?: declaredSleepHoursScore
        val sleep = ((sleepHoursScore + (sleepQuality * 10)) / 2)

        val nutrition = 60  // Baseline — not assessed in MVP
        return PillarScores(
            overall = (exercise + sleep + nutrition) / 3,
            exercise = exercise, sleep = sleep, nutrition = nutrition
        )
    }
}
```

- [ ] **Step 5: Run the whole core suite (backwards-compat gate)**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL — the new tests pass AND every pre-existing `CalculateScoresUseCaseTest` case still passes untouched.

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/domain/model/HealthSummary.kt core/src/commonMain/kotlin/com/neovita/shared/domain/usecase/CalculateScoresUseCase.kt core/src/commonTest/kotlin/com/neovita/shared/domain/usecase/CalculateScoresUseCaseTest.kt
git commit -m "feat(core): health-aware Longevity Score (measured steps/sleep override the questionnaire)"
```

---

### Task 2: Server — health_metrics storage, endpoints, and scoring wired in (TDD)

**Files:**
- Create: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/HealthDto.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/tables/HealthMetricsTable.kt`
- Create: `server/src/main/kotlin/com/neovita/server/db/repositories/HealthRepository.kt`
- Create: `server/src/main/kotlin/com/neovita/server/routes/HealthRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/db/DatabaseFactory.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/db/repositories/AssessmentRepository.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/Application.kt`, `plugins/Routing.kt`, `routes/AssessmentRoutes.kt`
- Test: `server/src/test/kotlin/com/neovita/server/routes/HealthRoutesTest.kt`

**Interfaces:**
- Consumes: `HealthSummary` (Task 1).
- Produces: `DailyHealthMetricDto(date: String, steps: Int? = null, sleepMinutes: Int? = null, restingHeartRate: Int? = null)`, `HealthUploadRequest(metrics: List<DailyHealthMetricDto>)`, `HealthSummaryDto(avgDailySteps: Int? = null, avgSleepMinutes: Int? = null, restingHeartRate: Int? = null, daysWithData: Int = 0)`; `HealthRepository.upsertAll(userId, metrics)` / `.summary(userId, days: Int = 7): HealthSummary`; `POST /api/health/metrics` (auth, 204), `GET /api/health/summary` (auth). Task 3 consumes the DTOs/endpoint.

- [ ] **Step 1: Write the failing test**

`HealthRoutesTest.kt` — copy the H2 harness from `server/src/test/kotlin/com/neovita/server/routes/DeviceRoutesTest.kt` (same `testSecret`, `jwtService`, `testConfig(dbName)`; db names `health_test_*`; call `startApplication()` before any direct DB write, as `PushRoutesTest` does):

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.repositories.HealthRepository
import com.neovita.server.module
import com.neovita.server.services.JwtService
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

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

    private val twoDays = """
        {"metrics":[
          {"date":"2026-07-25","steps":8000,"sleepMinutes":420,"restingHeartRate":60},
          {"date":"2026-07-26","steps":12000,"sleepMinutes":480,"restingHeartRate":58}
        ]}
    """.trimIndent()

    @Test
    fun `uploads metrics and returns an averaged summary`() = testApplication {
        environment { config = testConfig("health_test_summary") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-1", "USER")

        val upload = client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(twoDays)
        }
        assertEquals(HttpStatusCode.NoContent, upload.status)

        val summary = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, summary.status)
        val body = summary.bodyAsText()
        assertTrue(body.contains("\"avgDailySteps\":10000"), body)
        assertTrue(body.contains("\"avgSleepMinutes\":450"), body)
        assertTrue(body.contains("\"daysWithData\":2"), body)
    }

    @Test
    fun `re-uploading the same day updates instead of duplicating`() = testApplication {
        environment { config = testConfig("health_test_upsert") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-2", "USER")
        val oneDay = """{"metrics":[{"date":"2026-07-25","steps":%d}]}"""

        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(oneDay.format(3000))
        }
        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(oneDay.format(9000))
        }
        val body = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        assertTrue(body.contains("\"avgDailySteps\":9000"), body)
        assertTrue(body.contains("\"daysWithData\":1"), body)
    }

    @Test
    fun `metrics of one user never leak into another summary`() = testApplication {
        environment { config = testConfig("health_test_isolation") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-a", "USER")}")
            contentType(ContentType.Application.Json); setBody(twoDays)
        }
        val other = client.get("/api/health/summary") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken("user-b", "USER")}")
        }.bodyAsText()
        assertTrue(other.contains("\"daysWithData\":0"), other)
    }

    @Test
    fun `health endpoints require auth`() = testApplication {
        environment { config = testConfig("health_test_401") }
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/health/summary").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/health/metrics").status)
    }

    @Test
    fun `assessment scores use the uploaded health data`() = testApplication {
        environment { config = testConfig("health_test_scoring") }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken("user-3", "USER")
        // Cuestionario pesimista: "1 vez" por semana.
        val assessment = """{"exerciseFrequency":"1 vez","exerciseType":"Cardio",
            "sleepHours":"6-8 horas","sleepQuality":6,"mainGoal":"Energía"}""".trimIndent()

        val before = client.post("/api/assessments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(assessment)
        }.bodyAsText()

        client.post("/api/health/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"metrics":[{"date":"2026-07-26","steps":12000}]}""")
        }

        val after = client.get("/api/assessments/latest") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()

        assertTrue(before.contains("\"exercise\":40"), "declarado: $before")
        assertTrue(after.contains("\"exercise\":100"), "medido: $after")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.HealthRoutesTest" --console=plain`
Expected: FAIL to compile — unresolved `HealthRepository`

- [ ] **Step 3: DTOs**

`core/src/commonMain/kotlin/com/neovita/shared/network/dto/HealthDto.kt`:

```kotlin
package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

// Agregados diarios crudos medidos por el dispositivo. El servidor decide qué hacer con
// ellos (hoy: alimentar el Longevity Score) — el cliente no interpreta nada.
@Serializable
data class DailyHealthMetricDto(
    val date: String,                       // ISO-8601 "YYYY-MM-DD" (día local del dispositivo)
    val steps: Int? = null,
    val sleepMinutes: Int? = null,
    val restingHeartRate: Int? = null
)

@Serializable
data class HealthUploadRequest(val metrics: List<DailyHealthMetricDto> = emptyList())

@Serializable
data class HealthSummaryDto(
    val avgDailySteps: Int? = null,
    val avgSleepMinutes: Int? = null,
    val restingHeartRate: Int? = null,
    val daysWithData: Int = 0
)
```

- [ ] **Step 4: Table + repository**

`HealthMetricsTable.kt`:

```kotlin
package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object HealthMetricsTable : Table("health_metrics") {
    val userId = varchar("user_id", 64)
    val date = varchar("metric_date", 10)          // "YYYY-MM-DD"
    val steps = integer("steps").nullable()
    val sleepMinutes = integer("sleep_minutes").nullable()
    val restingHeartRate = integer("resting_heart_rate").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, date)
}
```

`HealthRepository.kt`:

```kotlin
package com.neovita.server.db.repositories

import com.neovita.server.db.tables.HealthMetricsTable
import com.neovita.shared.domain.model.HealthSummary
import com.neovita.shared.network.dto.DailyHealthMetricDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class HealthRepository {

    /** Un registro por usuario y día; re-subir el mismo día actualiza (el dispositivo re-envía días parciales). */
    fun upsertAll(userId: String, metrics: List<DailyHealthMetricDto>) = transaction {
        val now = System.currentTimeMillis()
        metrics.forEach { m ->
            val updated = HealthMetricsTable.update({
                (HealthMetricsTable.userId eq userId) and (HealthMetricsTable.date eq m.date)
            }) {
                it[steps] = m.steps
                it[sleepMinutes] = m.sleepMinutes
                it[restingHeartRate] = m.restingHeartRate
                it[updatedAt] = now
            }
            if (updated == 0) {
                HealthMetricsTable.insert {
                    it[HealthMetricsTable.userId] = userId
                    it[date] = m.date
                    it[steps] = m.steps
                    it[sleepMinutes] = m.sleepMinutes
                    it[restingHeartRate] = m.restingHeartRate
                    it[updatedAt] = now
                }
            }
        }
    }

    /** Medias de los [days] días más recientes con datos. Campos sin ninguna medida quedan null. */
    fun summary(userId: String, days: Int = 7): HealthSummary = transaction {
        val rows = HealthMetricsTable.selectAll()
            .where { HealthMetricsTable.userId eq userId }
            .orderBy(HealthMetricsTable.date, SortOrder.DESC)
            .limit(days)
            .toList()
        fun avg(values: List<Int>): Int? = if (values.isEmpty()) null else values.sum() / values.size
        HealthSummary(
            avgDailySteps = avg(rows.mapNotNull { it[HealthMetricsTable.steps] }),
            avgSleepMinutes = avg(rows.mapNotNull { it[HealthMetricsTable.sleepMinutes] }),
            restingHeartRate = avg(rows.mapNotNull { it[HealthMetricsTable.restingHeartRate] })
        )
    }

    fun daysWithData(userId: String, days: Int = 7): Int = transaction {
        HealthMetricsTable.selectAll()
            .where { HealthMetricsTable.userId eq userId }
            .orderBy(HealthMetricsTable.date, SortOrder.DESC)
            .limit(days)
            .count().toInt()
    }
}
```

- [ ] **Step 5: Routes**

`HealthRoutes.kt`:

```kotlin
package com.neovita.server.routes

import com.neovita.server.db.repositories.HealthRepository
import com.neovita.shared.network.dto.HealthSummaryDto
import com.neovita.shared.network.dto.HealthUploadRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes(repo: HealthRepository) {
    authenticate("jwt-auth") {
        post("/health/metrics") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<HealthUploadRequest>()
            repo.upsertAll(userId, req.metrics)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/health/summary") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val s = repo.summary(userId)
            call.respond(
                HealthSummaryDto(
                    avgDailySteps = s.avgDailySteps,
                    avgSleepMinutes = s.avgSleepMinutes,
                    restingHeartRate = s.restingHeartRate,
                    daysWithData = repo.daysWithData(userId)
                )
            )
        }
    }
}
```

**Route-order warning:** these live inside the `route("/api") { ... }` block, so the paths are `/api/health/*` and do NOT collide with the top-level `/health` healthcheck in `Routing.kt`. Do not move them outside `/api`.

- [ ] **Step 6: Wire scoring into assessments**

In `AssessmentRepository.kt`: add a constructor parameter `private val healthRepository: HealthRepository? = null`, and pass the summary into both scoring call sites — in `save(...)`:

```kotlin
        val scores = calculateScores(
            frequency, type, sleepHours, sleepQuality,
            health = healthRepository?.summary(userId)
        ).toDto()
```

and in `toEntity()` (which recomputes on read) — it needs the userId, which the row already has:

```kotlin
    private fun ResultRow.toEntity(): AssessmentEntity {
        val freq = this[AssessmentsTable.exerciseFrequency]
        val type = this[AssessmentsTable.exerciseType]
        val sh = this[AssessmentsTable.sleepHours]
        val sq = this[AssessmentsTable.sleepQuality]
        val uid = this[AssessmentsTable.userId]
        val scores = calculateScores(freq, type, sh, sq, health = healthRepository?.summary(uid)).toDto()
```

(keep the rest of `toEntity` as it is, replacing only the score computation with the line above; if it currently computes `scores` inline in the returned object, hoist it exactly as shown.)

In `DatabaseFactory.kt` add `HealthMetricsTable` to imports and `createMissingTablesAndColumns(...)`.

In `Application.kt`: `val healthRepo = HealthRepository()`, change `val assessmentRepo = AssessmentRepository()` to `AssessmentRepository(healthRepo)`, and pass `healthRepo` to `configureRouting`. In `Routing.kt`: add parameter `healthRepo: HealthRepository = HealthRepository()` and register `healthRoutes(healthRepo)` inside `/api` after `pushRoutes(...)`.

- [ ] **Step 7: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all pass (including the pre-existing assessment/screen/push tests)

- [ ] **Step 8: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/HealthDto.kt server/src/main/kotlin/com/neovita/server/db/tables/HealthMetricsTable.kt server/src/main/kotlin/com/neovita/server/db/repositories/HealthRepository.kt server/src/main/kotlin/com/neovita/server/routes/HealthRoutes.kt server/src/main/kotlin/com/neovita/server/db/DatabaseFactory.kt server/src/main/kotlin/com/neovita/server/db/repositories/AssessmentRepository.kt server/src/main/kotlin/com/neovita/server/Application.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/test/kotlin/com/neovita/server/routes/HealthRoutesTest.kt
git commit -m "feat(server): health_metrics storage, /api/health endpoints, measured scoring"
```

---

### Task 3: Core/shared — upload API + HealthSync expect/actual skeleton (TDD)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt` (+ `core/src/commonTest/.../ApiServiceTest.kt`)
- Create: `shared/src/commonMain/kotlin/com/neovita/app/health/HealthSync.kt`
- Create: `shared/src/iosMain/kotlin/com/neovita/app/health/HealthSync.ios.kt`
- Create: `shared/src/wasmJsMain/kotlin/com/neovita/app/health/HealthSync.wasmJs.kt`
- Create: `shared/src/androidMain/kotlin/com/neovita/app/health/HealthSync.android.kt` (placeholder; Task 4 fills it)

**Interfaces:**
- Produces: `ApiService.uploadHealthMetrics(metrics: List<DailyHealthMetricDto>): Result<Unit>`, `ApiService.getHealthSummary(): Result<HealthSummaryDto>`; `enum class HealthSyncState { UNAVAILABLE, NEEDS_PERMISSION, SYNCING, SYNCED, ERROR }`; `expect class HealthSyncClient() { fun isAvailable(): Boolean; suspend fun requestPermissions(): Boolean; suspend fun sync(apiService: ApiService): HealthSyncState }` in `com.neovita.app.health`. Task 4 implements the Android actual, Task 5 drives the UI.

- [ ] **Step 1: TDD the ApiService methods**

In `ApiServiceTest.kt` add mock routes (before `else`):

```kotlin
            "/health/metrics" -> respond("", HttpStatusCode.NoContent)
            "/health/summary" -> respond(
                content = """{"avgDailySteps":9000,"avgSleepMinutes":430,"daysWithData":5}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
```

and tests:

```kotlin
    @Test fun `uploadHealthMetrics posts and succeeds`() = runTest {
        val result = apiService.uploadHealthMetrics(
            listOf(DailyHealthMetricDto(date = "2026-07-26", steps = 9000))
        )
        assertTrue(result.isSuccess)
    }

    @Test fun `getHealthSummary parses the averages`() = runTest {
        val summary = apiService.getHealthSummary().getOrNull()
        assertEquals(9000, summary?.avgDailySteps)
        assertEquals(5, summary?.daysWithData)
    }
```

Run (expect compile failure), then implement in `ApiService.kt` after `registerDeviceToken`:

```kotlin
    suspend fun uploadHealthMetrics(metrics: List<DailyHealthMetricDto>): Result<Unit> = safeCall {
        httpClient.post("$baseUrl/health/metrics") {
            contentType(ContentType.Application.Json)
            setBody(HealthUploadRequest(metrics))
        }
        Unit
    }

    suspend fun getHealthSummary(): Result<HealthSummaryDto> = safeCall {
        httpClient.get("$baseUrl/health/summary").body()
    }
```

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain` → PASS.

- [ ] **Step 2: The expect contract**

`shared/src/commonMain/kotlin/com/neovita/app/health/HealthSync.kt`:

```kotlin
package com.neovita.app.health

import com.neovita.shared.network.ApiService

enum class HealthSyncState { UNAVAILABLE, NEEDS_PERMISSION, SYNCING, SYNCED, ERROR }

// Lectura de datos de salud del dispositivo. Siempre iniciada por la usuaria (nunca al
// arrancar): son datos sensibles. Plataformas sin soporte devuelven UNAVAILABLE.
expect class HealthSyncClient() {
    fun isAvailable(): Boolean
    suspend fun requestPermissions(): Boolean
    suspend fun sync(apiService: ApiService): HealthSyncState
}
```

- [ ] **Step 3: The three non-Android actuals**

`HealthSync.ios.kt` and `HealthSync.wasmJs.kt` (identical bodies; the iOS comment says HealthKit llega con el sub-proyecto 1b):

```kotlin
package com.neovita.app.health

import com.neovita.shared.network.ApiService

actual class HealthSyncClient actual constructor() {
    actual fun isAvailable(): Boolean = false
    actual suspend fun requestPermissions(): Boolean = false
    actual suspend fun sync(apiService: ApiService): HealthSyncState = HealthSyncState.UNAVAILABLE
}
```

`HealthSync.android.kt` — same placeholder body for now (Task 4 replaces it), so every target compiles.

- [ ] **Step 4: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/ApiService.kt core/src/commonTest/kotlin/com/neovita/shared/network/ApiServiceTest.kt shared/src/commonMain/kotlin/com/neovita/app/health/HealthSync.kt shared/src/iosMain/kotlin/com/neovita/app/health/HealthSync.ios.kt shared/src/wasmJsMain/kotlin/com/neovita/app/health/HealthSync.wasmJs.kt shared/src/androidMain/kotlin/com/neovita/app/health/HealthSync.android.kt
git commit -m "feat(core): health metrics upload API + HealthSyncClient contract"
```

---

### Task 4: Android — real Health Connect reader

**Files:**
- Modify: `gradle/libs.versions.toml`, `shared/build.gradle.kts` (androidMain dep)
- Modify: `shared/src/androidMain/kotlin/com/neovita/app/health/HealthSync.android.kt`
- Create: `shared/src/androidMain/kotlin/com/neovita/app/health/HealthPermissionLauncher.kt`
- Modify: `androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: the Task 3 contract, `CurrentActivityHolder`, `ApiService`.
- Produces: `object HealthPermissionLauncher { var request: ((Set<String>, (Boolean) -> Unit) -> Unit)? }` — MainActivity registers the ActivityResult contract into it.

- [ ] **Step 1: Dependency**

`libs.versions.toml`: `health-connect = "1.1.0"` under `[versions]`; `[libraries]`: `health-connect-client = { module = "androidx.health.connect:connect-client", version.ref = "health-connect" }`. In `shared/build.gradle.kts` `androidMain.dependencies`: `implementation(libs.health.connect.client)`.

- [ ] **Step 2: Permission launcher holder**

`HealthPermissionLauncher.kt`:

```kotlin
package com.neovita.app.health

// Health Connect exige lanzar su contrato de permisos desde una Activity registrada.
// MainActivity registra aquí su launcher (mismo patrón que CurrentActivityHolder).
object HealthPermissionLauncher {
    @Volatile
    var request: ((Set<String>, (Boolean) -> Unit) -> Unit)? = null
}
```

- [ ] **Step 3: The Android actual**

`HealthSync.android.kt`:

```kotlin
package com.neovita.app.health

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.DailyHealthMetricDto
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume

private val READ_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
)

actual class HealthSyncClient actual constructor() {

    private fun client(): HealthConnectClient? {
        val context = CurrentActivityHolder.activity?.applicationContext ?: return null
        return runCatching {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) null
            else HealthConnectClient.getOrCreate(context)
        }.getOrNull()
    }

    actual fun isAvailable(): Boolean = client() != null

    actual suspend fun requestPermissions(): Boolean {
        val client = client() ?: return false
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        if (granted.containsAll(READ_PERMISSIONS)) return true
        val launcher = HealthPermissionLauncher.request ?: return false
        return suspendCancellableCoroutine { cont ->
            launcher(READ_PERMISSIONS) { ok -> cont.resume(ok) }
        }
    }

    // Lee los últimos 7 días agregados por día y los sube crudos: el servidor decide.
    actual suspend fun sync(apiService: ApiService): HealthSyncState {
        val client = client() ?: return HealthSyncState.UNAVAILABLE
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        if (!granted.containsAll(READ_PERMISSIONS)) return HealthSyncState.NEEDS_PERMISSION

        return runCatching {
            val end = LocalDateTime.now()
            val start = end.minus(7, ChronoUnit.DAYS)
            val groups: List<AggregationResultGroupedByPeriod> = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        SleepSessionRecord.SLEEP_DURATION_TOTAL,
                        HeartRateRecord.BPM_AVG
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
            val metrics = groups.mapNotNull { group ->
                val steps = group.result[StepsRecord.COUNT_TOTAL]?.toInt()
                val sleep = group.result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toInt()
                val bpm = group.result[HeartRateRecord.BPM_AVG]?.toInt()
                if (steps == null && sleep == null && bpm == null) null
                else DailyHealthMetricDto(
                    date = group.startTime.toLocalDate().toString(),
                    steps = steps, sleepMinutes = sleep, restingHeartRate = bpm
                )
            }
            if (metrics.isEmpty()) return HealthSyncState.SYNCED   // sin datos que subir, no es error
            apiService.uploadHealthMetrics(metrics)
                .fold(onSuccess = { HealthSyncState.SYNCED }, onFailure = { HealthSyncState.ERROR })
        }.getOrElse {
            Log.w("NeoVitaHealth", "Sync de salud falló", it)
            HealthSyncState.ERROR
        }
    }
}
```

- [ ] **Step 4: Register the launcher in MainActivity**

In `MainActivity.kt` add (imports `androidx.health.connect.client.PermissionController`, `com.neovita.app.health.HealthPermissionLauncher`):

```kotlin
    private val healthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            pendingHealthCallback?.invoke(granted.isNotEmpty())
            pendingHealthCallback = null
        }
    private var pendingHealthCallback: ((Boolean) -> Unit)? = null
```

and in `onCreate`, after `CurrentActivityHolder.activity = this`:

```kotlin
        HealthPermissionLauncher.request = { permissions, callback ->
            pendingHealthCallback = callback
            healthPermissions.launch(permissions)
        }
```

and in `onDestroy`, before `super.onDestroy()`:

```kotlin
        HealthPermissionLauncher.request = null
```

- [ ] **Step 5: Manifest**

In `AndroidManifest.xml` add the read permissions next to the existing ones:

```xml
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_SLEEP" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
```

and inside `<application>`, the rationale activity-alias Health Connect requires (points at MainActivity):

```xml
        <activity-alias
            android:name="ViewPermissionUsageActivity"
            android:exported="true"
            android:targetActivity="com.neovita.app.MainActivity"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
            <intent-filter>
                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
            </intent-filter>
        </activity-alias>
```

- [ ] **Step 6: Build**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL. If a Health Connect API signature differs from the code above, adapt minimally and report the adaptation.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts shared/src/androidMain/kotlin/com/neovita/app/health/HealthSync.android.kt shared/src/androidMain/kotlin/com/neovita/app/health/HealthPermissionLauncher.kt androidApp/src/main/kotlin/com/neovita/app/MainActivity.kt androidApp/src/main/AndroidManifest.xml
git commit -m "feat(android): Health Connect reader (steps, sleep, heart rate) behind user consent"
```

---

### Task 5: UI — flag-gated Profile entry that connects and syncs

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `HealthSyncClient` + `HealthSyncState` (Task 3/4), `RemoteConfigRepository` + `isFeatureEnabled` (existing), `ApiService` (Koin).

- [ ] **Step 1: Add the entry**

In `ProfileScreen.kt`, inside the same settings/options section that already holds the `AssessmentScreen` and `ContentAdminScreen` entries (search for `navigator.parent?.push(AssessmentScreen())`), add — matching the surrounding `ListItem` style used there (imports: `androidx.compose.runtime.*` for `remember`/`mutableStateOf`/`collectAsState`/`getValue`, `kotlinx.coroutines.launch`, `androidx.compose.runtime.rememberCoroutineScope`, `com.neovita.app.health.HealthSyncClient`, `com.neovita.app.health.HealthSyncState`, `com.neovita.shared.config.RemoteConfigRepository`, `com.neovita.shared.config.isFeatureEnabled`, `com.neovita.shared.network.ApiService`, `org.koin.compose.koinInject`):

```kotlin
                    // "healthSync" nace apagado: la entrada solo aparece cuando el servidor
                    // la enciende (ship dormant), y leer datos siempre lo inicia la usuaria.
                    val config by koinInject<RemoteConfigRepository>().config.collectAsState()
                    if (config.isFeatureEnabled("healthSync", default = false)) {
                        val apiService = koinInject<ApiService>()
                        val healthClient = remember { HealthSyncClient() }
                        val scope = rememberCoroutineScope()
                        var healthState by remember { mutableStateOf<HealthSyncState?>(null) }
                        val label = when (healthState) {
                            null -> "Conectar datos de salud"
                            HealthSyncState.SYNCING -> "Sincronizando…"
                            HealthSyncState.SYNCED -> "Datos de salud sincronizados"
                            HealthSyncState.NEEDS_PERMISSION -> "Permiso de salud pendiente"
                            HealthSyncState.UNAVAILABLE -> "Health Connect no disponible"
                            HealthSyncState.ERROR -> "No se pudo sincronizar — reintentar"
                        }
                        SettingsItem(
                            title = label,
                            icon = "❤️",
                            onClick = {
                                if (healthState == HealthSyncState.SYNCING) return@SettingsItem
                                scope.launch {
                                    healthState = HealthSyncState.SYNCING
                                    healthState = if (!healthClient.isAvailable()) {
                                        HealthSyncState.UNAVAILABLE
                                    } else if (!healthClient.requestPermissions()) {
                                        HealthSyncState.NEEDS_PERMISSION
                                    } else {
                                        healthClient.sync(apiService)
                                    }
                                }
                            }
                        )
                    }
```

**Adaptation note:** the existing entries use a local helper (around `ProfileScreen.kt:381`, a `ListItem`-based composable taking `title`/`icon`/`onClick`). Use that exact helper with its real name and parameter names — read the file first; do not invent `SettingsItem` if the helper is called something else, and match its parameter order.

- [ ] **Step 2: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt
git commit -m "feat(app): flag-gated 'Conectar datos de salud' entry in Profile"
```

---

### Task 6: E2E on the emulator (controller-run)

**Files:** none (verification only). AVD `Pixel_5_E2E` (API 35 ships Health Connect in-system).

- [ ] **Step 1: Server-side E2E without any device** — with the local server running and the dev JWT: `POST /api/health/metrics` with two days of data → 204; `GET /api/health/summary` → averages + `daysWithData`; `POST /api/assessments` with `"1 vez"` then re-read `/api/assessments/latest` → the `exercise` pillar jumps from the declared value to the measured one. This is the product payoff and needs no Health Connect at all.
- [ ] **Step 2: Dormant check** — server without `APP_FEATURES=healthSync=true`: Profile shows no health entry, no permission dialog, app behaves exactly as before.
- [ ] **Step 3: Flag-on check** — server with `APP_FEATURES="healthSync=true"`: the entry appears. Tapping it either opens the Health Connect permission sheet or reports "Health Connect no disponible" if the emulator image lacks the provider — both are acceptable outcomes to record; the app must not crash either way.
- [ ] **Step 4: Real-data sync** — only if the emulator has a working Health Connect provider: grant permissions, then confirm rows land in `psql -d neovita -c "SELECT * FROM health_metrics;"`. If the emulator has no health data, this stays documented as pending a physical device.
