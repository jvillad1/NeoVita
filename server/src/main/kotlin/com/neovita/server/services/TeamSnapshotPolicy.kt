package com.neovita.server.services

import com.neovita.shared.network.dto.PillarScoresDto
import com.neovita.shared.network.dto.TeamResponse
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * When an employer gets new team numbers.
 *
 * A minimum group size alone doesn't protect anyone: if the employer reads the average before
 * and after one person answers (or re-answers, or their wearable data moves), the difference
 * times the team size is that person's score. So the employer sees a snapshot, not live
 * numbers, and a new snapshot is only published when
 *  - at least [MIN_INTERVAL_MS] (30 days) passed since the previous one, and
 *  - at least [MIN_CHANGED_MEMBERS] people changed in between (joined, left, or their
 *    scores moved),
 * so any difference between two snapshots mixes at least that many people. Averages are also
 * rounded to [ROUNDING_STEP], which blurs whatever is left.
 */
object TeamSnapshotPolicy {
    const val MIN_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
    const val MIN_CHANGED_MEMBERS = TeamResponse.MIN_GROUP_SIZE
    const val ROUNDING_STEP = 5

    fun shouldPublish(previousCreatedAt: Long?, changed: Int, evaluated: Int, now: Long): Boolean {
        if (evaluated < TeamResponse.MIN_GROUP_SIZE) return false
        if (previousCreatedAt == null) return true
        return now - previousCreatedAt >= MIN_INTERVAL_MS && changed >= MIN_CHANGED_MEMBERS
    }

    /** People present in only one of the two, or present in both with a different fingerprint. */
    fun changedMembers(previous: Map<String, String>, current: Map<String, String>): Int =
        (previous.keys + current.keys).count { previous[it] != current[it] }

    fun fingerprint(salt: String, userId: String, s: PillarScoresDto): String {
        val input = "$salt|$userId|${s.overall},${s.exercise},${s.sleep},${s.nutrition}"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun aggregate(scores: Collection<PillarScoresDto>) = PillarScoresDto(
        overall = scores.map { it.overall }.roundedAverage(),
        exercise = scores.map { it.exercise }.roundedAverage(),
        sleep = scores.map { it.sleep }.roundedAverage(),
        nutrition = scores.map { it.nutrition }.roundedAverage(),
    )

    private fun List<Int>.roundedAverage(): Int =
        (average() / ROUNDING_STEP).roundToInt() * ROUNDING_STEP
}
