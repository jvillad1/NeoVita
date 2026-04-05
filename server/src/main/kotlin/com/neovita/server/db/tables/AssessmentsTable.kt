package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object AssessmentsTable : Table("assessments") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val createdAt = long("created_at")          // epochMilliseconds
    val exerciseFrequency = varchar("exercise_frequency", 50)
    val exerciseType = varchar("exercise_type", 100)
    val sleepHours = varchar("sleep_hours", 10)  // "4-6"|"6-8"|"8+"
    val sleepQuality = integer("sleep_quality")   // 1-10
    val mainGoal = varchar("main_goal", 255)
    override val primaryKey = PrimaryKey(id)
}
