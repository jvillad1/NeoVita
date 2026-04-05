package com.neovita.server.plugins

import com.neovita.server.db.DatabaseFactory
import io.ktor.server.application.*

fun Application.configureDatabase() {
    DatabaseFactory.init(this)
}
