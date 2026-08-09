package com.neovita.server.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Message
import org.slf4j.LoggerFactory

/**
 * Envía un push a un token. Devuelve null si FCM lo aceptó, o el código de error si no.
 *
 * Existe como interfaz para que los tests puedan decidir qué contesta FCM sin red ni
 * credenciales: la política de qué error mata un token es lo que hay que poder verificar.
 */
fun interface FcmSender {
    fun send(token: String, title: String, body: String, target: String?): MessagingErrorCode?
}

/** [sent] aceptados por FCM; [dead] tokens que ya no existen y hay que dar de baja. */
data class PushResult(val sent: Int, val dead: List<String>)

// Sends data-only FCM messages: the app's FirebaseMessagingService always builds the
// notification itself, so the tap contract {title, body, target} stays binary-stable.
// Without FIREBASE_SERVICE_ACCOUNT the service is disabled (routes answer PUSH_DISABLED).
class PushService(
    private val serviceAccountJson: String?,
    private val sender: FcmSender? = null,
) {

    private val log = LoggerFactory.getLogger(PushService::class.java)
    val enabled: Boolean = !serviceAccountJson.isNullOrBlank() || sender != null

    private val messaging: FirebaseMessaging? by lazy {
        val json = serviceAccountJson
        if (json.isNullOrBlank()) null
        else runCatching {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(json.byteInputStream()))
                .build()
            val app = FirebaseApp.getApps().firstOrNull { it.name == "neovita-push" }
                ?: FirebaseApp.initializeApp(options, "neovita-push")
            FirebaseMessaging.getInstance(app)
        }.onFailure { log.warn("Push deshabilitado: credenciales inválidas", it) }.getOrNull()
    }

    private val fcm: FcmSender? by lazy { sender ?: messaging?.let(::realSender) }

    /**
     * Envía a cada token por separado. Los tokens muertos vuelven en [PushResult.dead] en vez
     * de perderse en un log: quien llama los da de baja, que es lo único que impide que la
     * tabla crezca sin fin y que cada envío gaste una llamada por cada desinstalación pasada.
     */
    fun send(tokens: List<String>, title: String, body: String, target: String?): PushResult {
        val send = fcm ?: return PushResult(0, emptyList())
        var sent = 0
        val dead = mutableListOf<String>()
        tokens.forEach { token ->
            val error = send.send(token, title, body, target)
            when {
                error == null -> sent++
                error in TOKEN_IS_GONE -> {
                    dead += token
                    log.info("Token dado de baja: FCM respondió $error")
                }
                else -> log.warn("Push falló para un token (reintentable): $error")
            }
        }
        return PushResult(sent, dead)
    }

    private fun realSender(fm: FirebaseMessaging) = FcmSender { token, title, body, target ->
        val builder = Message.builder()
            .setToken(token)
            .putData("title", title)
            .putData("body", body)
        target?.let { builder.putData("target", it) }
        try {
            fm.send(builder.build())
            null
        } catch (e: FirebaseMessagingException) {
            e.messagingErrorCode ?: MessagingErrorCode.INTERNAL
        } catch (e: Exception) {
            log.warn("Push falló para un token: ${e.message}")
            MessagingErrorCode.INTERNAL
        }
    }

    private companion object {
        /**
         * Los dos únicos códigos que significan "este token no va a servir nunca más":
         * la app se desinstaló/borró sus datos, o el token está malformado.
         *
         * SENDER_ID_MISMATCH queda fuera a propósito: significa que el token pertenece a otro
         * proyecto de Firebase, o sea un error de configuración nuestro. Podar por él vaciaría
         * la tabla entera de golpe el día que alguien cambie mal una credencial.
         */
        val TOKEN_IS_GONE = setOf(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT)
    }
}
