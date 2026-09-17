package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

// Minimal usage log: just enough to compute retention (distinct users per day/week),
// activation (assessment_completed), and engagement (chat_message_sent) with SQL against
// this table — see B2B panel's own manual-SQL precedent. No metadata column: add one only
// when a concrete query needs it, per the "instrumentación mínima" ask.
object EventsTable : Table("events") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 64)
    val type = varchar("type", 64)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
