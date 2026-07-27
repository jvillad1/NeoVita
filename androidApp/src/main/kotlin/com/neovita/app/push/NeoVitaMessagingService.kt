package com.neovita.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.neovita.app.MainActivity
import com.neovita.app.android.R

// Receives data-only FCM messages {title, body, target?} and builds the notification
// locally, so the tap contract stays stable across server changes (install-once spec).
class NeoVitaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTokenUploader.upload(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: return
        val body = message.data["body"] ?: ""
        val target = message.data["target"]

        val channelId = "neovita_general"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Recordatorios NeoVita", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            target?.let { putExtra("push_target", it) }
        }
        val pending = PendingIntent.getActivity(
            this, (target ?: "").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify((target ?: "").hashCode(), notification)
        } // sin permiso POST_NOTIFICATIONS notify lanza SecurityException en 33+: ignorar
    }
}
