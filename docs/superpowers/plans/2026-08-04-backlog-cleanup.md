# Backlog Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three findings the screen-editor and health reviews left open — a silent lost-write on concurrent screen saves, an edit that can 200 yet never reach any client, and a per-member query fan-out on the employer dashboard.

**Architecture:** All three are localized. Screen saves become optimistically concurrent (`UPDATE ... WHERE version = expected`, 409 on conflict, insert-collision handled); saving also publishes, so an edit can't succeed invisibly. The B2B team endpoint stops asking per member and asks once per table.

**Tech Stack:** Kotlin, Ktor, Exposed, H2 test harness.

## Global Constraints

- Kotlin 2.0.21; no new dependencies; no `java.*` in commonMain.
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings Spanish. Branch: `claude/backlog-cleanup`. Commit here.
- New DTO fields MUST have defaults. CI builds iOS and wasm — a change breaking either fails the PR.
- Behavior visible to clients must not change except where a task says so.

---

### Task 1: Screen saves stop losing writes and stop succeeding invisibly (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/neovita/server/db/repositories/ScreenRepository.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/routes/ScreenRoutes.kt`
- Test: `server/src/test/kotlin/com/neovita/server/routes/ScreenRoutesTest.kt`

**Interfaces:**
- Produces: `ScreenRepository.save(slug, sections, expectedVersion: Int?): ScreenDefinitionDto?` — returns null when `expectedVersion` doesn't match the stored version (caller answers 409). `expectedVersion = null` keeps the old force-write behavior for callers that don't care. The route reads the optional `If-Match` header; absent header = force.

Two defects are being fixed together because they live in the same method:
- **Lost write:** two concurrent PUTs both read version N, both write N+1; one body is silently discarded and *both* callers get 200 with the same version, so the loser believes it saved.
- **Invisible success:** `getActive` filters `active = true`, but `save`'s update branch never touches `active`. Nothing sets it false today, so this is latent — the day something does, an employer edits, gets 200 with a bumped version, and no client ever sees it. Saving now publishes.

- [ ] **Step 1: Write the failing tests**

Append to `ScreenRoutesTest.kt` (it already has the harness and the `employer()` helper):

```kotlin
    @Test
    fun `a stale If-Match is rejected with 409 instead of clobbering`() = testApplication {
        environment { config = testConfig("screens_test_conflict") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        // Primer guardado: la pantalla pasa a versión 2.
        val first = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.IfMatch, "1")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.OK, first.status)

        // Segundo editor que venía de la versión 1: su escritura NO debe pisar la anterior.
        val stale = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.IfMatch, "1")
            contentType(ContentType.Application.Json)
            setBody("""{"sections":[{"type":"QUOTE_BANNER","text":"pisada"}]}""")
        }
        assertEquals(HttpStatusCode.Conflict, stale.status)

        // Lo guardado sigue siendo lo del primer editor.
        val after = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        assertTrue(after.contains("Página demo"), after)
        assertTrue(!after.contains("pisada"), after)
    }

    @Test
    fun `without If-Match the save still forces through`() = testApplication {
        environment { config = testConfig("screens_test_force") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val response = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `saving republishes a screen so an edit can't succeed invisibly`() = testApplication {
        environment { config = testConfig("screens_test_republish") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        // Una pantalla desactivada no la sirve getActive...
        transaction {
            ScreensTable.update({ ScreensTable.slug eq "dashboard" }) { it[active] = false }
        }
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/api/screens/dashboard") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.status
        )

        // ...pero guardarla la vuelve a publicar.
        client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(
            HttpStatusCode.OK,
            client.get("/api/screens/dashboard") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.status
        )
    }
```

Add the imports the file needs: `com.neovita.server.db.tables.ScreensTable`, `org.jetbrains.exposed.sql.transactions.transaction`, `org.jetbrains.exposed.sql.update`, `org.jetbrains.exposed.sql.SqlExpressionBuilder.eq`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.ScreenRoutesTest" --console=plain`
Expected: FAIL — the conflict test gets 200 instead of 409

- [ ] **Step 3: Make the write conditional**

`ScreenRepository.save` becomes:

```kotlin
    /**
     * Guarda [sections] en [slug] subiendo la versión: los clientes usan version como ETag,
     * así que el bump es lo que invalida su caché y hace visible el cambio.
     *
     * Con [expectedVersion] no nulo la escritura es condicional (bloqueo optimista): si otra
     * persona guardó mientras editábamos, devuelve null en vez de pisar su trabajo — sin esto
     * dos guardados simultáneos descartan uno en silencio y ambos reciben 200.
     *
     * Guardar además republica: `getActive` filtra `active`, así que sin esto una pantalla
     * desactivada aceptaría ediciones con 200 que ningún cliente llegaría a ver.
     */
    fun save(slug: String, sections: List<SectionDto>, expectedVersion: Int? = null): ScreenDefinitionDto? = transaction {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString<List<SectionDto>>(sections)
        val current = ScreensTable.selectAll()
            .where { ScreensTable.slug eq slug }
            .singleOrNull()

        if (current == null) {
            if (expectedVersion != null) return@transaction null   // esperaba una fila que no existe
            ScreensTable.insert {
                it[ScreensTable.slug] = slug
                it[version] = 1
                it[sectionsJson] = encoded
                it[active] = true
                it[updatedAt] = now
            }
            return@transaction ScreenDefinitionDto(slug = slug, version = 1, sections = sections)
        }

        val storedVersion = current[ScreensTable.version]
        if (expectedVersion != null && expectedVersion != storedVersion) return@transaction null
        val nextVersion = storedVersion + 1
        // La condición sobre version va en el UPDATE, no sólo en el chequeo de arriba: así
        // dos transacciones concurrentes no pueden escribir ambas la misma versión.
        val updated = ScreensTable.update({
            (ScreensTable.slug eq slug) and (ScreensTable.version eq storedVersion)
        }) {
            it[version] = nextVersion
            it[sectionsJson] = encoded
            it[active] = true
            it[updatedAt] = now
        }
        if (updated == 0) return@transaction null
        ScreenDefinitionDto(slug = slug, version = nextVersion, sections = sections)
    }
```

Add the `org.jetbrains.exposed.sql.and` import if absent.

- [ ] **Step 4: The route answers 409**

In `ScreenRoutes.kt`'s PUT, after validation and before responding, replace `call.respond(repo.save(slug, body.sections))` with:

```kotlin
            // If-Match lleva la versión que el editor tenía cargada; si no viene, se fuerza.
            val expected = call.request.headers[HttpHeaders.IfMatch]?.toIntOrNull()
            val saved = repo.save(slug, body.sections, expected)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("code" to "SCREEN_CONFLICT",
                          "message" to "Otra persona guardó esta pantalla mientras editabas. Recarga y vuelve a aplicar tus cambios.")
                )
            call.respond(saved)
```

- [ ] **Step 5: The editor sends its loaded version**

In `server/src/main/resources/web/screen-editor.html`, in `save()`'s fetch, add the header alongside the existing ones — the editor already tracks the loaded version for its "Versión guardada" label; send that value:

```javascript
          "If-Match": String(savedVersion),
```

and in the response handling, treat 409 like the 400 case but with its own message: show the server's `message` and tell the user to press "Recargar". Do not claim success.

- [ ] **Step 6: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all pass (the pre-existing PUT tests send no `If-Match`, so they take the force path unchanged)

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/com/neovita/server/db/repositories/ScreenRepository.kt server/src/main/kotlin/com/neovita/server/routes/ScreenRoutes.kt server/src/main/resources/web/screen-editor.html server/src/test/kotlin/com/neovita/server/routes/ScreenRoutesTest.kt
git commit -m "fix(server): optimistic locking on screen saves, and saving republishes"
```

---

### Task 2: The employer dashboard stops querying per team member (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/neovita/server/db/repositories/AssessmentRepository.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/db/repositories/HealthRepository.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/routes/B2BRoutes.kt`
- Test: `server/src/test/kotlin/com/neovita/server/routes/B2BRoutesTest.kt` (create if absent)

**Interfaces:**
- Produces: `AssessmentRepository.latestScoresFor(userIds: List<String>): Map<String, PillarScoresDto>` and `HealthRepository.summariesFor(userIds: List<String>): Map<String, HealthSummary>`.

`GET /api/b2b/team` currently calls `findLatest(member.id)` per member, and since the health work `findLatest` also runs a health-summary query per member — so a team of N costs ~2N queries. Batch both.

- [ ] **Step 1: Write the failing test**

Create `B2BRoutesTest.kt` using the same H2 harness as `ScreenRoutesTest` (copy `testSecret`, `jwtService`, `testConfig`; `startApplication()` before direct DB writes):

```kotlin
    @Test
    fun `team scores come back for every member`() = testApplication {
        environment { config = testConfig("b2b_test_team") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }

        val users = UserRepository()
        val boss = users.upsert("boss@test.dev", "Boss")
        val a = users.upsert("a@test.dev", "Ana")
        val b = users.upsert("b@test.dev", "Beto")
        transaction {
            UsersTable.update({ UsersTable.id eq boss.id }) { it[role] = "EMPLOYER"; it[companyId] = "acme" }
            listOf(a.id, b.id).forEach { id ->
                UsersTable.update({ UsersTable.id eq id }) { it[companyId] = "acme" }
            }
        }
        val assessments = AssessmentRepository(HealthRepository())
        assessments.save(a.id, "Todos los días", "Cardio", "7-8 horas", 8, "Energía")
        assessments.save(b.id, "Nunca", "No hago ejercicio", "5-6 horas", 3, "Energía")

        val response = client.get("/api/b2b/team") {
            header(HttpHeaders.Authorization, "Bearer ${jwtService.generateToken(boss.id, "EMPLOYER")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Ana"), body)
        assertTrue(body.contains("Beto"), body)
        // Ana entrena a diario y Beto nunca: el promedio del equipo no puede ser 0.
        val avg = Json.parseToJsonElement(body).jsonObject["avgScore"]!!.jsonPrimitive.int
        assertTrue(avg > 0, "avgScore fue $avg — body: $body")
    }
```

(Adapt the `UsersTable` column names to the real ones — read `server/src/main/kotlin/com/neovita/server/db/tables/UsersTable.kt` first. If `AssessmentRepository`'s constructor or `save` signature differs, adapt and note it.)

- [ ] **Step 2: Run it — it should PASS against the current code**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.B2BRoutesTest" --console=plain`
Expected: PASS. This test is the safety net proving the batching refactor doesn't change the response; it is written first precisely so the refactor is verifiable.

- [ ] **Step 3: Batch the queries**

In `AssessmentRepository`, add:

```kotlin
    /** Puntuaciones de la evaluación más reciente de cada usuario, en una sola consulta
     *  (el dashboard de empresa las pedía una por miembro). */
    fun latestScoresFor(userIds: List<String>): Map<String, PillarScoresDto> {
        if (userIds.isEmpty()) return emptyMap()
        val health = healthRepository?.summariesFor(userIds).orEmpty()
        return transaction {
            AssessmentsTable.selectAll()
                .where { AssessmentsTable.userId inList userIds }
                .orderBy(AssessmentsTable.createdAt, SortOrder.DESC)
                .groupBy { it[AssessmentsTable.userId] }
                .mapValues { (userId, rows) ->
                    val row = rows.first()
                    calculateScores(
                        row[AssessmentsTable.exerciseFrequency],
                        row[AssessmentsTable.exerciseType],
                        row[AssessmentsTable.sleepHours],
                        row[AssessmentsTable.sleepQuality],
                        health = health[userId]
                    ).toDto()
                }
        }
    }
```

In `HealthRepository`, add:

```kotlin
    /** Resúmenes de varios usuarios en una consulta; misma ventana de recencia que summary(). */
    fun summariesFor(userIds: List<String>, days: Int = 7): Map<String, HealthSummary> {
        if (userIds.isEmpty()) return emptyMap()
        return transaction {
            HealthMetricsTable.selectAll()
                .where { (HealthMetricsTable.userId inList userIds) and (HealthMetricsTable.date greaterEq recencyCutoff()) }
                .orderBy(HealthMetricsTable.date, SortOrder.DESC)
                .groupBy { it[HealthMetricsTable.userId] }
                .mapValues { (_, rows) ->
                    val recent = rows.take(days)
                    fun avg(values: List<Int>): Int? = if (values.isEmpty()) null else values.sum() / values.size
                    HealthSummary(
                        avgDailySteps = avg(recent.mapNotNull { it[HealthMetricsTable.steps] }),
                        avgSleepMinutes = avg(recent.mapNotNull { it[HealthMetricsTable.sleepMinutes] }),
                        avgHeartRate = avg(recent.mapNotNull { it[HealthMetricsTable.avgHeartRate] })
                    )
                }
        }
    }
```

(Adapt to the real column/property names — read both repositories first. `recencyCutoff()` already exists in `HealthRepository`; if it is private, keep it private and call it from inside the class.)

In `B2BRoutes.kt`, replace the per-member call:

```kotlin
            val members = userRepository.findByCompany(user.companyId!!)
            val scoresByUser = assessmentRepo.latestScoresFor(members.map { it.id })
            val team = members.map { member ->
                TeamMemberDto(
                    userId = member.id, name = member.name, email = member.email,
                    scores = scoresByUser[member.id]
                )
            }
```

- [ ] **Step 4: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL — in particular the B2B test from Step 1 still passes, proving the response didn't change.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/com/neovita/server/db/repositories/AssessmentRepository.kt server/src/main/kotlin/com/neovita/server/db/repositories/HealthRepository.kt server/src/main/kotlin/com/neovita/server/routes/B2BRoutes.kt server/src/test/kotlin/com/neovita/server/routes/B2BRoutesTest.kt
git commit -m "perf(server): batch the employer dashboard's per-member queries"
```

---

### Task 3: Verification (controller-run)

- [ ] **Step 1:** Full suites green (`:server:test :core:testDebugUnitTest`) and all three targets compile.
- [ ] **Step 2:** Live check against a local server: two PUTs with the same stale `If-Match` — the first 200, the second 409 with the Spanish reason, and a GET confirms the first editor's content survived.
- [ ] **Step 3:** Live check that a PUT with no `If-Match` still succeeds (the editor's older behavior and any script must keep working).
