package com.neovita.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.health.connect.client.PermissionController
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.neovita.app.android.BuildConfig
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.app.health.HealthPermissionLauncher
import com.neovita.app.push.PushTargetHolder
import com.neovita.shared.config.AppPlatform
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase

class MainActivity : ComponentActivity() {
    private val healthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            // Un otorgamiento parcial (p.ej. 1 de 3 permisos) también llega aquí como true;
            // sync() vuelve a validar con containsAll antes de leer nada, así que es seguro.
            HealthPermissionLauncher.deliver(granted.isNotEmpty())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CurrentActivityHolder.activity = this
        HealthPermissionLauncher.owner = this
        HealthPermissionLauncher.request = { permissions, callback ->
            HealthPermissionLauncher.setPending(callback)
            healthPermissions.launch(permissions)
        }
        handlePushTarget(intent)
        val driver = AndroidSqliteDriver(NeoVitaDatabase.Schema, this, "neovita.db")
        val cache = SqlDelightLocalCache(NeoVitaDatabase(driver))
        setContent {
            App(
                baseUrl = BuildConfig.SERVER_URL + "/api",
                cache = cache,
                clientInfo = ClientInfo(AppPlatform.ANDROID, BuildConfig.VERSION_CODE)
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushTarget(intent)
    }

    private fun handlePushTarget(intent: Intent?) {
        intent?.getStringExtra("push_target")?.let { PushTargetHolder.target.value = it }
    }

    override fun onDestroy() {
        if (CurrentActivityHolder.activity === this) CurrentActivityHolder.activity = null
        if (HealthPermissionLauncher.owner === this) {
            HealthPermissionLauncher.owner = null
            HealthPermissionLauncher.request = null
        }
        super.onDestroy()
    }
}
