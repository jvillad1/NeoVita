package com.neovita.server.db.tables

import org.jetbrains.exposed.sql.Table

/**
 * The team averages an employer is shown, frozen at [createdAt]. See TeamSnapshotPolicy for
 * why the employer never sees live numbers.
 */
object TeamSnapshotsTable : Table("team_snapshots") {
    val id = varchar("id", 36)
    val companyId = varchar("company_id", 36).index()
    val createdAt = long("created_at")
    val overall = integer("overall")
    val exercise = integer("exercise")
    val sleep = integer("sleep")
    val nutrition = integer("nutrition")
    // Salted hashes, one per evaluated member, only used to count who changed since this
    // snapshot. Deliberately not the scores themselves: no copy of anyone's health data lives
    // here (it would survive that person resetting their history).
    val salt = varchar("salt", 36)
    val memberFingerprints = text("member_fingerprints") // JSON object: userId -> fingerprint
    override val primaryKey = PrimaryKey(id)
}
