# SDUI de contenido — diseño

**Fecha:** 2026-07-26
**Alcance F1 (este spec ejecuta F1):** `:core` (schema wire del árbol de pantalla +
ApiService), `:server` (tabla `screen_definitions` + seed + ruta), `:shared`
(SduiRenderer + Dashboard piloto con fallback). F2 (editor admin) y F3 (extender a Plan
y pantallas nuevas) quedan especificadas aquí a nivel de dirección y se planifican en
ciclos propios.

## Problema / valor

Las pantallas de contenido de NeoVita están hardcodeadas en Compose (el Dashboard
rediseñado muestra `EXP_CARDS`/`HABIT_CARDS`/`PRACTICE_CARDS` inline) — cambiar una
tarjeta exige recompilar el wasm + Docker + Railway (web) y reinstalar apps nativas.
Peor: el pipeline de contenido server-driven que YA existe (`content_items` + CRUD +
pantalla admin EMPLOYER) quedó huérfano — `DashboardViewModel` baja el feed y la UI
nunca lo renderiza. El usuario quiere: pantallas de contenido definidas como DATOS,
editables al instante, con apps nativas de instalación única.

## Decisiones (locked)

- **SDUI de contenido, no framework completo:** solo pantallas de contenido; login,
  assessment, chat y flujos siguen nativos. Sin formularios ni lógica server-defined.
- **Definiciones en la DB** (elegido): tabla `screen_definitions`, editable en runtime.
  Seed inicial desde código con el contenido EXACTO del Dashboard actual (día 1 se ve
  idéntico, pero ya es data). F2 agrega el editor admin; hasta entonces se edita vía
  SQL/seed.
- **Schema cerrado y versionado en `:core`** (@Serializable, patrón `ContentTaxonomy`):
  el server y el cliente comparten los tipos. Tipos de sección v1:
  `HERO_SCORE` · `CARD_ROW` · `CARD_LIST` · `QUOTE_BANNER` · `CONTENT_FEED`.
  Acciones de tarjeta SOLO de lista blanca: `NAVIGATE(target ∈ {home,chat,plan,profile})`
  y `OPEN_URL(url)`. Nada arbitrario.
- **Tres capas anti-rotura (locked):**
  1. El renderer **ignora secciones/acciones de tipo desconocido** (el enum wire se
     deserializa tolerante: tipo no reconocido → la sección se descarta, la pantalla
     sigue) — apps viejas conviven con schemas nuevos.
  2. El cliente **cachea la última definición válida** (`LocalCache` existente:
     SQLDelight en android/ios; la web sin cache usa el fetch o el fallback).
  3. **Fallback nativo:** si no hay definición (fetch falló y sin cache) → el Dashboard
     hardcodeado actual se renderiza tal cual (se conserva exactamente para esto).
  Editar mal una pantalla NUNCA rompe la app: lo peor es ver la versión anterior.
- **Secciones "smart" vs "static":** `HERO_SCORE` y `CONTENT_FEED` reciben sus datos
  del ViewModel (scores del plan, feed de content_items — los caminos que ya existen);
  las demás vienen completas en el JSON. Sin lenguaje de bindings en v1 (YAGNI).
- **Versionado:** `ScreenDefinition.version: Int` (monotónico, lo sube cada write);
  cliente manda `If-None-Match: <version>` y el server responde 304 si no cambió.

## Diseño F1

### A — Wire schema (`core/.../network/dto/ScreenDto.kt`)

```kotlin
@Serializable data class ScreenDefinitionDto(
    val slug: String,            // "dashboard"
    val version: Int,
    val sections: List<SectionDto>,
)
@Serializable data class SectionDto(
    val type: String,            // HERO_SCORE | CARD_ROW | CARD_LIST | QUOTE_BANNER | CONTENT_FEED
    val title: String? = null,
    val cards: List<CardDto> = emptyList(),   // CARD_ROW / CARD_LIST
    val text: String? = null,                  // QUOTE_BANNER
    val category: String? = null,              // CONTENT_FEED: filtro opcional de content_items
)
@Serializable data class CardDto(
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val badge: String? = null,
    val action: ActionDto? = null,
)
@Serializable data class ActionDto(val type: String, val target: String)  // NAVIGATE|OPEN_URL

object ScreenTaxonomy {
    val SECTION_TYPES = listOf("HERO_SCORE", "CARD_ROW", "CARD_LIST", "QUOTE_BANNER", "CONTENT_FEED")
    val ACTION_TYPES = listOf("NAVIGATE", "OPEN_URL")
    val NAVIGATE_TARGETS = listOf("home", "chat", "plan", "profile")
}
```

`type` es String (no enum) a propósito: la tolerancia a desconocidos vive en el
renderer (skip), no en la deserialización.

### B — Server

- Tabla `screen_definitions(slug PK, version Int, sections_json Text, active Bool,
  updated_at)`, registrada en el DatabaseFactory (patrón `createMissingTablesAndColumns`
  existente).
- Seed en boot (patrón `ContentSeed`): `ScreenSeed.kt` con el slug `dashboard` cuyo
  contenido replica EXACTAMENTE las `EXP_CARDS`/`HABIT_CARDS`/`PRACTICE_CARDS` +
  título/estructura del Dashboard actual, más una sección `HERO_SCORE` arriba y una
  `CONTENT_FEED` al final (el rescate del huérfano). `seedIfEmpty` — no pisa ediciones.
- Ruta `GET /api/screens/{slug}` (autenticada como el resto de /api): 200 con el DTO,
  304 si `If-None-Match` == version, 404 si slug inexistente/inactivo.

### C — Cliente

- `ApiService.getScreen(slug: String, cachedVersion: Int?): ScreenDefinitionDto?`
  (null en 304 → usar cache). Cache vía la interfaz `LocalCache` existente (nueva
  entrada screen por slug: json + version; android/ios SQLDelight, web null-cache).
- `SduiRenderer(definition, state, callbacks)` en `shared/.../ui/sdui/`: mapea cada
  sección a componentes; al construirlo se EXTRAEN como componentes reutilizables las
  tarjetas hoy inline del Dashboard (la deuda del rediseño). Secciones smart toman
  `state` (scores, feed) del `DashboardViewModel` actual.
- `DashboardScreen`: intenta definición (cache → red); si hay → `SduiRenderer`; si no →
  el composable actual (renombrado `DashboardFallback`) intacto.

## F2 (dirección, ciclo propio)

Editor en la pantalla admin existente (rol EMPLOYER): lista de secciones reordenables,
forms por tipo, guardado → `PUT /api/screens/{slug}` (sube version). Validación
server-side contra `ScreenTaxonomy`.

## F3 (dirección)

`PlanScreen` a SDUI; pantallas de contenido NUEVAS = fila nueva con slug nuevo + entrada
de navegación genérica; `CONTENT_FEED` con más filtros.

## Testing F1

- **Unit `:core`:** (de)serialización del schema; sección de tipo desconocido se
  DEserializa sin explotar (String type) y el renderer la salta (test del filtro de
  secciones renderizables, lógica pura extraída `renderableSections(def)`).
- **Server (harness de tests existente):** GET screen 200/304/404; seed idempotente;
  auth requerida.
- **Compile:** todos los targets de `:shared` + `:core`; web dist.
- **E2E local (server + Postgres nativo Homebrew — Docker no está en esta máquina):**
  UPDATE del sections_json por SQL (simula edición admin) → refresh en la web → la
  pantalla cambia SIN redeploy. La prueba de la promesa.

## Fuera de alcance (futuro)

Editor admin (F2); Plan/pantallas nuevas (F3); bindings genéricos; forms/acciones
arbitrarias; segmentación por usuario/rol de secciones; A/B.
