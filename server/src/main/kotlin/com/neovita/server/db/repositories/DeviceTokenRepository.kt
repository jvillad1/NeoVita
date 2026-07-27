package com.neovita.server.db.repositories

import com.neovita.server.db.tables.DeviceTokensTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class DeviceTokenRepository {

    fun upsert(token: String, userId: String, platform: String) = transaction {
        val now = System.currentTimeMillis()
        val updated = DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
            it[DeviceTokensTable.userId] = userId
            it[DeviceTokensTable.platform] = platform
            it[updatedAt] = now
        }
        if (updated == 0) {
            DeviceTokensTable.insert {
                it[DeviceTokensTable.token] = token
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.platform] = platform
                it[updatedAt] = now
            }
        }
    }

    fun tokensForUser(userId: String): List<String> = transaction {
        DeviceTokensTable.selectAll().where { DeviceTokensTable.userId eq userId }
            .map { it[DeviceTokensTable.token] }
    }

    fun allTokens(): List<String> = transaction {
        DeviceTokensTable.selectAll().map { it[DeviceTokensTable.token] }
    }
}
