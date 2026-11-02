package com.neovita.server.db.repositories

import com.neovita.server.db.tables.TeamSnapshotsTable
import com.neovita.server.services.TeamSnapshotPolicy
import com.neovita.shared.network.dto.PillarScoresDto
import com.neovita.shared.network.dto.TeamResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class TeamSnapshot(val createdAt: Long, val averages: PillarScoresDto)

class TeamSnapshotRepository {

    private class Stored(val snapshot: TeamSnapshot, val salt: String, val fingerprints: Map<String, String>)

    /**
     * The snapshot the employer of [companyId] should see, given each evaluated member's
     * current [scores]; publishes a new one first if [TeamSnapshotPolicy] allows it.
     * Null when too few members are evaluated right now.
     */
    fun currentFor(
        companyId: String,
        scores: Map<String, PillarScoresDto>,
        now: Long = System.currentTimeMillis()
    ): TeamSnapshot? = transaction {
        if (scores.size < TeamResponse.MIN_GROUP_SIZE) return@transaction null
        val previous = latest(companyId)
        val changed = previous?.let { p ->
            TeamSnapshotPolicy.changedMembers(p.fingerprints, fingerprints(p.salt, scores))
        } ?: scores.size
        if (!TeamSnapshotPolicy.shouldPublish(previous?.snapshot?.createdAt, changed, scores.size, now)) {
            return@transaction previous?.snapshot
        }
        val salt = UUID.randomUUID().toString()
        val averages = TeamSnapshotPolicy.aggregate(scores.values)
        TeamSnapshotsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[TeamSnapshotsTable.companyId] = companyId
            it[createdAt] = now
            it[overall] = averages.overall
            it[exercise] = averages.exercise
            it[sleep] = averages.sleep
            it[nutrition] = averages.nutrition
            it[TeamSnapshotsTable.salt] = salt
            it[memberFingerprints] = Json.encodeToString(fingerprints(salt, scores))
        }
        TeamSnapshot(now, averages)
    }

    private fun fingerprints(salt: String, scores: Map<String, PillarScoresDto>) =
        scores.mapValues { (userId, s) -> TeamSnapshotPolicy.fingerprint(salt, userId, s) }

    private fun latest(companyId: String): Stored? = TeamSnapshotsTable.selectAll()
        .where { TeamSnapshotsTable.companyId eq companyId }
        .orderBy(TeamSnapshotsTable.createdAt, SortOrder.DESC)
        .limit(1)
        .firstOrNull()
        ?.let { row ->
            Stored(
                snapshot = TeamSnapshot(
                    createdAt = row[TeamSnapshotsTable.createdAt],
                    averages = PillarScoresDto(
                        overall = row[TeamSnapshotsTable.overall],
                        exercise = row[TeamSnapshotsTable.exercise],
                        sleep = row[TeamSnapshotsTable.sleep],
                        nutrition = row[TeamSnapshotsTable.nutrition],
                    )
                ),
                salt = row[TeamSnapshotsTable.salt],
                fingerprints = Json.decodeFromString(row[TeamSnapshotsTable.memberFingerprints])
            )
        }
}
