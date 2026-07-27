# SDUI de Contenido F1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El Dashboard de NeoVita se renderiza desde una definición servida por la DB (editable sin deploy), con renderer tolerante, cache local y fallback nativo intacto.

**Architecture:** Schema wire cerrado en `:core` (@Serializable, `type` como String tolerante) + `renderableSections` puro que filtra tipos/acciones desconocidos; tabla `screen_definitions` + seed (contenido EXACTO del Dashboard actual, más `HERO_SCORE` y el rescate del `CONTENT_FEED` huérfano) + `GET /api/screens/{slug}` con 304 por version; `ApiService.getScreen` + cache en `LocalCache` (SQLDelight nativas, web sin cache); `SduiRenderer` mapea secciones a componentes (extrayendo la tarjeta inline como componente) y `DashboardScreen` usa definición→renderer o fallback nativo.

**Tech Stack:** Ktor/Exposed/Postgres (H2 en tests, `ktor-server-test-host` ya es dep), kotlinx.serialization, SQLDelight, Compose Multiplatform, Voyager.

**Spec:** `docs/superpowers/specs/2026-07-26-sdui-contenido-design.md`

## Global Constraints

- Repo `/Users/carolinarestrepo/Developer/NeoVita`, branch `feat/sdui-contenido`. JDK: el del proyecto (jvmTarget 17, sin pin JBR — el JAVA_HOME actual sirve). Deps nuevas SOLO vía `gradle/libs.versions.toml`.
- Tipos de sección LOCKED: `HERO_SCORE`, `CARD_ROW`, `CARD_LIST`, `QUOTE_BANNER`, `CONTENT_FEED`. Acciones LOCKED: `NAVIGATE` (targets `home|chat|plan|profile`) y `OPEN_URL`. `type` es String — la tolerancia vive en `renderableSections`, no en la deserialización.
- Anti-rotura LOCKED: sección desconocida se salta; acción inválida se strippea (tarjeta sin acción); sin definición válida (ni red ni cache) → `DashboardFallback` (el composable actual, intacto).
- **Refinamiento documentado del spec:** `CardDto` gana `meta: String? = null` (el rating "4.9" de las tarjetas actuales). Los gradientes por-tarjeta del Dashboard actual NO viajan en el DTO — el renderer aplica un scrim estándar sobre `imageUrl` (visualmente MUY cercano, no pixel-perfect; aceptado).
- Paquetes: DTOs en `com.neovita.shared.network.dto`; server en `com.neovita.server.*`; UI en `com.neovita.app.ui.sdui`. Seguir patrón `Content*` (tabla/repo/ruta/seed) y `ApiService` (`Result` + `safeCall`, baseUrl ya incluye `/api`).
- Cada tarea termina verde y con commit. E2E final contra Postgres nativo Homebrew (Docker no existe en esta máquina): DB `neovita` en el postgresql@16 local.

## File Structure

```
core/.../shared/network/dto/ScreenDto.kt              [C] DTOs + ScreenTaxonomy + renderableSections
core/src/<testSourceSet>/…/ScreenDtoTest.kt           [C] unit (usar el source set de CalculateScoresUseCaseTest)
core/.../shared/network/ApiService.kt                 [M] getScreen
core/.../shared/data/cache/LocalCache.kt              [M] cacheScreen/getScreen
core/src/nonWasmMain/.../SqlDelightLocalCache.kt      [M] impl + tabla .sq nueva
server/.../db/tables/ScreensTable.kt                  [C]
server/.../db/repositories/ScreenRepository.kt        [C] getActive(slug), seedIfEmpty
server/.../db/ScreenSeed.kt                           [C] dashboard seed (contenido actual)
server/.../db/DatabaseFactory.kt                      [M] registrar + seed
server/.../routes/ScreenRoutes.kt                     [C] GET /screens/{slug} (200/304/404, auth)
server/.../plugins/Routing.kt                         [M] registrar
server/src/test/.../routes/ScreenRoutesTest.kt        [C] HTTP harness NUEVO (testApplication + H2)
shared/.../ui/sdui/SduiRenderer.kt                    [C] + section composables
shared/.../ui/sdui/SduiCard.kt                        [C] tarjeta extraída (reemplaza inline del Dashboard)
shared/.../screens/dashboard/DashboardScreen.kt       [M] def→renderer | fallback (rename del actual)
shared/.../screens/dashboard/DashboardViewModel.kt    [M] carga cache→red de la definición
```

---

### Task 1: Schema wire + tolerancia (TDD, :core)

**Files:**
- Create: `core/src/commonMain/kotlin/com/neovita/shared/network/dto/ScreenDto.kt`
- Test: en el MISMO source set donde vive `CalculateScoresUseCaseTest` (localizarlo con `find core/src -name "CalculateScoresUseCaseTest.kt"` y colocar `ScreenDtoTest.kt` junto a él): `.../ScreenDtoTest.kt`

**Interfaces:**
- Produces (Tasks 2-4): los DTOs y `ScreenTaxonomy` EXACTOS del spec §A (copiarlos de `docs/superpowers/specs/2026-07-26-sdui-contenido-design.md`, agregando `meta: String? = null` a `CardDto` tras `badge`), más:

```kotlin
/** Secciones que un renderer de esta versión sabe dibujar, con acciones inválidas strippeadas. */
fun renderableSections(def: ScreenDefinitionDto): List<SectionDto> =
    def.sections
        .filter { it.type in ScreenTaxonomy.SECTION_TYPES }
        .map { section ->
            section.copy(cards = section.cards.map { card ->
                if (card.action != null && !isValidAction(card.action)) card.copy(action = null) else card
            })
        }

private fun isValidAction(a: ActionDto): Boolean = when (a.type) {
    "NAVIGATE" -> a.target in ScreenTaxonomy.NAVIGATE_TARGETS
    "OPEN_URL" -> a.target.startsWith("https://")
    else -> false
}
```

- [ ] **Step 1: Test que falla**

```kotlin
package com.neovita.shared.network.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrip_serializes_and_deserializes() {
        val def = ScreenDefinitionDto(
            slug = "dashboard", version = 3,
            sections = listOf(
                SectionDto(type = "CARD_ROW", title = "Experiencias", cards = listOf(
                    CardDto(title = "Yoga", subtitle = "Tulum", imageUrl = "https://x/y.jpg",
                        badge = "Desde \$25 USD", meta = "4.9",
                        action = ActionDto("NAVIGATE", "plan")),
                )),
                SectionDto(type = "QUOTE_BANNER", text = "Muévete cada día"),
            ),
        )
        val decoded = json.decodeFromString<ScreenDefinitionDto>(json.encodeToString(ScreenDefinitionDto.serializer(), def))
        assertEquals(def, decoded)
    }

    @Test
    fun unknown_section_type_deserializes_and_is_filtered() {
        val raw = """{"slug":"dashboard","version":1,"sections":[
            {"type":"HOLOGRAM_3D","title":"Futuro"},
            {"type":"QUOTE_BANNER","text":"hola"}]}"""
        val def = json.decodeFromString<ScreenDefinitionDto>(raw)
        assertEquals(2, def.sections.size)              // deserializa sin explotar
        val renderable = renderableSections(def)
        assertEquals(1, renderable.size)                // el desconocido se salta
        assertEquals("QUOTE_BANNER", renderable[0].type)
    }

    @Test
    fun invalid_actions_are_stripped_not_fatal() {
        val def = ScreenDefinitionDto("s", 1, listOf(
            SectionDto(type = "CARD_LIST", cards = listOf(
                CardDto(title = "a", action = ActionDto("NAVIGATE", "settings")),   // target fuera de lista
                CardDto(title = "b", action = ActionDto("EXPLODE", "x")),            // tipo desconocido
                CardDto(title = "c", action = ActionDto("OPEN_URL", "http://insecure")), // no https
                CardDto(title = "d", action = ActionDto("NAVIGATE", "plan")),        // válida
            )),
        ))
        val cards = renderableSections(def)[0].cards
        assertNull(cards[0].action); assertNull(cards[1].action); assertNull(cards[2].action)
        assertEquals(ActionDto("NAVIGATE", "plan"), cards[3].action)
    }
}
```

Run (ajustar al target del source set encontrado, p.ej.): `./gradlew :core:jvmTest --tests "*.ScreenDtoTest"` → FAIL (unresolved).

- [ ] **Step 2: Implementar** `ScreenDto.kt` = spec §A + `meta` + `renderableSections`/`isValidAction` de arriba. → GREEN.

- [ ] **Step 3: Commit** — `git add core/src && git commit -m "feat(core): schema wire de pantallas SDUI con tolerancia a tipos desconocidos"`

---

### Task 2: Server — tabla, seed del Dashboard actual, ruta (TDD HTTP con harness nuevo)

**Files:**
- Create: `server/src/main/kotlin/com/neovita/server/db/tables/ScreensTable.kt`, `.../db/repositories/ScreenRepository.kt`, `.../db/ScreenSeed.kt`, `.../routes/ScreenRoutes.kt`
- Modify: `server/src/main/kotlin/com/neovita/server/db/DatabaseFactory.kt`, `.../plugins/Routing.kt`
- Modify: `gradle/libs.versions.toml` + `server/build.gradle.kts` SOLO si falta H2 (`testImplementation(libs.h2)`; verificar catálogo primero)
- Test: `server/src/test/kotlin/com/neovita/server/routes/ScreenRoutesTest.kt`

**Interfaces:**
- Consumes: `ScreenDefinitionDto` etc. (Task 1); patrón `ContentTable`/`ContentRepository`/`contentRoutes`/`SEED_CONTENT` (leerlos primero); `JwtService` para mint de tokens en el test.
- Produces: `object ScreensTable : Table("screen_definitions")` (slug PK varchar(64), version Int, sectionsJson Text, active Bool default true, updatedAt Long); `class ScreenRepository { fun getActive(slug): ScreenDefinitionDto?; fun seedIfEmpty(defs: List<ScreenDefinitionDto>) }` (serializa/deserializa sectionsJson con kotlinx Json); `GET /api/screens/{slug}` dentro de `authenticate("jwt-auth")`: 200 DTO | 304 si header `If-None-Match` == version.toString() | 404 si no existe o `!active`; registrado en Routing con `screenRoutes(ScreenRepository())`.

- [ ] **Step 1: Seed** — `ScreenSeed.kt`: `val SEED_SCREENS = listOf(dashboardScreen())` donde `dashboardScreen()` construye `ScreenDefinitionDto(slug="dashboard", version=1, sections=[...])` replicando el Dashboard actual: leer `shared/.../screens/dashboard/DashboardScreen.kt` y transcribir LAS MISMAS tarjetas de `EXP_CARDS`, `HABIT_CARDS` y `PRACTICE_CARDS` (título → `title`, subtítulo → `subtitle`, url → `imageUrl`, precio → `badge`, rating → `meta`; los gradientes NO viajan — constraint global) como tres secciones `CARD_ROW` con los títulos de sección que la pantalla actual muestra; anteponer `SectionDto(type="HERO_SCORE")` y anexar `SectionDto(type="CONTENT_FEED", title="Para ti")`.

- [ ] **Step 2: Test HTTP que falla** — `ScreenRoutesTest.kt` es el PRIMER harness HTTP del repo; construirlo con `testApplication` + config de test apuntando a H2 en memoria (`environment { config = MapApplicationConfig("database.url" to "jdbc:h2:mem:screens_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "database.driver" to "org.h2.Driver", "jwt.secret" to "<secreto-test-de-32+chars>", ...las claves que application.conf exija — leerlo) }` + `application { module() }` o el wiring parcial mínimo que el módulo real permita (leer `Application.kt`; si `module()` exige `CLAUDE_API_KEY`, dar un valor dummy por config). Mint del token con `JwtService` real y el secreto de test. Tests:

```kotlin
    @Test fun `screen 200 with seeded dashboard`()      // GET /api/screens/dashboard + Bearer → 200; slug=="dashboard"; sections.size==5; version==1
    @Test fun `screen 304 when version matches`()        // + header If-None-Match: "1" → 304, cuerpo vacío
    @Test fun `screen 404 for unknown slug`()            // GET /api/screens/nope → 404
    @Test fun `screen requires auth`()                   // sin Bearer → 401
    @Test fun `seed is idempotent`()                     // re-invocar seedIfEmpty no duplica ni pisa (update manual de version sobrevive)
```

(Los cuerpos con asserts concretos sobre el JSON, patrón kotlinx `Json.parseToJsonElement`; escribirlos completos en el archivo.)

Run: `./gradlew :server:test --tests "*.ScreenRoutesTest"` → FAIL (ruta inexistente).

- [ ] **Step 3: Implementar** tabla/repo/ruta/registro + DatabaseFactory (`ScreensTable` en `createMissingTablesAndColumns` + `ScreenRepository().seedIfEmpty(SEED_SCREENS)`). → GREEN + `./gradlew :server:test` completo verde.

- [ ] **Step 4: Commit** — `feat(server): screen_definitions + seed del dashboard + GET /api/screens/{slug} con 304`

---

### Task 3: Cliente — ApiService + cache local (TDD donde es puro)

**Files:**
- Modify: `core/.../network/ApiService.kt`; `core/.../data/cache/LocalCache.kt`; `core/src/nonWasmMain/.../SqlDelightLocalCache.kt` + el `.sq` de SQLDelight (localizar el archivo de schema con `find core/src -name "*.sq"` y AGREGAR la tabla al final):

```sql
CREATE TABLE IF NOT EXISTS cached_screen (
    slug TEXT NOT NULL PRIMARY KEY,
    version INTEGER NOT NULL,
    json TEXT NOT NULL
);
upsertScreen:
INSERT OR REPLACE INTO cached_screen(slug, version, json) VALUES (?, ?, ?);
selectScreen:
SELECT version, json FROM cached_screen WHERE slug = ?;
```

**Interfaces:**
- Produces: `ApiService.getScreen(slug: String, cachedVersion: Int? = null): Result<ScreenDefinitionDto?>` — 304 → `Result.success(null)`; `LocalCache.cacheScreen(slug: String, version: Int, json: String)` y `LocalCache.getScreen(slug: String): CachedScreen?` con `data class CachedScreen(val version: Int, val json: String)` (en el archivo de LocalCache, junto a los Cached* existentes).

- [ ] **Step 1:** `ApiService.getScreen` (patrón `safeCall`; mandar `If-None-Match` si `cachedVersion != null`; si `response.status == NotModified` retornar null — leer cómo `safeCall` maneja status no-2xx y adaptar: probablemente exija manejar 304 ANTES del `.body()`). `LocalCache` + impl SQLDelight + regenerar (`./gradlew :core:generateSqlDelightInterface` o el task equivalente que exista — listar con `:core:tasks | grep -i sql`). Web: sin cambios (cache null).
- [ ] **Step 2:** Compilar todos los targets de `:core` (`./gradlew :core:build -x los targets iOS release si hay OOM análogo; mínimo :core:jvmTest :core:compileKotlinWasmJs y el android`). Tests de Task 1 siguen verdes.
- [ ] **Step 3: Commit** — `feat(core): getScreen con 304 + cache local de definiciones de pantalla`

---

### Task 4: SduiRenderer + Dashboard piloto con fallback

**Files:**
- Create: `shared/src/commonMain/kotlin/com/neovita/app/ui/sdui/SduiRenderer.kt`, `.../ui/sdui/SduiCard.kt`
- Modify: `shared/.../screens/dashboard/DashboardScreen.kt`, `.../screens/dashboard/DashboardViewModel.kt`

**Interfaces:**
- Consumes: Task 1 DTOs + `renderableSections`; Task 3 `getScreen`/cache; estado actual del `DashboardViewModel` (leerlo: ya carga plan/scores y feed de content).
- Produces: `@Composable fun SduiRenderer(definition: ScreenDefinitionDto, scores: /*tipo real del state*/, feed: List<ContentItem>, onNavigateTab: (String) -> Unit, onOpenUrl: (String) -> Unit)`.

- [ ] **Step 1: SduiCard** — extraer el composable de tarjeta que hoy vive inline en `DashboardScreen` (el que renderiza `ExpCard`) a `SduiCard(card: CardDto, onClick: (() -> Unit)?)`: misma geometría/tipografía; el gradiente por-tarjeta se reemplaza por un scrim estándar (negro 0→60%) sobre la imagen; `badge` abajo-izquierda y `meta` (rating) arriba-derecha como hoy. El Dashboard fallback SIGUE usando su versión actual sin tocar (no compartir el componente con el fallback — el fallback es la póliza de seguro, no se refactoriza).
- [ ] **Step 2: SduiRenderer** — `LazyColumn`/`Column` sobre `renderableSections(definition)`: `HERO_SCORE` → el mismo bloque de score del Dashboard actual (extraído o invocado con `scores`); `CARD_ROW` → `LazyRow` de `SduiCard`; `CARD_LIST` → columna de `SduiCard`; `QUOTE_BANNER` → banner con `text` (estilo del design system); `CONTENT_FEED` → lista de `feed` (filtrada por `section.category` si viene) con el item-card del feed (nuevo, simple: título/teaser/minutos). Acciones: `NAVIGATE` → `onNavigateTab(target)`; `OPEN_URL` → `onOpenUrl(target)` (expect/actual o `uriHandler` de Compose — usar `LocalUriHandler`).
- [ ] **Step 3: ViewModel + Screen** — `DashboardViewModel`: al cargar, `cache = localCache?.getScreen("dashboard")`; `getScreen("dashboard", cache?.version)` → si DTO nuevo: usarlo + `cacheScreen(json crudo re-serializado, version)`; si null (304): decodificar cache; si falla todo: `screenDef = null`. `DashboardScreen`: `state.screenDef?.let { SduiRenderer(it, ...) } ?: DashboardFallback(...)` — renombrar el cuerpo actual a `DashboardFallback` SIN modificarlo. Navegación de tabs: el `TabNavigator` de `MainScreen` — pasar un callback que cambie de tab por nombre (leer `navigation/tabs/*` para el mecanismo real; si cambiar de tab desde dentro exige `LocalTabNavigator`, usarlo).
- [ ] **Step 4: Compilar** todos los targets de `:shared` (android + wasmJs mínimo; iOS si no hay OOM análogo al de movi — si los links release fallan por heap, usar debug targets y anotarlo).
- [ ] **Step 5: Commit** — `feat(ui): SduiRenderer + Dashboard servido por definición con fallback nativo`

---

### Task 5: E2E — la promesa (server local + edición sin deploy)

**Files:** ninguno commiteado.

- [ ] **Step 1:** Postgres nativo: `createdb neovita 2>/dev/null || true` (el postgresql@16 de Homebrew ya corre para movi; usuario del sistema). Exportar `DB_URL="jdbc:postgresql://localhost:5432/neovita?user=$(whoami)"`, `JWT_SECRET` de dev (32+ chars), `CLAUDE_API_KEY` dummy (`sk-dummy` — el chat no se toca en este e2e). `./gradlew :server:run` en background; `/health` → OK.
- [ ] **Step 2:** Registrar sesión de prueba: NeoVita solo tiene login Google — para el e2e, mint directo de un JWT con `JwtService` NO es posible por curl; alternativa: insertar un usuario por SQL y generar el token con un script Kotlin NO existe... **Camino simple y suficiente:** probar la ruta con un token generado por el MISMO mecanismo del harness de test (agregar en el e2e un mini test de integración `:server:test` marcado que apunte a Postgres NO — demasiado). DECISIÓN: el e2e de API se hace con un usuario insertado por SQL + token minted con una `main` temporal de kotlin script... Para NO sobre-ingenierizar: el e2e usa `ScreenRoutesTest` (H2) para el contrato, y la PROMESA se verifica así:
  1. `curl -s localhost:8080/api/screens/dashboard` sin auth → 401 (ruta viva).
  2. `psql neovita -c "SELECT slug, version FROM screen_definitions"` → seed presente (dashboard, v1).
  3. `psql neovita -c "UPDATE screen_definitions SET sections_json = REPLACE(sections_json, 'Yoga al amanecer', 'Yoga E2E SIN DEPLOY'), version = version + 1 WHERE slug='dashboard'"`.
  4. `psql neovita -c "SELECT version FROM screen_definitions WHERE slug='dashboard'"` → 2 (la edición vive en runtime; el server la servirá en el próximo GET sin reinicio — verificable si se dispone de token: opcional, no bloqueante).
  5. Reiniciar el server → `SELECT` de nuevo → sigue v2 con "Yoga E2E" (el seed NO pisó la edición — idempotencia real).
- [ ] **Step 3:** Matar server, `dropdb neovita` si se desea limpiar (dejarla es aceptable), `git push -u origin feat/sdui-contenido`. Reporte completo.

**Nota honesta:** la verificación visual completa (web renderizando la edición) requiere sesión Google real — queda como verificación manual del usuario post-deploy (documentado). El contrato server↔cliente queda cubierto por los tests HTTP + unit del renderer-filtro.
