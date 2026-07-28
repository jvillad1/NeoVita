package com.neovita.server.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import org.slf4j.LoggerFactory

// Sends data-only FCM messages: the app's FirebaseMessagingService always builds the
// notification itself, so the tap contract {title, body, target} stays binary-stable.
// Without FIREBASE_SERVICE_ACCOUNT the service is disabled (routes answer PUSH_DISABLED).
class PushService(private val serviceAccountJson: String?) {

    private val log = LoggerFactory.getLogger(PushService::class.java)
    val enabled: Boolean = !serviceAccountJson.isNullOrBlank()

    private val messaging: FirebaseMessaging? by lazy {
        if (!enabled) null
        else runCatching {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountJson!!.byteInputStream()))
                .build()
            val app = FirebaseApp.getApps().firstOrNull { it.name == "neovita-push" }
                ?: FirebaseApp.initializeApp(options, "neovita-push")
            FirebaseMessaging.getInstance(app)
        }.onFailure { log.warn("Push deshabilitado: credenciales inválidas", it) }.getOrNull()
    }

    /** Sends to each token individually; returns how many were accepted by FCM. */
    fun send(tokens: List<String>, title: String, body: String, target: String?): Int {
        val fm = messaging ?: return 0
        var sent = 0
        tokens.forEach { token ->
            runCatching {
                val builder = Message.builder()
                    .setToken(token)
                    .putData("title", title)
                    .putData("body", body)
                target?.let { builder.putData("target", it) }
                fm.send(builder.build())
                sent++
            }.onFailure { log.warn("Push falló para un token: ${it.message}") }
        }
        return sent
    }
}
