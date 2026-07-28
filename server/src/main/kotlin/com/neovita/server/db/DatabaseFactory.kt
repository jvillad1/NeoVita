package com.neovita.server.db

import com.neovita.server.db.repositories.ContentRepository
import com.neovita.server.db.repositories.ScreenRepository
import com.neovita.server.db.tables.AssessmentsTable
import com.neovita.server.db.tables.ContentTable
import com.neovita.server.db.tables.DeviceTokensTable
import com.neovita.server.db.tables.HealthMetricsTable
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
        val database = Database.connect(url, driver)
        // Bound to the explicit `database` (not Exposed's ambient default) and kept as a
        // single transaction: under the Ktor test host, this init() can run concurrently
        // with another test's module() on a different thread, and a bare `transaction {}`
        // resolves against a *global*, mutable "default database" var — so an unpinned call
        // here can silently operate against a sibling test's (still schema-less) H2 instance.
        // Nested `transaction {}` calls inside seedIfEmpty() reuse this thread's already-open
        // transaction (and therefore this exact `database`), sidestepping that race.
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, AssessmentsTable, PlansTable, ContentTable, ScreensTable, DeviceTokensTable, HealthMetricsTable)
            // Seed dashboard content on first boot so the feed is never empty.
            ContentRepository().seedIfEmpty(SEED_CONTENT)
            // Seed the SDUI screen definitions (currently just "dashboard") on first boot.
            ScreenRepository().seedIfEmpty(SEED_SCREENS)
        }
    }
}
