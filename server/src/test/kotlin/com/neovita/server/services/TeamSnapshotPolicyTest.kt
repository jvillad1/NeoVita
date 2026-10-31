package com.neovita.server.services

import com.neovita.server.services.TeamSnapshotPolicy.MIN_INTERVAL_MS
import com.neovita.shared.network.dto.PillarScoresDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TeamSnapshotPolicyTest {

    private fun s(v: Int) = PillarScoresDto(overall = v, exercise = v, sleep = v, nutrition = v)

    @Test
    fun `joins, leaves and changed scores all count as changes`() {
        val previous = mapOf("ana" to "1", "beto" to "2", "cami" to "3")
        val current = mapOf("ana" to "1", "beto" to "changed", "dani" to "4")
        // beto changed, cami left, dani joined; ana is the same.
        assertEquals(3, TeamSnapshotPolicy.changedMembers(previous, current))
    }

    @Test
    fun `the fingerprint depends on the scores and the salt, and does not contain them`() {
        val a = TeamSnapshotPolicy.fingerprint("salt", "ana", s(70))
        assertEquals(a, TeamSnapshotPolicy.fingerprint("salt", "ana", s(70)))
        assertNotEquals(a, TeamSnapshotPolicy.fingerprint("salt", "ana", s(71)))
        assertNotEquals(a, TeamSnapshotPolicy.fingerprint("other", "ana", s(70)))
        assertTrue(a.matches(Regex("[0-9a-f]{64}")), "not an opaque SHA-256 hex digest: $a")
    }

    @Test
    fun `averages are rounded to the nearest 5`() {
        val avg = TeamSnapshotPolicy.aggregate(listOf(s(61), s(62), s(64), s(66), s(70)))
        assertEquals(65, avg.overall) // 64.6
        assertEquals(60, TeamSnapshotPolicy.aggregate(List(5) { s(62) }).sleep)
    }

    @Test
    fun `a first snapshot needs only the minimum group`() {
        assertTrue(TeamSnapshotPolicy.shouldPublish(previousCreatedAt = null, changed = 5, evaluated = 5, now = 0))
        assertFalse(TeamSnapshotPolicy.shouldPublish(previousCreatedAt = null, changed = 4, evaluated = 4, now = 0))
    }

    @Test
    fun `a new snapshot needs a month and at least 5 changed people`() {
        val now = 10 * MIN_INTERVAL_MS
        val monthAgo = now - MIN_INTERVAL_MS
        assertTrue(TeamSnapshotPolicy.shouldPublish(monthAgo, changed = 5, evaluated = 6, now = now))
        assertFalse(TeamSnapshotPolicy.shouldPublish(monthAgo, changed = 4, evaluated = 6, now = now))
        assertFalse(TeamSnapshotPolicy.shouldPublish(monthAgo + 1, changed = 6, evaluated = 6, now = now))
    }
}
