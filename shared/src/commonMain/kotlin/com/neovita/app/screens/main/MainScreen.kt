package com.neovita.app.screens.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.*
import com.neovita.app.navigation.tabs.*
import com.neovita.app.ui.theme.*
import com.neovita.shared.config.RemoteConfigRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.app.navigation.url.AppRoute
import com.neovita.app.navigation.url.BrowserUrl
import com.neovita.app.navigation.url.tabFor
import com.neovita.shared.config.isFeatureEnabled
import org.koin.compose.koinInject

class MainScreen : Screen {
    @Composable
    override fun Content() {
        // La pestaña inicial sale de la URL: recargar en /chat debe dejarte en el chat,
        // no devolverte al inicio.
        val inicial = remember { tabFor(AppRoute.fromPath(BrowserUrl.currentPath())) }
        TabNavigator(inicial) {
            Scaffold(
                containerColor = NeoDarkBg,
                bottomBar = {
                    NeoBottomBar()
                }
            ) { paddingValues ->
                Box(Modifier.padding(paddingValues)) {
                    val tabNavigator = LocalTabNavigator.current
                    SyncUrlWithTab(tabNavigator)
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f))
                        },
                        label = "tabContent"
                    ) { tab ->
                        tab.Content()
                    }
                }
            }
        }
    }
}

private data class NavItem(
    val tab: Tab,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun NeoBottomBar() {
    val tabNavigator = LocalTabNavigator.current
    // "chat" is a shipped feature: default true (visible unless the server disables it).
    val config by koinInject<RemoteConfigRepository>().config.collectAsState()

    // El rol vive en el servidor, no en el JWT del cliente: se pregunta una vez al entrar.
    // Ocultar la pestaña es comodidad, no seguridad — el endpoint exige EMPLOYER igual.
    val userRepo = koinInject<UserRepository>()
    var esEmpleador by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        esEmpleador = userRepo.getMe().getOrNull()?.role == "EMPLOYER"
    }

    val navItems = buildList {
        add(NavItem(HomeTab, "Inicio", Icons.Filled.Home))
        if (config.isFeatureEnabled("chat", default = true)) {
            add(NavItem(ChatTab, "Coach", Icons.Filled.MailOutline))
        }
        add(NavItem(PlanTab, "Plan", Icons.Filled.DateRange))
        if (esEmpleador) {
            add(NavItem(B2BTab, "Empresa", Icons.Filled.Star))
        }
        add(NavItem(ProfileTab, "Perfil", Icons.Filled.Person))
    }

    Surface(
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                NeoTabItem(item, tabNavigator)
            }
        }
    }
}

@Composable
private fun RowScope.NeoTabItem(item: NavItem, tabNavigator: TabNavigator) {
    val selected = tabNavigator.current == item.tab

    val iconTint by animateColorAsState(
        targetValue = if (selected) NeoCrimson else Color(0xFFBBBBBB),
        animationSpec = tween(200),
        label = "iconTint"
    )

    Surface(
        onClick = { tabNavigator.current = item.tab },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Selection pill background
                if (selected) {
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 32.dp)
                            .background(NeoCrimson.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    )
                }
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                item.label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = iconTint
            )
        }
    }
}

/**
 * Mantiene la barra de direcciones y la pestaña activa diciendo lo mismo, en los dos
 * sentidos: al cambiar de pestaña se escribe la URL, y Atrás/Adelante del navegador
 * cambian de pestaña en vez de sacarte de la app.
 *
 * En Android e iOS `BrowserUrl` no hace nada, así que este bloque es inofensivo allí.
 */
@Composable
private fun SyncUrlWithTab(tabNavigator: cafe.adriel.voyager.navigator.tab.TabNavigator) {
    LaunchedEffect(tabNavigator.current) {
        routeFor(tabNavigator.current)?.let { BrowserUrl.push(it) }
    }
    LaunchedEffect(Unit) {
        BrowserUrl.onBackForward { ruta ->
            tabNavigator.current = tabFor(ruta)
        }
    }
}

private fun routeFor(tab: cafe.adriel.voyager.navigator.tab.Tab): AppRoute? = when (tab) {
    HomeTab -> AppRoute.DASHBOARD
    ChatTab -> AppRoute.CHAT
    PlanTab -> AppRoute.PLAN
    B2BTab -> AppRoute.B2B
    ProfileTab -> AppRoute.PROFILE
    else -> null
}
