# Bottom Nav + Screen Redesign

**Date:** 2026-03-29
**Branch:** feature/neovita-mvp

## Overview

Add a Voyager `TabNavigator`-based bottom navigation bar to NeoVita and redesign the four main screens (Dashboard, Chat, Plan, Profile) to match the Stitch mockups.

## Navigation Architecture

### Flow

```
LoginScreen
  └── (replace) MainScreen
                  └── TabNavigator
                        ├── HomeTab   → DashboardScreen
                        ├── ChatTab   → ChatScreen
                        ├── PlanTab   → PlanScreen
                        └── ProfileTab → ProfileScreen
                                           └── (push) AssessmentScreen
                                                         └── (push) ResultsScreen
```

### New Files

| File | Purpose |
|------|---------|
| `screens/main/MainScreen.kt` | Hosts `TabNavigator` + `NavigationBar` scaffold |
| `navigation/tabs/HomeTab.kt` | Tab object for Dashboard |
| `navigation/tabs/ChatTab.kt` | Tab object for Chat |
| `navigation/tabs/PlanTab.kt` | Tab object for Plan |
| `navigation/tabs/ProfileTab.kt` | Tab object for Profile |

### Modified Files

| File | Change |
|------|--------|
| `navigation/AppNavigation.kt` | Add `MainScreen` as navigation destination |
| `screens/login/LoginScreen.kt` | Navigate to `MainScreen` instead of `DashboardScreen` |
| `screens/dashboard/DashboardScreen.kt` | Visual redesign |
| `screens/chat/ChatScreen.kt` | Visual redesign |
| `screens/plan/PlanScreen.kt` | Visual redesign |
| `screens/profile/ProfileScreen.kt` | Visual redesign |

### Tab Implementation

Each `Tab` is an `object` implementing `cafe.adriel.voyager.navigator.tab.Tab`:

```kotlin
object HomeTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Home")

    @Composable
    override fun Content() {
        Navigator(DashboardScreen())
    }
}
```

`ProfileTab.Content()` uses its own `Navigator` so that pushing `AssessmentScreen` stays within the tab stack.

### MainScreen

`MainScreen` implements `Screen` and renders:
- `TabNavigator(HomeTab)` as the root
- `Scaffold` with a `NavigationBar` at the bottom
- `CurrentTab()` as the body content

## Bottom Navigation Bar Design

- Background: `NeoNavy` (#1A2B4A)
- 4 items: **HOME**, **CHAT**, **PLAN**, **PROFILE**
- Icons: `Icons.Rounded` — `Home`, `Chat`, `List`, `Person`
- Active state: teal circular background (`NeoTeal700`) behind icon, label in teal
- Inactive state: gray icon + label
- Labels: uppercase, `labelSmall` typography

## Screen Redesigns

### Dashboard (Home)

**Top bar:** `NeoVita` title (left) + user avatar circle (right)

**Body (scrollable):**
1. Greeting: `"Hola, {name} 👋"` (`headlineMedium`, `NeoNavy`, bold) + subtitle `"Tu santuario de longevidad está listo."` (gray)
2. Centered `ScoreRing` (existing component, size 160dp) showing overall score + label "ÍNDICE GENERAL"
3. Row of 3 `MetricCard`s: Ejercicio, Sueño, Nutrición — each with a small `ScoreRing` (72dp) and score value
4. `TaskCard` (teal gradient background) showing "TAREA DE HOY" label, task name bold, description, "0% completado" footer — only shown when `plan != null`
5. Welcome card (existing) when `plan == null`

### Chat

**Top bar:** `"NeoVita"` (left) + `"Coach NeoVita"` (center, subtitle style) + avatar (right)

**Body:** No functional changes. Visual improvements:
- User bubbles: `NeoTeal700` background, white text, rounded top-left corners
- AI bubbles: `NeoSurface` background, `NeoNavy` text, rounded top-right corners
- Suggestion chips row styled with `NeoTeal200` background

### Plan

**Header:**
- Title line 1: `"Mi Plan de"` (black, `headlineLarge`, bold)
- Title line 2: `"Longevidad"` (teal, `headlineLarge`, bold)
- Subtitle: `"Optimiza tu salud celular con recomendaciones personalizadas basadas en tu perfil biológico."` (gray, `bodyMedium`)

**Body:** Existing expandable `PlanSection` cards — add emoji icons to section headers.

**Footer:** `"Generar mi plan"` button + `"ÚLTIMA ACTUALIZACIÓN: {date}"` timestamp below button.

### Profile

**Header:** Avatar circle (`NeoTeal200` bg, initials `NeoTeal900`) + name + email

**Stats row:** Two cards side by side — `EDAD {n} años` and `ESTADO Vital`

**Próximo Hito card** (`NeoTeal700` background): `"Meta de Longevidad: 95+"` + `LinearProgressIndicator` + `"65% completado de tu plan actual"`

**Action buttons:** `"Realizar nueva evaluación"` (outlined teal) + `"Cerrar sesión"` (outlined red)

**Configuración de Cuenta:** Section header + 3 `SettingsListItem`s:
- Información Personal
- Notificaciones
- Privacidad y Seguridad

Each item has a leading icon, label, and trailing `>` chevron.

**Quote banner:** Teal/dark card with italic quote text and `"— Sabiduría NeoVita"` attribution.

## New Shared Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `MetricCard` | `ui/components/` | Mini score ring + label for dashboard pillars |
| `TaskCard` | `ui/components/` | Teal "Tarea de hoy" card with progress |
| `SettingsListItem` | `ui/components/` | Row with icon + label + chevron for Profile |
| `QuoteBanner` | `ui/components/` | Dark card with quote text |
| `NeoTopBar` | `ui/components/` | Consistent top app bar across all main screens |

## Out of Scope

- `AssessmentScreen` and `ResultsScreen` visual redesign (accessible from Profile, no redesign this iteration)
- `OnboardingScreen` redesign
- Real user avatar image (initials placeholder sufficient)
- Notificaciones / Información Personal / Privacidad screens (navigation items present but no destination yet — tapping shows nothing)
