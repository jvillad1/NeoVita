# Install Once, Update via Web — Strategy Design

**Date:** 2026-07-26
**Branch:** claude/neovita-cdeac2
**Status:** Approved (strategy); sub-project 1 goes to detailed planning next

## Goal

Users install the native NeoVita app **once** from the App Store / Play Store. From then
on, the product evolves through **server/web deploys** (Railway). Store releases become
rare events reserved for changes to native capabilities — not a requirement for shipping
product improvements.

## Decision context

Options considered:

- **A. Thin native shell + WebView loading the wasm web app + native bridges.** Rejected:
  Google Sign-In is blocked inside webviews, offline story is weak, Apple 4.2
  ("minimum functionality") risk, and wasm-in-WebView performance is unproven on
  mid-range devices.
- **B. Keep native Compose apps; make store releases rare via server-driven surface.**
  **Chosen.** Best UX and performance, SQLDelight offline cache already exists, native
  push/health are straightforward, no friction with Apple review.
- **C. WebView shell on Android, native on iOS.** Rejected: two divergent codepaths.

Constraints confirmed with the user:

- Must be published on both App Store and Google Play.
- Native capabilities wanted within the next year: push notifications,
  HealthKit/Health Connect, offline (nice-to-have only — "opens and shows something
  reasonable without network" is enough).
- iOS 18+ minimum is acceptable.

## Architecture: four mechanisms

### 1. Server as the product brain (already largely true)

Plans, AI chat (Claude prompts), scoring, and educational content (`/api/content` CRUD +
admin screen) live server-side. **Principle: new business logic defaults to the server.**
Changing any of it is a Railway deploy, never a store release.

### 2. Remote config + version gate (new — the centerpiece)

Extend `GET /api/config` (added 2026-07 for the Google client ID) to serve:

```json
{
  "googleClientId": "…",
  "features": { "chat": true, "healthSync": false },
  "minVersion": { "android": 3, "ios": 2 },
  "maintenance": false,
  "webScreens": [ { "id": "recetas", "title": "Recetas", "icon": "…", "url": "/web/recetas" } ]
}
```

Client behavior at startup and on each return to foreground:

- Feature flag off → the screen/entry point is hidden. Features can **ship dormant** in
  the binary and be enabled server-side months later.
- `minVersion` above the installed build → full-screen "Actualiza la app" gate. This is
  the escape hatch for the rare forced update.
- `maintenance: true` → maintenance screen.
- Config fetch fails → last cached config, else safe defaults (everything currently
  shipped stays on; no lockout without a server statement).

### 3. WebView slots inside the native app (new — web deploys inside the installed app)

A `WebScreen(url)` composable in `:shared` backed by platform webviews via
expect/actual (Android `WebView`, iOS `WKWebView`; on wasm target it's a plain link/new
tab). Session JWT is injected so pages served by our own Ktor server are authenticated.
Menu/dashboard entries come from `webScreens` in remote config, so **new screens
(content, surveys, promos, experiments) deploy with a push to Railway** and appear in
already-installed apps. A web experiment that matures gets "graduated" to native Compose
in the next planned release.

Scope guard: webview slots are for **secondary/content surfaces**, not core flows
(login, assessment, plan, chat stay native).

### 4. Irreducibly native capabilities, shipped once, designed generically

- **Native Google Sign-In** (today `TODO()` on Android/iOS — without it native apps are
  unusable). Android: Credential Manager. iOS: Google Sign-In SDK or
  ASWebAuthenticationSession. The server already verifies id_tokens (incl. `aud` check).
- **Push** (FCM/APNs): generic payload contract — "show title/body, on tap open deep
  link or URL" — so the server controls behavior without binary changes.
- **Health** (Health Connect/HealthKit): upload raw metrics (steps, sleep, HR) to a new
  endpoint; the server decides how coaching uses them.

Contracts are deliberately generic so they evolve server-side without store releases.

## Sub-projects (each gets its own spec → plan → implementation)

| # | Sub-project | Why this order |
|---|-------------|----------------|
| 1 | Native Google Sign-In — Android first, then iOS | Hard prerequisite; native apps are unusable without login |
| 2 | Remote config + version gate | Small; unlocks "ship dormant, enable via server" and forced-update escape hatch |
| 3 | WebView slots | Delivers the "deploy web into the installed app" capability |
| 4 | Push notifications | Can land in the first big store release |
| 5 | Health integrations | Largest native surface; after push |

1→2→3 delivers the install-once/update-via-web loop as early as possible.

## Error handling (strategy level)

- Remote config unreachable → cached/default config; never brick the app.
- WebView slot fails to load → in-slot retry state, never blocks native navigation.
- Version gate only triggers on an explicit server statement (`minVersion`), not on
  network errors.

## Testing (strategy level)

- Config parsing/gating logic in `:core` commonTest (pure logic, no platform).
- Server route tests for the extended `/api/config`.
- Each sub-project defines its own test plan in its spec.

## Out of scope (this strategy doc)

- Railway deploy of the current branch (separate pending task).
- The web (wasm) target keeps working as today; nothing here regresses it.
- Store listing/assets work (screenshots, privacy labels) — handled per release.
