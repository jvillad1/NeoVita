package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = varchar("id", 36)           // UUID
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val age = integer("age").default(0)
    val role = varchar("role", 20).default("USER")  // USER | EMPLOYER
    val companyId = varchar("company_id", 36).nullable()
    override val primaryKey = PrimaryKey(id)
}
