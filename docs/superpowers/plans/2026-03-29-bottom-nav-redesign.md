# Bottom Nav + Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Voyager TabNavigator bottom nav bar (Home, Chat, Plan, Profile) and redesign all four main screens to match the Stitch mockups.

**Architecture:** `MainScreen` hosts a Voyager `TabNavigator` with four `Tab` objects, each rendering its screen. A Material3 `NavigationBar` sits at the bottom of a `Scaffold`. Profile-level navigation (Assessment push, logout) targets the root navigator via `navigator.parent`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager 1.0.0 (`voyager-navigator` + `voyager-tab-navigator`), Koin 4.0, Material3.

---

## File Map

**Create:**
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/main/MainScreen.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/HomeTab.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/ChatTab.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/PlanTab.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/ProfileTab.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/NeoTopBar.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/MetricCard.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/TaskCard.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/SettingsListItem.kt`
- `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/QuoteBanner.kt`

**Modify:**
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/login/LoginScreen.kt` — navigate to `MainScreen` instead of `DashboardScreen`
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt` — fix nav calls + full redesign
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/results/ResultsScreen.kt` — navigate to `MainScreen` on finish
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/dashboard/DashboardScreen.kt` — full redesign
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/chat/ChatScreen.kt` — full redesign
- `composeApp/src/commonMain/kotlin/com/neovita/app/screens/plan/PlanScreen.kt` — full redesign

---

## Navigator Topology

```
Root AppNavigation Navigator
  └── MainScreen               ← replaced from LoginScreen on login/demo
        └── TabNavigator (Voyager internal Navigator, parent = root)
              ├── HomeTab  → DashboardScreen().Content()
              ├── ChatTab  → ChatScreen().Content()
              ├── PlanTab  → PlanScreen().Content()
              └── ProfileTab → ProfileScreen().Content()
                                 navigator.parent = ROOT
                                 .push(AssessmentScreen())  → pushed on ROOT
                                 .replaceAll(LoginScreen())  → full logout on ROOT
```

`AssessmentScreen` and `ResultsScreen` are pushed/replaced on the ROOT navigator, so they appear full-screen with no bottom nav.

`ResultsScreen` ends with `navigator.replaceAll(MainScreen())` to land on the Home tab after the assessment flow.

---

## Task 1: Create Tab Objects

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/HomeTab.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/ChatTab.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/PlanTab.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/navigation/tabs/ProfileTab.kt`

- [ ] **Step 1: Create HomeTab.kt**

```kotlin
package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.dashboard.DashboardScreen

object HomeTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Home")

    @Composable
    override fun Content() = DashboardScreen().Content()
}
```

- [ ] **Step 2: Create ChatTab.kt**

```kotlin
package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.chat.ChatScreen

object ChatTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Chat")

    @Composable
    override fun Content() = ChatScreen().Content()
}
```

- [ ] **Step 3: Create PlanTab.kt**

```kotlin
package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.plan.PlanScreen

object PlanTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Plan")

    @Composable
    override fun Content() = PlanScreen().Content()
}
```

- [ ] **Step 4: Create ProfileTab.kt**

```kotlin
package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.profile.ProfileScreen

object ProfileTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Profile")

    @Composable
    override fun Content() = ProfileScreen().Content()
}
```

---

## Task 2: Create MainScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/main/MainScreen.kt`

- [ ] **Step 1: Write MainScreen.kt**

```kotlin
package com.neovita.app.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.neovita.app.navigation.tabs.ChatTab
import com.neovita.app.navigation.tabs.HomeTab
import com.neovita.app.navigation.tabs.PlanTab
import com.neovita.app.navigation.tabs.ProfileTab
import com.neovita.app.ui.theme.NeoNavy
import com.neovita.app.ui.theme.NeoTeal500
import com.neovita.app.ui.theme.NeoTeal700

class MainScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(HomeTab) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = NeoNavy) {
                        TabItem(HomeTab, "HOME", "🏠")
                        TabItem(ChatTab, "CHAT", "💬")
                        TabItem(PlanTab, "PLAN", "📋")
                        TabItem(ProfileTab, "PROFILE", "👤")
                    }
                }
            ) { paddingValues ->
                Box(Modifier.padding(paddingValues)) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, label: String, icon: String) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current == tab
    NavigationBarItem(
        selected = selected,
        onClick = { tabNavigator.current = tab },
        icon = {
            Text(icon, style = MaterialTheme.typography.titleSmall)
        },
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = NeoTeal500,
            selectedTextColor = NeoTeal500,
            indicatorColor = NeoTeal700.copy(alpha = 0.3f),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray
        )
    )
}
```

---

## Task 3: Wire Navigation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/login/LoginScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/results/ResultsScreen.kt`

- [ ] **Step 1: Update LoginScreen — replace DashboardScreen navigation with MainScreen**

In `LoginScreen.kt`, change both navigation targets:

```kotlin
// Line ~33: LaunchedEffect success handler
LaunchedEffect(state.success) {
    state.success?.let { auth ->
        navigator.replace(if (auth.isNewUser) OnboardingScreen() else MainScreen())
    }
}

// Line ~74: Modo Demo button
TextButton(
    onClick = { navigator.replace(MainScreen()) },
    modifier = Modifier.fillMaxWidth()
) {
    Text("Modo Demo", color = Color.Gray, fontWeight = FontWeight.Normal)
}
```

Add import at the top of LoginScreen.kt:
```kotlin
import com.neovita.app.screens.main.MainScreen
```

Remove import (no longer needed):
```kotlin
import com.neovita.app.screens.dashboard.DashboardScreen
```

- [ ] **Step 2: Update ProfileScreen — fix navigator calls to use parent**

In `ProfileScreen.kt`, change both navigation calls:

```kotlin
// Assessment button — use navigator.parent to push above the tab nav
OutlinedButton(
    onClick = { navigator.parent?.push(AssessmentScreen()) },
    modifier = Modifier.fillMaxWidth()
) {
    Text("Realizar nueva evaluación", color = NeoTeal700)
}

// Logout button — use navigator.parent to replace the root
OutlinedButton(
    onClick = { navigator.parent?.replaceAll(LoginScreen()) },
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeoRed)
) {
    Text("Cerrar sesión")
}
```

- [ ] **Step 3: Update ResultsScreen — navigate to MainScreen instead of DashboardScreen**

In `ResultsScreen.kt`, update the "Ver mi Plan" button:

```kotlin
Button(
    onClick = { navigator.replaceAll(MainScreen()) },
    modifier = Modifier.fillMaxWidth().height(52.dp),
    colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
) {
    Text("Ver mi Plan →", color = Color.White, fontWeight = FontWeight.SemiBold)
}
```

Add import, remove old:
```kotlin
// Add:
import com.neovita.app.screens.main.MainScreen
// Remove:
import com.neovita.app.screens.dashboard.DashboardScreen
```

- [ ] **Step 4: Build to verify navigation skeleton compiles**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Install and smoke-test tab switching**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.neovita.app/.MainActivity
```

Tap "Modo Demo" → MainScreen with bottom nav should appear. Tap each tab → verify switching works without crash.

- [ ] **Step 6: Commit navigation skeleton**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add -p
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat(nav): add TabNavigator MainScreen and wire all navigation"
```

---

## Task 4: NeoTopBar Component

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/NeoTopBar.kt`

- [ ] **Step 1: Create NeoTopBar.kt**

```kotlin
package com.neovita.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoBg
import com.neovita.app.ui.theme.NeoNavy
import com.neovita.app.ui.theme.NeoTeal700

@Composable
fun NeoTopBar(
    title: String,
    subtitle: String? = null,
    userName: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NeoBg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = NeoNavy,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
        val initial = userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(NeoTeal700),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

---

## Task 5: MetricCard and TaskCard Components

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/MetricCard.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/TaskCard.kt`

- [ ] **Step 1: Create MetricCard.kt**

```kotlin
package com.neovita.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoNavy
import com.neovita.app.ui.theme.NeoSurface

@Composable
fun MetricCard(label: String, score: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScoreRing(score = score, size = 64.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                "$score",
                style = MaterialTheme.typography.titleMedium,
                color = NeoNavy,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

- [ ] **Step 2: Create TaskCard.kt**

```kotlin
package com.neovita.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neovita.app.ui.theme.NeoTeal700

@Composable
fun TaskCard(
    taskTitle: String,
    description: String,
    progress: Float = 0f,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeoTeal700)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "TAREA DE HOY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                taskTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${(progress * 100).toInt()}% completado",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

---

## Task 6: SettingsListItem and QuoteBanner Components

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/SettingsListItem.kt`
- Create: `composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/QuoteBanner.kt`

- [ ] **Step 1: Create SettingsListItem.kt**

```kotlin
package com.neovita.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoNavy

@Composable
fun SettingsListItem(
    icon: String,
    label: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = NeoNavy,
            modifier = Modifier.weight(1f)
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = Color.Gray)
    }
}
```

- [ ] **Step 2: Create QuoteBanner.kt**

```kotlin
package com.neovita.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoNavy
import com.neovita.app.ui.theme.NeoTeal200

@Composable
fun QuoteBanner(
    quote: String,
    attribution: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeoNavy)
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "\"$quote\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "— $attribution",
                style = MaterialTheme.typography.labelSmall,
                color = NeoTeal200
            )
        }
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit all new components**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/neovita/app/ui/components/
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat(ui): add NeoTopBar, MetricCard, TaskCard, SettingsListItem, QuoteBanner components"
```

---

## Task 7: Redesign DashboardScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Replace DashboardScreen.kt content**

```kotlin
package com.neovita.app.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import com.neovita.app.ui.components.MetricCard
import com.neovita.app.ui.components.NeoTopBar
import com.neovita.app.ui.components.ScoreRing
import com.neovita.app.ui.components.TaskCard
import com.neovita.app.ui.theme.*

class DashboardScreen : Screen {
    @Composable override fun Content() {
        val vm: DashboardViewModel = koinInject()
        val state by vm.state.collectAsState()

        Column(Modifier.fillMaxSize().background(NeoBg)) {
            NeoTopBar(title = "NeoVita", userName = state.user?.name)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column {
                    Text(
                        "Hola, ${state.user?.name ?: "amigo"} 👋",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeoNavy, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tu santuario de longevidad está listo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeoTeal700)
                    }
                } else {
                    val plan = state.plan
                    if (plan != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ScoreRing(
                                score = plan.scores.overall,
                                size = 160.dp,
                                label = "ÍNDICE GENERAL"
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricCard("Ejercicio", plan.scores.exercise, Modifier.weight(1f))
                            MetricCard("Sueño", plan.scores.sleep, Modifier.weight(1f))
                            MetricCard("Nutrición", plan.scores.nutrition, Modifier.weight(1f))
                        }

                        val firstTask = plan.exercise.firstOrNull()
                        if (firstTask != null) {
                            TaskCard(
                                taskTitle = firstTask,
                                description = "Mantén un ritmo constante para optimizar tu salud cardiovascular."
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = NeoSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🌱", style = MaterialTheme.typography.displaySmall)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "¡Bienvenido a NeoVita!",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NeoNavy, fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Completa tu evaluación para obtener tu plan personalizado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Install and verify Dashboard looks correct**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.neovita.app/.MainActivity
```

Tap "Modo Demo" → Home tab should show greeting, "¡Bienvenido a NeoVita!" card, bottom nav.

- [ ] **Step 4: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/neovita/app/screens/dashboard/DashboardScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat(dashboard): redesign to match Stitch mockup"
```

---

## Task 8: Redesign ChatScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/chat/ChatScreen.kt`

- [ ] **Step 1: Replace ChatScreen.kt content**

```kotlin
package com.neovita.app.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.components.NeoTopBar
import com.neovita.app.ui.theme.*
import com.neovita.shared.domain.model.MessageRole

class ChatScreen : Screen {
    @Composable override fun Content() {
        val vm: ChatViewModel = koinInject()
        val state by vm.state.collectAsState()
        val listState = rememberLazyListState()

        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty())
                listState.animateScrollToItem(state.messages.size - 1)
        }

        Column(Modifier.fillMaxSize().background(NeoBg)) {
            NeoTopBar(
                title = "NeoVita",
                subtitle = "Coach NeoVita",
                userName = null
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(state.messages) { msg ->
                    val isUser = msg.role == MessageRole.USER
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) NeoTeal700 else NeoSurface
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.content,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) Color.White else NeoNavy
                            )
                        }
                    }
                }
                if (state.isStreaming) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(start = 8.dp),
                                color = NeoTeal700, strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            if (state.error != null) ErrorBanner(state.error!!)

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf("Nutrición", "Ejercicio", "Sueño", "Estrés")
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { vm.sendMessage("Dime más sobre $suggestion") },
                        label = {
                            Text(suggestion, style = MaterialTheme.typography.bodySmall)
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = NeoTeal200
                        )
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(NeoSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = vm::updateInput,
                    placeholder = { Text("Escribe un mensaje...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { vm.sendMessage(state.inputText) },
                    enabled = state.inputText.isNotBlank() && !state.isStreaming,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeoTeal700)
                ) {
                    Text("➤", color = Color.White)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/neovita/app/screens/chat/ChatScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat(chat): redesign to match Stitch mockup"
```

---

## Task 9: Redesign PlanScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/plan/PlanScreen.kt`

- [ ] **Step 1: Replace PlanScreen.kt content**

```kotlin
package com.neovita.app.screens.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.components.NeoTopBar
import com.neovita.app.ui.theme.*

class PlanScreen : Screen {
    @Composable override fun Content() {
        val vm: PlanViewModel = koinInject()
        val state by vm.state.collectAsState()

        Column(Modifier.fillMaxSize().background(NeoBg)) {
            NeoTopBar(title = "NeoVita")

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bicolor title
                Text(
                    buildAnnotatedString {
                        append("Mi Plan de\n")
                        withStyle(SpanStyle(color = NeoTeal700)) {
                            append("Longevidad")
                        }
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeoNavy,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Optimiza tu salud celular con recomendaciones personalizadas basadas en tu perfil biológico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeoTeal700)
                    }
                } else {
                    if (state.isGenerating) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NeoSurface)
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NeoTeal700, strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Generando tu plan personalizado...", color = NeoNavy)
                            }
                            if (state.streamBuffer.isNotEmpty()) {
                                Text(
                                    state.streamBuffer,
                                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    val plan = state.plan
                    if (plan != null) {
                        PlanSection("🥗 Nutrición", plan.nutrition)
                        PlanSection("😴 Sueño", plan.sleep)
                        PlanSection("🏃 Ejercicio", plan.exercise)
                    } else if (!state.isGenerating) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NeoSurface)
                        ) {
                            Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Aún no tienes un plan", color = NeoNavy)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Genera tu plan personalizado con IA",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    if (state.error != null) {
                        ErrorBanner(state.error!!, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    Button(
                        onClick = vm::generatePlan,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !state.isGenerating,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
                    ) {
                        Text(
                            if (state.plan != null) "Regenerar plan" else "Generar mi plan",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (state.plan != null) {
                        Text(
                            "ÚLTIMA ACTUALIZACIÓN: HOY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSection(title: String, items: List<String>) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeoSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NeoNavy,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", color = NeoTeal700)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    items.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("• ", color = NeoTeal700, fontWeight = FontWeight.Bold)
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeoNavy
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/neovita/app/screens/plan/PlanScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat(plan): redesign to match Stitch mockup"
```

---

## Task 10: Redesign ProfileScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt`

- [ ] **Step 1: Replace ProfileScreen.kt content**

```kotlin
package com.neovita.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.screens.assessment.AssessmentScreen
import com.neovita.app.screens.login.LoginScreen
import com.neovita.app.ui.components.NeoTopBar
import com.neovita.app.ui.components.QuoteBanner
import com.neovita.app.ui.components.SettingsListItem
import com.neovita.app.ui.theme.*
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class ProfileState(val user: UserDto? = null, val isLoading: Boolean = true)

class ProfileViewModel(private val userRepo: UserRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()
    init {
        scope.launch {
            val user = userRepo.getMe().getOrNull()
            _state.update { it.copy(user = user, isLoading = false) }
        }
    }
}

class ProfileScreen : Screen {
    @Composable override fun Content() {
        val vm: ProfileViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Column(Modifier.fillMaxSize().background(NeoBg)) {
            NeoTopBar(title = "NeoVita", userName = state.user?.name)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Avatar
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(NeoTeal200),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = state.user?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Text(
                        initial,
                        style = MaterialTheme.typography.headlineLarge,
                        color = NeoTeal900,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.user?.name ?: "...",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeoNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        state.user?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                // Action buttons
                OutlinedButton(
                    onClick = { navigator.parent?.push(AssessmentScreen()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Realizar nueva evaluación", color = NeoTeal700)
                }
                OutlinedButton(
                    onClick = { navigator.parent?.replaceAll(LoginScreen()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeoRed)
                ) {
                    Text("Cerrar sesión")
                }

                // Stats row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeoSurface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("EDAD", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            val age = state.user?.age
                            Text(
                                if (age != null && age > 0) "$age años" else "—",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeoNavy,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeoTeal700)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("ESTADO", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text(
                                "Vital",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Próximo hito
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NeoTeal900)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "PRÓXIMO HITO",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeoTeal200
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Meta de Longevidad: 95+",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { 0.65f },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeoTeal500,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "65% completado de tu plan actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeoTeal200
                        )
                    }
                }

                // Configuración de Cuenta
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NeoSurface)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Configuración de Cuenta",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        HorizontalDivider()
                        SettingsListItem(icon = "👤", label = "Información Personal")
                        HorizontalDivider()
                        SettingsListItem(icon = "🔔", label = "Notificaciones")
                        HorizontalDivider()
                        SettingsListItem(icon = "🔒", label = "Privacidad y Seguridad")
                    }
                }

                QuoteBanner(
                    quote = "La salud es la verdadera riqueza del mañana.",
                    attribution = "Sabiduría NeoVita"
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Install and do full smoke test**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.neovita.app/.MainActivity
```

Verify:
- Login screen → Modo Demo → Dashboard with bottom nav ✅
- Tab switching: Home, Chat, Plan, Profile — no crash ✅
- Profile screen shows stats, hito card, settings, quote ✅
- "Realizar nueva evaluación" → opens Assessment full-screen (no nav bar) ✅
- Back from Assessment → returns to MainScreen / Profile tab ✅

- [ ] **Step 4: Commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add composeApp/src/commonMain/kotlin/com/neovita/app/screens/profile/ProfileScreen.kt
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat(profile): redesign to match Stitch mockup"
```

---

## Task 11: Final Verification and Cleanup

- [ ] **Step 1: Full build clean**

```bash
./gradlew clean :composeApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no warnings about unresolved references.

- [ ] **Step 2: Install and full flow test**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.neovita.app/.MainActivity
```

Full flow checklist:
- Login → Modo Demo → Dashboard ✅
- Home → Chat → Plan → Profile (all tabs render, no crash) ✅
- Profile → "Realizar nueva evaluación" → AssessmentScreen (full-screen, no nav bar) ✅
- Assessment back button → back to MainScreen / Profile tab ✅
- Profile → "Cerrar sesión" → LoginScreen ✅
- Chat: send a message (will fail if server not running — that's OK, just verify no crash) ✅
- Plan → "Generar mi plan" button visible ✅

- [ ] **Step 3: Final commit**

```bash
PRE_COMMIT_ALLOW_NO_CONFIG=1 git add .
PRE_COMMIT_ALLOW_NO_CONFIG=1 git commit -m "feat: bottom nav + full screen redesign complete"
```
