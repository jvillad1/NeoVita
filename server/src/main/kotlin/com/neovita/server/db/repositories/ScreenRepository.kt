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
import org.jetbrains.exposed.sql.insertIgnore
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

    /**
     * Guarda [sections] en [slug] subiendo la versión: los clientes usan version como ETag,
     * así que el bump es lo que invalida su caché y hace visible el cambio.
     *
     * Con [expectedVersion] no nulo la escritura es condicional (bloqueo optimista): si otra
     * persona guardó mientras editábamos, devuelve null en vez de pisar su trabajo — sin esto
     * dos guardados simultáneos descartan uno en silencio y ambos reciben 200.
     *
     * Guardar además republica: `getActive` filtra `active`, así que sin esto una pantalla
     * desactivada aceptaría ediciones con 200 que ningún cliente llegaría a ver.
     */
    fun save(slug: String, sections: List<SectionDto>, expectedVersion: Int? = null): ScreenDefinitionDto? = transaction {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString<List<SectionDto>>(sections)
        val current = ScreensTable.selectAll()
            .where { ScreensTable.slug eq slug }
            .singleOrNull()

        if (current == null) {
            if (expectedVersion != null) return@transaction null   // esperaba una fila que no existe
            // Entre el SELECT de arriba y este INSERT, otra petición puede haber creado el
            // mismo slug. Con un insert normal eso era una violación de clave -> 500. Se usa
            // insertIgnore y no un try/catch porque en Postgres una violación aborta la
            // transacción entera: capturar el error dejaría la transacción inservible.
            // Cero filas insertadas = alguien se adelantó, que es exactamente el 409 que la
            // ruta ya devuelve para null ("otra persona guardó mientras editabas").
            val inserted = ScreensTable.insertIgnore {
                it[ScreensTable.slug] = slug
                it[version] = 1
                it[sectionsJson] = encoded
                it[active] = true
                it[updatedAt] = now
            }
            if (inserted.insertedCount == 0) return@transaction null
            return@transaction ScreenDefinitionDto(slug = slug, version = 1, sections = sections)
        }

        val storedVersion = current[ScreensTable.version]
        if (expectedVersion != null && expectedVersion != storedVersion) return@transaction null
        val nextVersion = storedVersion + 1
        // La condición sobre version va en el UPDATE, no sólo en el chequeo de arriba: así
        // dos transacciones concurrentes no pueden escribir ambas la misma versión.
        val updated = ScreensTable.update({
            (ScreensTable.slug eq slug) and (ScreensTable.version eq storedVersion)
        }) {
            it[version] = nextVersion
            it[sectionsJson] = encoded
            it[active] = true
            it[updatedAt] = now
        }
        if (updated == 0) return@transaction null
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
