package com.neovita.server.db.repositories

import com.neovita.server.db.tables.ScreensTable
import com.neovita.shared.network.dto.ScreenDefinitionDto
import com.neovita.shared.network.dto.SectionDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ScreenRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the active screen definition for [slug], or null if it doesn't exist or is inactive. */
    fun getActive(slug: String): ScreenDefinitionDto? = transaction {
        ScreensTable.selectAll()
            .where { (ScreensTable.slug eq slug) and (ScreensTable.active eq true) }
            .singleOrNull()
            ?.toDto()
    }

    /** Seeds the bundled screen definitions on first boot so the app always has content to render. */
    fun seedIfEmpty(defs: List<ScreenDefinitionDto>) = transaction {
        if (ScreensTable.selectAll().limit(1).empty()) {
            val now = System.currentTimeMillis()
            defs.forEach { def ->
                ScreensTable.insert {
                    it[slug] = def.slug
                    it[version] = def.version
                    it[sectionsJson] = json.encodeToString<List<SectionDto>>(def.sections)
                    it[active] = true
                    it[updatedAt] = now
                }
            }
        }
    }

    private fun ResultRow.toDto(): ScreenDefinitionDto = ScreenDefinitionDto(
        slug = this[ScreensTable.slug],
        version = this[ScreensTable.version],
        sections = json.decodeFromString<List<SectionDto>>(this[ScreensTable.sectionsJson]),
    )
}
