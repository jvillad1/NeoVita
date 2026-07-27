package com.neovita.server.db

import com.neovita.server.db.repositories.ContentRepository
import com.neovita.server.db.repositories.ScreenRepository
import com.neovita.server.db.tables.AssessmentsTable
import com.neovita.server.db.tables.ContentTable
import com.neovita.server.db.tables.DeviceTokensTable
import com.neovita.server.db.tables.PlansTable
import com.neovita.server.db.tables.ScreensTable
import com.neovita.server.db.tables.UsersTable
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(app: Application) {
        val url = app.environment.config.property("database.url").getString()
        val driver = app.environment.config.property("database.driver").getString()
        Database.connect(url, driver)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, AssessmentsTable, PlansTable, ContentTable, ScreensTable, DeviceTokensTable)
        }
        // Seed dashboard content on first boot so the feed is never empty.
        ContentRepository().seedIfEmpty(SEED_CONTENT)
        // Seed the SDUI screen definitions (currently just "dashboard") on first boot.
        ScreenRepository().seedIfEmpty(SEED_SCREENS)
    }
}
