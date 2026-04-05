package com.neovita.app

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.neovita.shared.db.NeoVitaDatabase

fun MainViewController() = ComposeUIViewController {
    val driver = NativeSqliteDriver(NeoVitaDatabase.Schema, "neovita.db")
    val database = NeoVitaDatabase(driver)
    App(baseUrl = "http://localhost:8080", database = database)
}
