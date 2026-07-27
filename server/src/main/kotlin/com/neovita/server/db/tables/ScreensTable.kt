package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object ScreensTable : Table("screen_definitions") {
    val slug = varchar("slug", 64)
    val version = integer("version")
    val sectionsJson = text("sections_json")
    val active = bool("active").default(true)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(slug)
}
