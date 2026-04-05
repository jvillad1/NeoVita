package com.neovita.server.db.repositories

import com.neovita.server.db.tables.AssessmentsTable
import com.neovita.server.services.PillarScores
import com.neovita.server.services.ScoreService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class AssessmentEntity(
    val id: String, val userId: String, val createdAt: Long,
    val exerciseFrequency: String, val exerciseType: String,
    val sleepHours: String, val sleepQuality: Int, val mainGoal: String,
    val scores: PillarScores
)

class AssessmentRepository {
    fun save(
        userId: String, frequency: String, type: String,
        sleepHours: String, sleepQuality: Int, goal: String
    ): AssessmentEntity {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val scores = ScoreService.calculate(frequency, type, sleepHours, sleepQuality)
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

    private fun ResultRow.toEntity(): AssessmentEntity {
        val freq = this[AssessmentsTable.exerciseFrequency]
        val type = this[AssessmentsTable.exerciseType]
        val sh = this[AssessmentsTable.sleepHours]
        val sq = this[AssessmentsTable.sleepQuality]
        return AssessmentEntity(
            id = this[AssessmentsTable.id],
            userId = this[AssessmentsTable.userId],
            createdAt = this[AssessmentsTable.createdAt],
            exerciseFrequency = freq, exerciseType = type,
            sleepHours = sh, sleepQuality = sq,
            mainGoal = this[AssessmentsTable.mainGoal],
            scores = ScoreService.calculate(freq, type, sh, sq)
        )
    }
}
