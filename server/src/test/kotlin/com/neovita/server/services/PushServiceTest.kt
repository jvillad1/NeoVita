package com.neovita.server.services

import com.google.firebase.messaging.MessagingErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Un token de FCM muere solo: el usuario desinstala, borra los datos de la app o restaura el
 * teléfono. FCM contesta UNREGISTERED y ese token no volverá a servir jamás. Antes el fallo
 * se tragaba en un `log.warn`, así que la tabla sólo crecía y cada envío gastaba una llamada
 * por cada muerto, para siempre. `send` ahora los devuelve para que la ruta los dé de baja.
 */
class PushServiceTest {

    /** Sender falso: decide por token qué contesta FCM, sin red ni credenciales. */
    private fun serviceWith(vararg outcomes: Pair<String, MessagingErrorCode?>): Pair<PushService, MutableList<String>> {
        val byToken = outcomes.toMap()
        val attempted = mutableListOf<String>()
        val service = PushService(
            serviceAccountJson = null,
            sender = FcmSender { token, _, _, _ ->
                attempted += token
                byToken[token]
            }
        )
        return service to attempted
    }

    @Test
    fun `dead tokens come back so the caller can drop them`() {
        val (service, attempted) = serviceWith(
            "vivo" to null,
            "desinstalado" to MessagingErrorCode.UNREGISTERED,
            "basura" to MessagingErrorCode.INVALID_ARGUMENT
        )

        val result = service.send(listOf("vivo", "desinstalado", "basura"), "t", "b", null)

        assertEquals(1, result.sent)
        assertEquals(listOf("desinstalado", "basura"), result.dead)
        assertEquals(listOf("vivo", "desinstalado", "basura"), attempted, "se intentó con todos")
    }

    @Test
    fun `a temporary failure does not cost the user their token`() {
        // UNAVAILABLE/INTERNAL/QUOTA_EXCEEDED son fallos del momento: dar de baja el token
        // por uno de estos dejaría al usuario sin notificaciones por una caída de FCM.
        val (service, _) = serviceWith(
            "a" to MessagingErrorCode.UNAVAILABLE,
            "b" to MessagingErrorCode.INTERNAL,
            "c" to MessagingErrorCode.QUOTA_EXCEEDED,
            // El proyecto de Firebase mal configurado tampoco es culpa del dispositivo:
            // podarlos vaciaría la tabla entera por un error nuestro.
            "d" to MessagingErrorCode.SENDER_ID_MISMATCH
        )

        val result = service.send(listOf("a", "b", "c", "d"), "t", "b", null)

        assertEquals(0, result.sent)
        assertTrue(result.dead.isEmpty(), "no se dan de baja tokens por un fallo pasajero: ${result.dead}")
    }
}
