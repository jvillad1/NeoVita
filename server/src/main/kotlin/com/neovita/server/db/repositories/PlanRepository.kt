package com.neovita.server.db.repositories

import com.neovita.server.db.tables.PlansTable
import com.neovita.server.services.PillarScores
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class PlanEntity(
    val id: String, val userId: String, val generatedAt: Long,
    val planContent: String, val scores: PillarScores
)

class PlanRepository {
    fun save(userId: String, scores: PillarScores, planContent: String): PlanEntity {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        transaction {
            PlansTable.insert {
                it[PlansTable.id] = id
                it[PlansTable.userId] = userId
                it[generatedAt] = now
                it[nutritionJson] = planContent   // Stores full JSON plan from Claude
                it[sleepJson] = ""
                it[exerciseJson] = ""
                it[scoresJson] = Json.encodeToString(scores)
            }
        }
        return PlanEntity(id, userId, now, planContent, scores)
    }

    fun findCurrent(userId: String): PlanEntity? = transaction {
        PlansTable.selectAll()
            .where { PlansTable.userId eq userId }
            .orderBy(PlansTable.generatedAt, SortOrder.DESC)
            .limit(1).singleOrNull()?.let {
                PlanEntity(
                    id = it[PlansTable.id],
                    userId = it[PlansTable.userId],
                    generatedAt = it[PlansTable.generatedAt],
                    planContent = it[PlansTable.nutritionJson],
                    scores = Json.decodeFromString(it[PlansTable.scoresJson])
                )
            }
    }
}
