package com.neovita.server.db.repositories

import com.neovita.server.db.tables.AssessmentsTable
import com.neovita.shared.data.mapper.toDto
import com.neovita.shared.domain.usecase.CalculateScoresUseCase
import com.neovita.shared.network.dto.PillarScoresDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class AssessmentEntity(
    val id: String, val userId: String, val createdAt: Long,
    val exerciseFrequency: String, val exerciseType: String,
    val sleepHours: String, val sleepQuality: Int, val mainGoal: String,
    val scores: PillarScoresDto
)

class AssessmentRepository(private val healthRepository: HealthRepository? = null) {
    private val calculateScores = CalculateScoresUseCase()
    fun save(
        userId: String, frequency: String, type: String,
        sleepHours: String, sleepQuality: Int, goal: String
    ): AssessmentEntity {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val scores = calculateScores(
            frequency, type, sleepHours, sleepQuality,
            health = healthRepository?.summary(userId)
        ).toDto()
        transaction {
            AssessmentsTable.insert {
                it[AssessmentsTable.id] = id
                it[AssessmentsTable.userId] = userId
                it[createdAt] = now
                it[exerciseFrequency] = frequency
                it[exerciseType] = type
                it[AssessmentsTable.sleepHours] = sleepHours
                it[AssessmentsTable.sleepQuality] = sleepQuality
                it[mainGoal] = goal
            }
        }
        return AssessmentEntity(id, userId, now, frequency, type, sleepHours, sleepQuality, goal, scores)
    }

    fun findLatest(userId: String): AssessmentEntity? = transaction {
        AssessmentsTable.selectAll()
            .where { AssessmentsTable.userId eq userId }
            .orderBy(AssessmentsTable.createdAt, SortOrder.DESC)
            .limit(1).singleOrNull()?.toEntity()
    }

    /** Puntuaciones de la evaluación más reciente de cada usuario, en una sola consulta
     *  (el dashboard de empresa las pedía una por miembro). */
    fun latestScoresFor(userIds: List<String>): Map<String, PillarScoresDto> {
        if (userIds.isEmpty()) return emptyMap()
        val health = healthRepository?.summariesFor(userIds).orEmpty()
        return transaction {
            // Sólo se usa la evaluación más reciente de cada miembro, así que traer el
            // historial completo y quedarse con la primera fila hacía que el coste del
            // dashboard creciera con cada evaluación que alguien hubiera respondido nunca.
            // Paso 1: el instante más reciente por usuario — una fila por usuario.
            val newest = AssessmentsTable.createdAt.max()
            val latestPerUser = AssessmentsTable
                .select(AssessmentsTable.userId, newest)
                .where { AssessmentsTable.userId inList userIds }
                .groupBy(AssessmentsTable.userId)
                .mapNotNull { row ->
                    val ts = row[newest] ?: return@mapNotNull null
                    row[AssessmentsTable.userId] to ts
                }
            if (latestPerUser.isEmpty()) return@transaction emptyMap()

            // Paso 2: exactamente esas filas (par usuario+instante), no el historial.
            val onlyTheLatest = latestPerUser
                .map { (uid, ts) ->
                    (AssessmentsTable.userId eq uid) and (AssessmentsTable.createdAt eq ts)
                }
                .reduce { acc, condition -> acc or condition }

            AssessmentsTable.selectAll()
                .where(onlyTheLatest)
                .groupBy { it[AssessmentsTable.userId] }
                .mapValues { (userId, rows) ->
                    // createdAt son milisegundos: dos evaluaciones del mismo usuario pueden
                    // empatar. Desempatar por id deja el resultado estable entre peticiones
                    // en vez de depender del orden que devuelva la base de datos.
                    val row = rows.minByOrNull { it[AssessmentsTable.id] } ?: rows.first()
                    calculateScores(
                        row[AssessmentsTable.exerciseFrequency],
                        row[AssessmentsTable.exerciseType],
                        row[AssessmentsTable.sleepHours],
                        row[AssessmentsTable.sleepQuality],
                        health = health[userId]
                    ).toDto()
                }
        }
    }

    private fun ResultRow.toEntity(): AssessmentEntity {
        val freq = this[AssessmentsTable.exerciseFrequency]
        val type = this[AssessmentsTable.exerciseType]
        val sh = this[AssessmentsTable.sleepHours]
        val sq = this[AssessmentsTable.sleepQuality]
        val uid = this[AssessmentsTable.userId]
        val scores = calculateScores(freq, type, sh, sq, health = healthRepository?.summary(uid)).toDto()
        return AssessmentEntity(
            id = this[AssessmentsTable.id],
            userId = uid,
            createdAt = this[AssessmentsTable.createdAt],
            exerciseFrequency = freq, exerciseType = type,
            sleepHours = sh, sleepQuality = sq,
            mainGoal = this[AssessmentsTable.mainGoal],
            scores = scores
        )
    }
}
