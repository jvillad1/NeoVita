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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

class MainScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(HomeTab) {
            Scaffold(
                containerColor = NeoDarkBg,
                bottomBar = {
                    NeoBottomBar()
                }
            ) { paddingValues ->
                Box(Modifier.padding(paddingValues)) {
                    val tabNavigator = LocalTabNavigator.current
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
    val navItems = listOf(
        NavItem(HomeTab,    "Inicio",  Icons.Filled.Home),
        NavItem(ChatTab,    "Coach",   Icons.Filled.MailOutline),
        NavItem(PlanTab,    "Plan",    Icons.Filled.DateRange),
        NavItem(ProfileTab, "Perfil",  Icons.Filled.Person),
    )

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
