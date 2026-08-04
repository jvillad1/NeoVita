# SDUI Screen Editor (F2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An EMPLOYER can edit the app's server-driven screens from a web editor opened inside the app — no SQL, no deploy, no release — with the server validating every write against the same taxonomy the renderers use.

**Architecture:** Validation moves into one shared function in `:core` (`validateScreenSections`) so the server's `PUT` and the renderers agree by construction. The server gains `GET /api/screens` (list) and `PUT /api/screens/{slug}` (EMPLOYER, validated, bumps `version` so existing ETag caching invalidates itself). The editor itself is an HTML page at `/web/admin/screens` served by our own Ktor — reachable through the sub-project 3 WebView slot, so improving the editor is a deploy, never a release.

**Tech Stack:** Kotlin Multiplatform, Ktor, Exposed/H2 test harness, vanilla HTML/JS (no framework, no CDN — the page must work offline of any third party), Compose Multiplatform for the Profile entry.

## Global Constraints

- Kotlin 2.0.21; no `java.*` in commonMain; versions via `gradle/libs.versions.toml` (this plan adds NO dependencies).
- The worktree has no `local.properties`: prefix every Gradle command with `export ANDROID_HOME=/usr/local/share/android-commandlinetools`.
- User-facing strings Spanish. Branch: `claude/sdui-editor`. Commit here.
- New DTO fields MUST have defaults (installed-app forward compat).
- **The server is the only trust boundary.** The editor is a convenience; every constraint it enforces in the browser MUST also be enforced by `PUT`, and the server's answer is authoritative.
- **Auth constraint (load-bearing):** platform WebViews attach the session JWT only to the *initial* request, so a page's later `fetch()` calls are unauthenticated. The editor page is therefore EMPLOYER-gated server-side and bootstraps the caller's own token into the page for its API calls. This is a deliberate tradeoff — see Task 3 Step 1's comment requirements.
- CI now builds iOS and wasm too; a change that breaks either fails the PR.

---

### Task 1: Core — one shared validator for screen definitions (TDD)

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt`
- Test: `core/src/commonTest/kotlin/com/neovita/shared/network/dto/ScreenDtoTest.kt`

**Interfaces:**
- Produces: `fun validateScreenSections(sections: List<SectionDto>): String?` (null = valid, else a Spanish error message) and makes `isValidAction(a: ActionDto): Boolean` public. Task 2's `PUT` rejects with the returned message; Task 3's editor surfaces it.
- Keeps `renderableSections` exactly as is — renderers stay lenient (strip unknown), writes become strict (reject unknown). That asymmetry is intentional: old clients must survive new sections, but an admin must not be able to save a section no client can draw.

- [ ] **Step 1: Write the failing tests**

Append to `ScreenDtoTest.kt`:

```kotlin
    private fun card(title: String = "Card", action: ActionDto? = null) =
        CardDto(title = title, action = action)

    @Test fun `a well-formed screen validates`() {
        val sections = listOf(
            SectionDto(type = "HERO_SCORE"),
            SectionDto(type = "CARD_ROW", title = "Novedades", cards = listOf(
                card(action = ActionDto("OPEN_WEBVIEW", "/web/demo")),
                card(action = ActionDto("NAVIGATE", "plan")),
            )),
            SectionDto(type = "QUOTE_BANNER", text = "Hola"),
        )
        assertNull(validateScreenSections(sections))
    }

    @Test fun `an unknown section type is rejected`() {
        val error = validateScreenSections(listOf(SectionDto(type = "MYSTERY_MEAT")))
        assertNotNull(error)
        assertTrue(error.contains("MYSTERY_MEAT"), error)
    }

    @Test fun `an invalid action target is rejected`() {
        val error = validateScreenSections(listOf(
            SectionDto(type = "CARD_ROW", cards = listOf(card(action = ActionDto("OPEN_URL", "http://inseguro"))))
        ))
        assertNotNull(error)
    }

    @Test fun `a protocol-relative webview target is rejected`() {
        val error = validateScreenSections(listOf(
            SectionDto(type = "CARD_ROW", cards = listOf(card(action = ActionDto("OPEN_WEBVIEW", "//evil.example"))))
        ))
        assertNotNull(error)
    }

    @Test fun `a blank card title is rejected`() {
        val error = validateScreenSections(listOf(
            SectionDto(type = "CARD_ROW", cards = listOf(card(title = "   ")))
        ))
        assertNotNull(error)
    }

    @Test fun `an empty screen is rejected`() {
        assertNotNull(validateScreenSections(emptyList()))
    }

    @Test fun `too many sections are rejected`() {
        val many = List(21) { SectionDto(type = "HERO_SCORE") }
        assertNotNull(validateScreenSections(many))
    }

    @Test fun `too many cards in one section are rejected`() {
        val section = SectionDto(type = "CARD_ROW", cards = List(31) { card() })
        assertNotNull(validateScreenSections(listOf(section)))
    }

    @Test fun `an over-long title is rejected`() {
        val section = SectionDto(type = "CARD_ROW", title = "x".repeat(201), cards = listOf(card()))
        assertNotNull(validateScreenSections(listOf(section)))
    }
```

Add the imports the file needs (`kotlin.test.assertNull`, `assertNotNull`, `assertTrue`) if absent.

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --tests "com.neovita.shared.network.dto.ScreenDtoTest" --console=plain`
Expected: FAIL to compile — unresolved `validateScreenSections`

- [ ] **Step 3: Implement**

In `ScreenDto.kt`, change `private fun isValidAction` to `fun isValidAction` (keep its body untouched) and append:

```kotlin
// Límites de una definición de pantalla. Un renderer viejo tolera lo que no conoce
// (renderableSections lo strippea), pero GUARDAR algo que ningún cliente sabe dibujar
// es un error del editor, no del cliente: por eso escribir es estricto y leer es laxo.
private const val MAX_SECTIONS = 20
private const val MAX_CARDS_PER_SECTION = 30
private const val MAX_TEXT = 200

/** Valida una definición completa. Devuelve null si es válida, o el motivo en español. */
fun validateScreenSections(sections: List<SectionDto>): String? {
    if (sections.isEmpty()) return "La pantalla debe tener al menos una sección"
    if (sections.size > MAX_SECTIONS) return "Demasiadas secciones (máximo $MAX_SECTIONS)"

    sections.forEachIndexed { index, section ->
        val where = "sección ${index + 1}"
        if (section.type !in ScreenTaxonomy.SECTION_TYPES) {
            return "Tipo de sección desconocido en $where: ${section.type}"
        }
        section.title?.let {
            if (it.length > MAX_TEXT) return "El título de $where supera $MAX_TEXT caracteres"
        }
        section.text?.let {
            if (it.length > MAX_TEXT) return "El texto de $where supera $MAX_TEXT caracteres"
        }
        if (section.cards.size > MAX_CARDS_PER_SECTION) {
            return "Demasiadas tarjetas en $where (máximo $MAX_CARDS_PER_SECTION)"
        }
        section.cards.forEachIndexed { cardIndex, card ->
            val cardWhere = "tarjeta ${cardIndex + 1} de $where"
            if (card.title.isBlank()) return "La $cardWhere no tiene título"
            if (card.title.length > MAX_TEXT) return "El título de la $cardWhere supera $MAX_TEXT caracteres"
            card.action?.let { action ->
                if (!isValidAction(action)) {
                    return "Acción inválida en la $cardWhere: ${action.type} → ${action.target}"
                }
            }
        }
    }
    return null
}
```

- [ ] **Step 4: Run the core suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :core:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL — new tests pass and every pre-existing `ScreenDtoTest` case still passes.

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt core/src/commonTest/kotlin/com/neovita/shared/network/dto/ScreenDtoTest.kt
git commit -m "feat(core): shared validator for screen definitions (strict on write, lenient on read)"
```

---

### Task 2: Server — list and update endpoints (TDD)

**Files:**
- Modify: `server/src/main/kotlin/com/neovita/server/db/repositories/ScreenRepository.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/routes/ScreenRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/plugins/Routing.kt` (screenRoutes gains userRepo)
- Test: `server/src/test/kotlin/com/neovita/server/routes/ScreenRoutesTest.kt`

**Interfaces:**
- Consumes: `validateScreenSections` (Task 1), `requireRole` (existing, in `plugins/Authorization.kt`).
- Produces: `ScreenRepository.save(slug: String, sections: List<SectionDto>): ScreenDefinitionDto` (upsert, version = old + 1, or 1 when new) and `ScreenRepository.listAll(): List<ScreenDefinitionDto>`; `GET /api/screens` (EMPLOYER) and `PUT /api/screens/{slug}` (EMPLOYER). Task 3's editor consumes both.

- [ ] **Step 1: Write the failing tests**

Append to `ScreenRoutesTest.kt` — reuse its existing harness (`testSecret`, `jwtService`, `testConfig`), and mirror `PushRoutesTest`'s `employer()` helper (create the user via `UserRepository().upsert(...)`, promote with an `UsersTable` update, call `startApplication()` before writing):

```kotlin
    private val validBody = """
        {"sections":[
          {"type":"HERO_SCORE"},
          {"type":"CARD_ROW","title":"Novedades","cards":[
            {"title":"Página demo","action":{"type":"OPEN_WEBVIEW","target":"/web/demo"}}
          ]}
        ]}
    """.trimIndent()

    @Test
    fun `employer updates a screen and the version bumps`() = testApplication {
        environment { config = testConfig("screens_test_put") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val before = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        val beforeVersion = Json.parseToJsonElement(before).jsonObject["version"]!!.jsonPrimitive.int

        val put = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val newVersion = Json.parseToJsonElement(put.bodyAsText()).jsonObject["version"]!!.jsonPrimitive.int
        assertEquals(beforeVersion + 1, newVersion)

        // Lo guardado es lo que se lee después.
        val after = client.get("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        assertTrue(after.contains("Página demo"), after)
        assertTrue(after.contains("\"version\":$newVersion"), after)
    }

    @Test
    fun `an invalid definition is rejected with the reason`() = testApplication {
        environment { config = testConfig("screens_test_put_invalid") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val response = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"sections":[{"type":"MYSTERY_MEAT"}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("MYSTERY_MEAT"), response.bodyAsText())
    }

    @Test
    fun `a non-employer cannot update a screen`() = testApplication {
        environment { config = testConfig("screens_test_put_role") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val user = UserRepository().upsert("plain@test.dev", "Plain")
        val token = jwtService.generateToken(user.id, "USER")

        val response = client.put("/api/screens/dashboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(validBody)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `employer lists the screens`() = testApplication {
        environment { config = testConfig("screens_test_list") }
        application { module() }
        startApplication()
        val client = createClient { install(ContentNegotiation) { json() } }
        val token = jwtService.generateToken(employer(), "EMPLOYER")

        val response = client.get("/api/screens") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("dashboard"), response.bodyAsText())
    }
```

(If `requireRole` answers a status other than 403 for a non-admin, assert the real one and note it in your report.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --tests "com.neovita.server.routes.ScreenRoutesTest" --console=plain`
Expected: FAIL — 404/405 on PUT (route doesn't exist yet)

- [ ] **Step 3: Repository**

In `ScreenRepository.kt` add (imports `com.neovita.shared.network.dto.SectionDto`, `org.jetbrains.exposed.sql.update`, `org.jetbrains.exposed.sql.SortOrder` as needed):

```kotlin
    /** Guarda [sections] en [slug] subiendo la versión: los clientes usan version como ETag,
     *  así que el bump es lo que invalida su caché y hace visible el cambio. */
    fun save(slug: String, sections: List<SectionDto>): ScreenDefinitionDto = transaction {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString<List<SectionDto>>(sections)
        val current = ScreensTable.selectAll()
            .where { ScreensTable.slug eq slug }
            .singleOrNull()
        val nextVersion = (current?.get(ScreensTable.version) ?: 0) + 1
        if (current == null) {
            ScreensTable.insert {
                it[ScreensTable.slug] = slug
                it[version] = nextVersion
                it[sectionsJson] = encoded
                it[active] = true
                it[updatedAt] = now
            }
        } else {
            ScreensTable.update({ ScreensTable.slug eq slug }) {
                it[version] = nextVersion
                it[sectionsJson] = encoded
                it[updatedAt] = now
            }
        }
        ScreenDefinitionDto(slug = slug, version = nextVersion, sections = sections)
    }

    /** Todas las pantallas (para el editor). */
    fun listAll(): List<ScreenDefinitionDto> = transaction {
        ScreensTable.selectAll().map { it.toDto() }
    }
```

- [ ] **Step 4: Routes**

`ScreenRoutes.kt` becomes (keep the existing GET-with-ETag exactly as it is, and add):

```kotlin
fun Route.screenRoutes(repo: ScreenRepository, userRepository: UserRepository) {
    authenticate("jwt-auth") {
        // ... el GET /screens/{slug} existente, sin tocar ...

        // Administración de pantallas (EMPLOYER). El editor web vive en /web/admin/screens.
        get("/screens") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@get
            call.respond(repo.listAll())
        }
        put("/screens/{slug}") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@put
            val slug = call.parameters["slug"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receive<ScreenUpdateRequest>()
            // El servidor es la frontera de confianza: el editor valida por comodidad,
            // pero lo que decide es esto.
            validateScreenSections(body.sections)?.let { reason ->
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("code" to "INVALID_SCREEN", "message" to reason)
                )
            }
            call.respond(repo.save(slug, body.sections))
        }
    }
}
```

Add `@Serializable data class ScreenUpdateRequest(val sections: List<SectionDto> = emptyList())` to `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt`, plus the needed imports in the routes file (`com.neovita.server.db.repositories.UserRepository`, `com.neovita.server.plugins.requireRole`, `com.neovita.shared.network.dto.ScreenUpdateRequest`, `com.neovita.shared.network.dto.validateScreenSections`, `io.ktor.server.request.*`).

In `Routing.kt`, change the call to `screenRoutes(screenRepo, userRepo)`.

- [ ] **Step 5: Run the full server suite**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL, all pass (including the pre-existing screen/ETag tests)

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt server/src/main/kotlin/com/neovita/server/db/repositories/ScreenRepository.kt server/src/main/kotlin/com/neovita/server/routes/ScreenRoutes.kt server/src/main/kotlin/com/neovita/server/plugins/Routing.kt server/src/test/kotlin/com/neovita/server/routes/ScreenRoutesTest.kt
git commit -m "feat(server): EMPLOYER list + validated update endpoints for SDUI screens"
```

---

### Task 3: Server — the editor page at /web/admin/screens

**Files:**
- Modify: `server/src/main/kotlin/com/neovita/server/routes/WebRoutes.kt`
- Create: `server/src/main/resources/web/screen-editor.html`

**Interfaces:**
- Consumes: `GET /api/screens`, `PUT /api/screens/{slug}` (Task 2), `requireRole`.
- Produces: `GET /web/admin/screens` (authenticated + EMPLOYER) serving the editor.

- [ ] **Step 1: The route**

In `WebRoutes.kt`, change the signature to `fun Route.webRoutes(userRepository: UserRepository)` (keep `/web/demo` exactly as it is, public) and add:

```kotlin
    // El editor de pantallas: HTML servido por nosotros, abierto desde Perfil con el slot
    // WebView. Vive en el servidor a propósito — mejorarlo es un deploy, nunca un release.
    authenticate("jwt-auth") {
        get("/web/admin/screens") {
            if (!call.requireRole(userRepository, "EMPLOYER")) return@get
            // Los WebView sólo adjuntan el JWT a la petición INICIAL, así que los fetch()
            // de la página irían sin credencial. Le pasamos el token de quien ya se
            // autenticó aquí. Es su propio token, la página es same-origin, va por https y
            // está restringida a EMPLOYER; aun así no debe registrarse en logs ni salir de
            // este origen. (Mejora futura: un token efímero con alcance sólo-pantallas.)
            val token = call.request.headers[HttpHeaders.Authorization]
                ?.removePrefix("Bearer ")?.trim().orEmpty()
            val html = javaClass.getResource("/web/screen-editor.html")!!.readText()
                .replace("__BOOTSTRAP_TOKEN__", token)
            call.respondText(html, ContentType.Text.Html)
        }
    }
```

Add imports: `io.ktor.server.auth.*`, `com.neovita.server.db.repositories.UserRepository`, `com.neovita.server.plugins.requireRole`, `io.ktor.http.*`.

In `Routing.kt`, change `webRoutes()` to `webRoutes(userRepo)`.

**Placement warning:** `webRoutes` is registered outside `/api` and BEFORE `staticResources`. Keep it there — moving it after the static catch-all makes the page 404.

- [ ] **Step 2: The editor page**

Create `server/src/main/resources/web/screen-editor.html`. Requirements — a reviewer will check each:

- No external requests of any kind (no CDN, no fonts, no analytics): the page must work on a device with no internet beyond our own origin.
- Spanish UI. Visual style close to the app: fondo `#f5f5f7`, acento `#8B1D41` (NeoCrimson), tarjetas blancas con esquinas redondeadas.
- Reads `const TOKEN = "__BOOTSTRAP_TOKEN__";` and sends `Authorization: Bearer ${TOKEN}` on every fetch.
- On load: `GET /api/screens`, render a selector of slugs (default `dashboard`), then load that screen.
- Section list: each section shows its type and title, with **subir / bajar / eliminar** buttons, and a **＋ Añadir sección** control offering exactly the types in `ScreenTaxonomy.SECTION_TYPES` (hardcode the same five: HERO_SCORE, CARD_ROW, CARD_LIST, QUOTE_BANNER, CONTENT_FEED).
- Per-section fields by type: `title` for CARD_ROW/CARD_LIST/CONTENT_FEED, `text` for QUOTE_BANNER, `category` for CONTENT_FEED, and a card editor for CARD_ROW/CARD_LIST (título, subtítulo, imagen, badge, meta, y acción: tipo + target).
- **Guardar** issues the `PUT`; on 200 show the new version ("Guardado — versión N"); on 400 show the server's `message` verbatim (that is the authoritative reason). Never claim success on a non-2xx.
- A **JSON** toggle showing the current definition read-only, so an operator can see exactly what will be sent.
- Buttons disabled while a request is in flight.

Keep it one self-contained file (inline `<style>` and `<script>`). Aim for clarity over cleverness; it is going to be edited by hand later.

- [ ] **Step 3: Build and smoke-check the route**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :server:test --console=plain`
Expected: BUILD SUCCESSFUL (existing tests still green; the page has no test of its own — it is verified E2E in Task 5).

- [ ] **Step 4: Commit**

```bash
git add server/src/main/kotlin/com/neovita/server/routes/WebRoutes.kt server/src/main/resources/web/screen-editor.html server/src/main/kotlin/com/neovita/server/plugins/Routing.kt
git commit -m "feat(server): EMPLOYER-only screen editor page at /web/admin/screens"
```

---

### Task 4: Client — the Profile entry

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `WebContentScreen` (existing), the `state.user?.role == "EMPLOYER"` gate already used for `ContentAdminScreen` (around line 366).

- [ ] **Step 1: Add the entry**

Inside the same `if (state.user?.role == "EMPLOYER") { ... }` block that already offers "Administrar contenido", add — using the file's real `SettingsItem(icon, title, onClick)` helper and the same `navigator.parent?.push(...)` pattern:

```kotlin
                        SettingsItem(
                            icon = "🎛️",
                            title = "Editar pantallas",
                            onClick = {
                                navigator.parent?.push(
                                    WebContentScreen(title = "Editar pantallas", url = "/web/admin/screens")
                                )
                            }
                        )
```

Import `com.neovita.app.screens.web.WebContentScreen` if not already imported.

- [ ] **Step 2: Compile every target**

Run: `export ANDROID_HOME=/usr/local/share/android-commandlinetools && ./gradlew :androidApp:assembleDebug :webApp:compileKotlinWasmJs :shared:compileKotlinIosX64 --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt
git commit -m "feat(app): 'Editar pantallas' entry for EMPLOYER accounts"
```

---

### Task 5: E2E on the emulator (controller-run)

**Files:** none (verification only). AVD `Pixel_5_E2E`; dev JWT with `role=EMPLOYER` injected into shared_prefs (see the runbook note in memory).

- [ ] **Step 1: API-level E2E** — with the local server running and the EMPLOYER dev JWT: `GET /api/screens` lists `dashboard`; `PUT` a modified definition returns 200 with `version` bumped; a definition with an unknown section type returns 400 with the reason; a `USER`-role token gets 403.
- [ ] **Step 2: The editor in the app** — open Perfil → "Editar pantallas" → the page loads inside the WebView, lists the sections of `dashboard`, and the JSON view matches what the API returns.
- [ ] **Step 3: The payoff** — edit a card's title in the editor, Guardar, then reopen the app's Inicio tab and confirm the dashboard shows the new title. **No SQL, no deploy, no release.** This is the whole point of the sub-project; capture a screenshot of before and after.
- [ ] **Step 4: Negative** — save an invalid definition from the editor and confirm the page shows the server's Spanish reason and does not claim success.
