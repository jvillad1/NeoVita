package com.neovita.server.db.repositories

import com.neovita.server.db.tables.DeviceTokensTable
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
import kotlin.test.assertTrue

/**
 * El token es la clave primaria y `upsert` es ahora una sola sentencia atómica.
 *
 * Aviso para quien lea esto buscando el bug que arregló: no lo hubo. Con el UPDATE-y-si-no-
 * INSERT anterior, dos registros simultáneos chocaban contra la clave primaria, pero Exposed
 * reintentaba la transacción y el UPDATE del reintento daba el resultado correcto — medido:
 * `update → insert (violación) → update`. El cambio quita la excepción y el viaje de más, no
 * un fallo de comportamiento. Este test fija el comportamiento para que siga siendo así.
 *
 * La carrera se reproduce de forma determinista colando la fila rival desde otra conexión
 * justo antes de la escritura, sin hilos ni suerte.
 */
class DeviceTokenRepositoryTest {

    // JUnit crea una instancia por método: un contador de instancia volvería a 0 y los tests
    // compartirían la misma base H2 (persiste con DB_CLOSE_DELAY=-1).
    private companion object { val dbCounter = java.util.concurrent.atomic.AtomicInteger(0) }

    /** Dos conexiones a la MISMA base: la del servidor y la del rival. */
    private fun freshDbPair(name: String): Pair<Database, Database> {
        val url = "jdbc:h2:mem:device_tokens_${name}_${dbCounter.incrementAndGet()};" +
            "DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
        val db = Database.connect(url, driver = "org.h2.Driver")
        val rival = Database.connect(url, driver = "org.h2.Driver")
        transaction(db) { SchemaUtils.create(DeviceTokensTable) }
        return db to rival
    }

    private fun insertRowDirectly(db: Database, token: String, userId: String) = transaction(db) {
        DeviceTokensTable.insert {
            it[DeviceTokensTable.token] = token
            it[DeviceTokensTable.userId] = userId
            it[platform] = "android"
            it[updatedAt] = 1L
        }
    }

    @Test
    fun `registering the same token twice at once does not blow up`() {
        val (db, rival) = freshDbPair("race")
        var injected = false
        val seen = mutableListOf<String>()

        val interceptor = object : StatementInterceptor {
            override fun beforeExecution(transaction: Transaction, context: StatementContext) {
                val sql = context.sql(transaction).lowercase()
                seen += sql.take(90)
                // Antes de la ESCRITURA que crea la fila, no antes del UPDATE: si el rival
                // aparece antes del UPDATE, el UPDATE lo encuentra y no hay carrera que probar.
                // (Con el código arreglado la única sentencia es el upsert, y se inyecta ahí.)
                val esCreacion = !sql.startsWith("select") && !sql.startsWith("update")
                if (!injected && esCreacion && sql.contains("device_tokens")) {
                    injected = true
                    // Otra conexión, transacción propia: tiene que quedar CONFIRMADA antes de
                    // que el servidor escriba. Inyectarla en la misma transacción no simula
                    // nada — el rollback del choque se la llevaría.
                    insertRowDirectly(rival, "tok-1", "otro-usuario")
                }
            }
        }

        transaction(db) {
            registerInterceptor(interceptor)
            try {
                DeviceTokenRepository().upsert("tok-1", "usuario-real", "android")
            } finally {
                unregisterInterceptor(interceptor)
            }
        }

        assertTrue(injected, "la carrera no llegó a simularse. SQL visto: $seen")
        val rows = transaction(db) { DeviceTokensTable.selectAll().toList() }
        assertEquals(1, rows.size, "el token es la clave primaria: no puede haber dos filas")
        // El último registro es el que manda: el dispositivo acaba de decirnos de quién es.
        assertEquals("usuario-real", rows.single()[DeviceTokensTable.userId])
    }

    @Test
    fun `re-registering an existing token moves it to the new owner`() {
        val (db, _) = freshDbPair("reassign")
        val repo = DeviceTokenRepository()

        transaction(db) { repo.upsert("tok-2", "ana", "android") }
        transaction(db) { repo.upsert("tok-2", "beto", "ios") }

        val rows = transaction(db) { DeviceTokensTable.selectAll().toList() }
        assertEquals(1, rows.size)
        assertEquals("beto", rows.single()[DeviceTokensTable.userId])
        assertEquals("ios", rows.single()[DeviceTokensTable.platform])
    }

    @Test
    fun `pruning drops exactly the dead tokens`() {
        val (db, _) = freshDbPair("prune")
        val repo = DeviceTokenRepository()
        transaction(db) {
            repo.upsert("vivo", "ana", "android")
            repo.upsert("muerto-1", "ana", "android")
            repo.upsert("muerto-2", "beto", "ios")
        }

        val removed = transaction(db) { repo.delete(listOf("muerto-1", "muerto-2", "nunca-existió")) }

        assertEquals(2, removed, "sólo se borran los que existían")
        val left = transaction(db) { DeviceTokensTable.selectAll().map { it[DeviceTokensTable.token] } }
        assertEquals(listOf("vivo"), left)
    }
}
