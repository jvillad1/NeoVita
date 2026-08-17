package com.neovita.app.navigation.url

import cafe.adriel.voyager.navigator.tab.Tab
import com.neovita.app.navigation.tabs.B2BTab
import com.neovita.app.navigation.tabs.ChatTab
import com.neovita.app.navigation.tabs.HomeTab
import com.neovita.app.navigation.tabs.PlanTab
import com.neovita.app.navigation.tabs.ProfileTab

/**
 * Pestaña que corresponde a una ruta. Una ruta desconocida, o una que no es de pestaña
 * (login, evaluación…), cae en Inicio: es preferible a una pantalla en blanco.
 */
fun tabFor(route: AppRoute?): Tab = when (route) {
    AppRoute.CHAT -> ChatTab
    AppRoute.PLAN -> PlanTab
    AppRoute.B2B -> B2BTab
    AppRoute.PROFILE -> ProfileTab
    else -> HomeTab
}
