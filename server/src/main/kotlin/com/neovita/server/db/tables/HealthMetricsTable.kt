package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object HealthMetricsTable : Table("health_metrics") {
    val userId = varchar("user_id", 64)
    val date = varchar("metric_date", 10)          // "YYYY-MM-DD"
    val steps = integer("steps").nullable()
    val sleepMinutes = integer("sleep_minutes").nullable()
    val avgHeartRate = integer("avg_heart_rate").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, date)
}
