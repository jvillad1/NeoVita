package com.neovita.server.db.repositories

import com.neovita.server.db.tables.ScreensTable
import com.neovita.shared.network.dto.SectionDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dos peticiones pueden crear el mismo slug a la vez: ambas ven que no existe y ambas
 * insertan. Antes eso era una violación de clave primaria -> 500. La ruta ya traduce un
 * `null` de save() en 409, que es la respuesta correcta ("alguien se adelantó").
 *
 * La carrera se reproduce de forma determinista insertando la fila JUSTO antes del INSERT
 * de save(), desde otra conexión — sin necesidad de hilos ni de esperar a tener suerte.
 */
class ScreenRepositoryRaceTest {

    private companion object { val dbCounter = java.util.concurrent.atomic.AtomicInteger(0) }

    /** Devuelve dos conexiones a la MISMA base: la del servidor y la del rival. */
    private fun freshDbPair(name: String): Pair<Database, Database> {
        val url = "jdbc:h2:mem:screen_race_${name}_${dbCounter.incrementAndGet()};" +
            "DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
        val db = Database.connect(url, driver = "org.h2.Driver")
        val rival = Database.connect(url, driver = "org.h2.Driver")
        transaction(db) { SchemaUtils.create(ScreensTable) }
        return db to rival
    }

    private fun insertRowDirectly(db: Database, slug: String) = transaction(db) {
        ScreensTable.insert {
            it[ScreensTable.slug] = slug
            it[version] = 1
            it[sectionsJson] = "[]"
            it[active] = true
            it[updatedAt] = 1L
        }
    }

    @Test
    fun `losing the race to create a slug is a conflict, not a crash`() {
        val (db, rival) = freshDbPair("conflict")
        var injected = false
        val seen = mutableListOf<String>()

        // Se cuela la fila rival justo antes de la escritura de save(). No se filtra por
        // "insert": H2 en modo PostgreSQL puede renderizar insertIgnore como MERGE.
        val interceptor = object : StatementInterceptor {
            override fun beforeExecution(transaction: Transaction, context: StatementContext) {
                val sql = context.sql(transaction).lowercase()
                seen += sql.take(90)
                if (!injected && !sql.startsWith("select") && sql.contains("screen_definitions")) {
                    injected = true
                    // Conexión distinta y transacción propia: tiene que quedar CONFIRMADA
                    // antes de que el servidor intente su INSERT. Inyectarla en la misma
                    // transacción no simula nada — el rollback del choque se la lleva.
                    insertRowDirectly(rival, "dashboard")
                }
            }
        }

        val result = transaction(db) {
            registerInterceptor(interceptor)
            try {
                ScreenRepository().save("dashboard", listOf(SectionDto(type = "HERO_SCORE")))
            } finally {
                unregisterInterceptor(interceptor)
            }
        }

        assertTrue(injected, "la carrera no llegó a simularse. SQL visto: $seen")
        assertNull(result, "perder la carrera debería ser un conflicto (409)")

        // Lo que de verdad importa: la pantalla del que ganó NO se pisa. Antes de este
        // arreglo el choque provocaba un reintento de Exposed cuyo SELECT ya veía la fila
        // rival, se iba por la rama de UPDATE y la sobrescribía devolviendo 200.
        val rows = transaction(db) { ScreensTable.selectAll().toList() }
        assertEquals(1, rows.size, "se creó una fila de más")
        assertEquals("[]", rows.single()[ScreensTable.sectionsJson], "se pisó la pantalla del otro")
    }

    @Test
    fun `creating a brand new slug still works`() {
        val (db, _) = freshDbPair("happy")

        val saved = transaction(db) {
            ScreenRepository().save("nueva", listOf(SectionDto(type = "HERO_SCORE")))
        }

        assertEquals(1, saved?.version)
        assertEquals("nueva", saved?.slug)
    }
}
