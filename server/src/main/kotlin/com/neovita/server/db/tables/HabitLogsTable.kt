package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

/**
 * One row per day a person marks a habit done. `habitId` matches the stable ids in the
 * client's Blue Zones habit cards (`"meditation"`, `"plant_based_diet"`, etc.) — not an FK,
 * since the set of habits is defined client-side content, not server data.
 */
object HabitLogsTable : Table("habit_logs") {
    val userId = varchar("user_id", 64)
    val habitId = varchar("habit_id", 64)
    val date = varchar("log_date", 10)   // "YYYY-MM-DD"
    val loggedAt = long("logged_at")
    override val primaryKey = PrimaryKey(userId, habitId, date)
}
