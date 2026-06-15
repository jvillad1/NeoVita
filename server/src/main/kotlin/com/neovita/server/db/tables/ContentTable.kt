package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object ContentTable : Table("content_items") {
    val id = varchar("id", 36)
    val title = text("title")
    val category = varchar("category", 32)   // NUTRITION | EXERCISE | SLEEP | MENTAL_HEALTH | GENERAL
    val type = varchar("type", 16)           // ARTICLE | TIP | VIDEO
    val teaser = text("teaser")
    val readMinutes = integer("read_minutes")
    val sortOrder = integer("sort_order").default(0)
    val active = bool("active").default(true)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
