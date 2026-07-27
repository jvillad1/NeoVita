package com.neovita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.neovita.app.android.BuildConfig
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.shared.config.AppPlatform
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CurrentActivityHolder.activity = this
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

    override fun onDestroy() {
        if (CurrentActivityHolder.activity === this) CurrentActivityHolder.activity = null
        super.onDestroy()
    }
}
