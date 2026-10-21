package com.neovita.server.db.repositories

import com.neovita.server.db.tables.HabitLogsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

class HabitRepository {

    /** Marks [habitId] done on [date] if it wasn't, or un-marks it if it was. Returns the new state. */
    fun toggle(userId: String, habitId: String, date: String): Boolean = transaction {
        val existing = HabitLogsTable.selectAll()
            .where { (HabitLogsTable.userId eq userId) and (HabitLogsTable.habitId eq habitId) and (HabitLogsTable.date eq date) }
            .count() > 0
        if (existing) {
            HabitLogsTable.deleteWhere {
                (HabitLogsTable.userId eq userId) and (HabitLogsTable.habitId eq habitId) and (HabitLogsTable.date eq date)
            }
            false
        } else {
            HabitLogsTable.insert {
                it[HabitLogsTable.userId] = userId
                it[HabitLogsTable.habitId] = habitId
                it[HabitLogsTable.date] = date
                it[loggedAt] = System.currentTimeMillis()
            }
            true
        }
    }

    fun habitIdsFor(userId: String, date: String): Set<String> = transaction {
        HabitLogsTable.selectAll()
            .where { (HabitLogsTable.userId eq userId) and (HabitLogsTable.date eq date) }
            .map { it[HabitLogsTable.habitId] }
            .toSet()
    }

    /**
     * Días consecutivos con al menos un hábito marcado, contando hacia atrás desde el día más
     * reciente registrado. No se rompe por no haber marcado nada *todavía hoy* — sólo se
     * rompe si el último día registrado fue antier o antes (un día entero saltado).
     */
    fun currentStreak(userId: String, today: LocalDate = LocalDate.now()): Int = transaction {
        val days = HabitLogsTable.selectAll()
            .where { HabitLogsTable.userId eq userId }
            .map { LocalDate.parse(it[HabitLogsTable.date]) }
            .toSortedSet()
            .toList()
            .asReversed()
        if (days.isEmpty()) return@transaction 0

        val mostRecent = days.first()
        if (mostRecent.isBefore(today.minusDays(1))) return@transaction 0

        var streak = 0
        var expected = mostRecent
        for (day in days) {
            if (day == expected) {
                streak++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }
        streak
    }
}
