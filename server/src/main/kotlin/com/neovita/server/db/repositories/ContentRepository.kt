package com.neovita.server.db.repositories

import com.neovita.server.db.tables.ContentTable
import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ContentRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ContentRepository {

    fun listActive(): List<ContentItemDto> = transaction {
        ContentTable.selectAll()
            .where { ContentTable.active eq true }
            .orderBy(ContentTable.sortOrder to SortOrder.ASC)
            .map { it.toDto() }
    }

    fun listAll(): List<ContentItemDto> = transaction {
        ContentTable.selectAll()
            .orderBy(ContentTable.sortOrder to SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(req: ContentRequest): ContentItemDto = transaction {
        val newId = UUID.randomUUID().toString()
        ContentTable.insert {
            it[id] = newId
            it[title] = req.title
            it[category] = req.category
            it[type] = req.type
            it[teaser] = req.teaser
            it[readMinutes] = req.readMinutes
            it[sortOrder] = req.sortOrder
            it[active] = req.active
            it[createdAt] = System.currentTimeMillis()
        }
        ContentTable.selectAll().where { ContentTable.id eq newId }.single().toDto()
    }

    fun update(itemId: String, req: ContentRequest): ContentItemDto? = transaction {
        val updated = ContentTable.update({ ContentTable.id eq itemId }) {
            it[title] = req.title
            it[category] = req.category
            it[type] = req.type
            it[teaser] = req.teaser
            it[readMinutes] = req.readMinutes
            it[sortOrder] = req.sortOrder
            it[active] = req.active
        }
        if (updated == 0) null
        else ContentTable.selectAll().where { ContentTable.id eq itemId }.single().toDto()
    }

    fun delete(itemId: String): Boolean = transaction {
        ContentTable.deleteWhere { ContentTable.id eq itemId } > 0
    }

    /** Seeds the bundled content on first boot so the dashboard is never empty. */
    fun seedIfEmpty(items: List<ContentItemDto>) = transaction {
        if (ContentTable.selectAll().limit(1).empty()) {
            val now = System.currentTimeMillis()
            items.forEachIndexed { index, item ->
                ContentTable.insert {
                    it[id] = item.id
                    it[title] = item.title
                    it[category] = item.category
                    it[type] = item.type
                    it[teaser] = item.teaser
                    it[readMinutes] = item.readMinutes
                    it[sortOrder] = if (item.sortOrder != 0) item.sortOrder else index
                    it[active] = item.active
                    it[createdAt] = now
                }
            }
        }
    }

    private fun ResultRow.toDto() = ContentItemDto(
        id = this[ContentTable.id],
        title = this[ContentTable.title],
        category = this[ContentTable.category],
        type = this[ContentTable.type],
        teaser = this[ContentTable.teaser],
        readMinutes = this[ContentTable.readMinutes],
        sortOrder = this[ContentTable.sortOrder],
        active = this[ContentTable.active],
    )
}
