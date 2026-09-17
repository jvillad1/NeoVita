package com.neovita.server.db.repositories

import com.neovita.server.db.tables.EventsTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class EventRepository {
    fun log(userId: String, type: String) = transaction {
        EventsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[EventsTable.userId] = userId
            it[EventsTable.type] = type
            it[createdAt] = System.currentTimeMillis()
        }
    }
}
