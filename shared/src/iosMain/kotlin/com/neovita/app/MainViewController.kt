package com.neovita.app

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.neovita.shared.config.AppPlatform
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.data.cache.SqlDelightLocalCache
import com.neovita.shared.db.NeoVitaDatabase
import platform.Foundation.NSBundle

fun MainViewController() = ComposeUIViewController {
    val driver = NativeSqliteDriver(NeoVitaDatabase.Schema, "neovita.db")
    val cache = SqlDelightLocalCache(NeoVitaDatabase(driver))
    // CFBundleVersion is the iOS build number (int by convention in this project).
    val buildNumber = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
        ?.toIntOrNull() ?: 0
    App(
        baseUrl = "http://localhost:8080/api",
        cache = cache,
        clientInfo = ClientInfo(AppPlatform.IOS, buildNumber)
    )
}
