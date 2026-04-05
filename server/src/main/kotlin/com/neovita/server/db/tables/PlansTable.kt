package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object PlansTable : Table("plans") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val generatedAt = long("generated_at")
    val nutritionJson = text("nutrition_json")
    val sleepJson = text("sleep_json")
    val exerciseJson = text("exercise_json")
    val scoresJson = text("scores_json")
    override val primaryKey = PrimaryKey(id)
}
