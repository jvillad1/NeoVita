package com.neovita.app

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase

fun MainViewController() = ComposeUIViewController {
    val driver = NativeSqliteDriver(NeoVitaDatabase.Schema, "neovita.db")
    val cache = SqlDelightLocalCache(NeoVitaDatabase(driver))
    App(baseUrl = "http://localhost:8080/api", cache = cache)
}
