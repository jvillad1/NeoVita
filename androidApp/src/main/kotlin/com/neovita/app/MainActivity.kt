package com.neovita.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val driver = AndroidSqliteDriver(NeoVitaDatabase.Schema, this, "neovita.db")
        val cache = SqlDelightLocalCache(NeoVitaDatabase(driver))
        setContent {
            App(baseUrl = "http://10.0.2.2:8080/api", cache = cache)
        }
    }
}
