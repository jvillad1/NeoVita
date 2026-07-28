package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

object DeviceTokensTable : Table("device_tokens") {
    val token = varchar("token", 512)
    val userId = varchar("user_id", 64)
    val platform = varchar("platform", 16)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(token)
}
