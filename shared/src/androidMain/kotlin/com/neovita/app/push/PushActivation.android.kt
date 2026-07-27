package com.neovita.app.push

import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

        // El permiso solo se pide cuando push realmente se activa — en modo dormido la app
        // no debe mostrar ningún diálogo (y Android 13+ solo permite 2 negaciones).
        val activity = CurrentActivityHolder.activity
        if (activity != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }

        // Solo se marca activado si todo lo anterior tuvo éxito: un typo en las
        // variables de Firebase del servidor deja el flag sin marcar, así que el
        // próximo cambio real de config (LaunchedEffect(config) al corregir el env)
        // reintenta la activación limpiamente.
        activated = true
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
