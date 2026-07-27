package com.neovita.app.push

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.shared.config.isFeatureEnabled
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Volatile
private var activated = false

actual fun activatePush(config: WebConfigResponse?, apiService: ApiService) {
    if (activated) return
    val firebase = config?.firebase ?: return
    if (!config.isFeatureEnabled("push", default = false)) return
    val context = CurrentActivityHolder.activity?.applicationContext ?: return
    activated = true
    runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(firebase.apiKey)
                    .setApplicationId(firebase.appId)
                    .setProjectId(firebase.projectId)
                    .setGcmSenderId(firebase.senderId)
                    .build()
            )
        }
        PushTokenUploader.apiService = apiService
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { PushTokenUploader.upload(it) }
            .addOnFailureListener { Log.w("NeoVitaPush", "No se pudo obtener el token FCM", it) }
    }.onFailure {
        Log.w("NeoVitaPush", "Activación de push falló (config inválida?)", it)
    }
}

// Also called by NeoVitaMessagingService.onNewToken (token rotation).
object PushTokenUploader {
    @Volatile
    var apiService: ApiService? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun upload(token: String) {
        val api = apiService ?: return
        scope.launch { api.registerDeviceToken(token, "android") }
    }
}
