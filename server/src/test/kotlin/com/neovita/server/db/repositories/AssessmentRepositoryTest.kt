package com.neovita.server.db.repositories

import com.neovita.server.db.tables.AssessmentsTable
import com.neovita.server.db.tables.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `latestScoresFor` sólo usa la evaluación más reciente de cada miembro, pero traía el
 * historial completo. Estos tests fijan las dos cosas que importan: que el resultado sigue
 * siendo el correcto, y que el número de filas leídas ya no crece con el historial.
 */
class AssessmentRepositoryTest {

    // JUnit crea una instancia por método, así que un contador de instancia volvería a 0 en
    // cada test y los tres compartirían la misma base H2 (persiste con DB_CLOSE_DELAY=-1).
    private companion object { val dbCounter = java.util.concurrent.atomic.AtomicInteger(0) }

    private fun freshDb(): Database {
        val db = Database.connect(
            "jdbc:h2:mem:assessment_repo_${dbCounter.incrementAndGet()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver"
        )
        transaction(db) { SchemaUtils.create(UsersTable, AssessmentsTable) }
        return db
    }

    private fun Database.addUser(id: String) = transaction(this) {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[email] = "$id@test.com"
            it[name] = id
            it[role] = "USER"
        }
    }

    private fun Database.addAssessment(
        id: String, userId: String, createdAt: Long, quality: Int
    ) = transaction(this) {
        AssessmentsTable.insert {
            it[AssessmentsTable.id] = id
            it[AssessmentsTable.userId] = userId
            it[AssessmentsTable.createdAt] = createdAt
            it[exerciseFrequency] = "3-4 veces por semana"
            it[exerciseType] = "Caminar"
            it[sleepHours] = "7-8 horas"
            it[sleepQuality] = quality
            it[mainGoal] = "Energía"
        }
    }

    /** Captura el SQL de los SELECT sobre `assessments` ejecutados dentro del bloque. */
    private fun capturedAssessmentSql(db: Database, block: () -> Unit): List<String> {
        val seen = mutableListOf<String>()
        val interceptor = object : StatementInterceptor {
            override fun beforeExecution(transaction: Transaction, context: StatementContext) {
                val sql = context.sql(transaction).lowercase()
                if (sql.startsWith("select") && sql.contains("assessments")) seen += sql
            }
        }
        transaction(db) {
            registerInterceptor(interceptor)
            try { block() } finally { unregisterInterceptor(interceptor) }
        }
        return seen
    }

    @Test
    fun `the newest assessment wins, however long the history is`() {
        val db = freshDb()
        db.addUser("u1")
        // 20 evaluaciones viejas con calidad 1, y la más reciente con calidad 10.
        repeat(20) { i -> db.addAssessment("old-$i", "u1", createdAt = 1_000L + i, quality = 1) }
        db.addAssessment("newest", "u1", createdAt = 9_000L, quality = 10)

        val scores = AssessmentRepository().latestScoresFor(listOf("u1"))

        // La calidad de sueño 10 sólo puede venir de la evaluación más reciente.
        val fromNewest = AssessmentRepository().latestScoresFor(listOf("u1"))["u1"]
        assertEquals(scores["u1"], fromNewest)
        assertTrue(scores.containsKey("u1"))
        assertTrue(
            scores.getValue("u1").sleep > 50,
            "el pilar de sueño sugiere que ganó una evaluación vieja: ${scores.getValue("u1")}"
        )
    }

    @Test
    fun `a tie on createdAt resolves the same way every time`() {
        val db = freshDb()
        db.addUser("u1")
        // Mismo instante: createdAt son milisegundos y dos guardados pueden coincidir.
        db.addAssessment("bbb", "u1", createdAt = 5_000L, quality = 2)
        db.addAssessment("aaa", "u1", createdAt = 5_000L, quality = 9)

        val repo = AssessmentRepository()
        val first = repo.latestScoresFor(listOf("u1"))["u1"]
        repeat(4) {
            assertEquals(first, repo.latestScoresFor(listOf("u1"))["u1"], "el empate no es estable")
        }
    }

    @Test
    fun `no query reads the whole assessment history`() {
        val db = freshDb()
        db.addUser("u1")
        db.addUser("u2")
        repeat(10) { i ->
            db.addAssessment("u1-h$i", "u1", 1_000L + i, 5)
            db.addAssessment("u2-h$i", "u2", 1_000L + i, 5)
        }

        val statements = capturedAssessmentSql(db) {
            AssessmentRepository().latestScoresFor(listOf("u1", "u2"))
        }

        assertTrue(statements.isNotEmpty(), "no se capturó ninguna consulta")
        // El invariante que acota la lectura: cada SELECT o agrega (max) para quedarse con
        // una fila por usuario, o filtra por el instante concreto ya resuelto. Un SELECT
        // que sólo filtre por user_id devuelve el historial entero — que es lo que hacía.
        statements.forEach { sql ->
            assertTrue(
                sql.contains("max(") || sql.contains("created_at ="),
                "esta consulta lee el historial completo: $sql"
            )
        }
    }
}
