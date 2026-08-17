package com.neovita.app.navigation.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppRouteTest {

    @Test fun `each route round-trips through its own path`() {
        AppRoute.entries.forEach { r ->
            assertEquals(r, AppRoute.fromPath(r.path), "${r.path} no vuelve a ${r.name}")
        }
    }

    @Test fun `the root is not a route, so the caller picks the default`() {
        assertNull(AppRoute.fromPath("/"))
        assertNull(AppRoute.fromPath(""))
        assertNull(AppRoute.fromPath(null))
    }

    @Test fun `an unknown path does not resolve`() {
        // Un enlace viejo compartido por correo no debe abrir una pantalla al azar.
        assertNull(AppRoute.fromPath("/panel-antiguo"))
        assertNull(AppRoute.fromPath("/dashboard/extra"))
    }

    @Test fun `query and fragment are ignored`() {
        // Los enlaces reales llegan con seguimiento pegado.
        assertEquals(AppRoute.CHAT, AppRoute.fromPath("/chat?utm_source=correo"))
        assertEquals(AppRoute.CHAT, AppRoute.fromPath("/chat#seccion"))
    }

    @Test fun `a trailing slash or odd casing still resolves`() {
        assertEquals(AppRoute.PROFILE, AppRoute.fromPath("/profile/"))
        assertEquals(AppRoute.PROFILE, AppRoute.fromPath("/Profile"))
    }
}
