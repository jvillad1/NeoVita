package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object AssessmentsTable : Table("assessments") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val createdAt = long("created_at")          // epochMilliseconds
    val exerciseFrequency = varchar("exercise_frequency", 50)
    val exerciseType = varchar("exercise_type", 100)
    // Guarda la etiqueta que muestra la app, no un código: la más larga hoy es
    // "Menos de 5 horas" (16). Con varchar(10) esa opción reventaba el insert con 500.
    val sleepHours = varchar("sleep_hours", 64)
    val sleepQuality = integer("sleep_quality")   // 1-10
    val mainGoal = varchar("main_goal", 255)
    override val primaryKey = PrimaryKey(id)
}
