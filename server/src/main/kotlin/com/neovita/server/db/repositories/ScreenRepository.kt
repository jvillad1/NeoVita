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
import org.jetbrains.exposed.sql.update

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

    /** Guarda [sections] en [slug] subiendo la versión: los clientes usan version como ETag,
     *  así que el bump es lo que invalida su caché y hace visible el cambio. */
    fun save(slug: String, sections: List<SectionDto>): ScreenDefinitionDto = transaction {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString<List<SectionDto>>(sections)
        val current = ScreensTable.selectAll()
            .where { ScreensTable.slug eq slug }
            .singleOrNull()
        val nextVersion = (current?.get(ScreensTable.version) ?: 0) + 1
        if (current == null) {
            ScreensTable.insert {
                it[ScreensTable.slug] = slug
                it[version] = nextVersion
                it[sectionsJson] = encoded
                it[active] = true
                it[updatedAt] = now
            }
        } else {
            ScreensTable.update({ ScreensTable.slug eq slug }) {
                it[version] = nextVersion
                it[sectionsJson] = encoded
                it[updatedAt] = now
            }
        }
        ScreenDefinitionDto(slug = slug, version = nextVersion, sections = sections)
    }

    /** Todas las pantallas (para el editor). */
    fun listAll(): List<ScreenDefinitionDto> = transaction {
        ScreensTable.selectAll().map { it.toDto() }
    }

    private fun ResultRow.toDto(): ScreenDefinitionDto = ScreenDefinitionDto(
        slug = this[ScreensTable.slug],
        version = this[ScreensTable.version],
        sections = json.decodeFromString<List<SectionDto>>(this[ScreensTable.sectionsJson]),
    )
}
