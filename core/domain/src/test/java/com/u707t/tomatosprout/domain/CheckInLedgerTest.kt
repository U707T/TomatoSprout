package com.u707t.tomatosprout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CheckInLedgerTest {
    private val today = LocalDate.of(2026, 3, 10)

    private fun rule(target: Int = 1, start: LocalDate = today.minusDays(60)) =
        HabitRule("h1", Frequency.DAILY, target, null, 0b1111111, 1, start)

    private fun check(
        date: LocalDate,
        count: Int = 1,
        id: String = "$date-$count",
        deleted: Long? = null,
    ) = CheckIn(id, "h1", date, count, isMakeup = false, createdAt = 0L, deletedAt = deleted)

    @Test
    fun `soft deleted records are ignored`() {
        val ledger = CheckInLedger(
            listOf(check(today), check(today, count = 5, id = "deleted", deleted = 1L)),
        )
        assertEquals(1, ledger.countOn("h1", today))
    }

    @Test
    fun `multi count records accumulate to the target`() {
        val ledger = CheckInLedger(
            listOf(check(today, count = 1, id = "a"), check(today, count = 1, id = "b")),
        )
        assertEquals(2, ledger.countOn("h1", today))
        assertTrue(ledger.isMet(rule(target = 2), today))
        assertFalse(ledger.isMet(rule(target = 3), today))
    }

    @Test
    fun `counts are isolated per task`() {
        val other = CheckIn("other", "h2", today, 3, createdAt = 0L)
        val ledger = CheckInLedger(listOf(check(today), other))
        assertEquals(1, ledger.countOn("h1", today))
        assertEquals(3, ledger.countOn("h2", today))
        assertEquals(0, ledger.countOn("missing", today))
    }

    @Test
    fun `ledger streak matches the list based implementation`() {
        val checks = listOf(
            check(today),
            check(today.minusDays(1), count = 2, id = "y"),
            check(today.minusDays(2)),
            check(today.minusDays(4)),
        )
        val r = rule(target = 2)
        val ledger = CheckInLedger(checks)

        assertEquals(
            StreakCalculator.currentStreak(r, checks, today),
            StreakCalculator.currentStreak(r, ledger, today),
        )
        assertEquals(
            StreakCalculator.longestStreak(r, checks, today),
            StreakCalculator.longestStreak(r, ledger, today),
        )
        assertEquals(
            StreakCalculator.completionRate(r, checks, today.minusDays(9), today, today),
            StreakCalculator.completionRate(r, ledger, today.minusDays(9), today, today),
            0.0001f,
        )
    }

    @Test
    fun `latest date keeps future records for timezone changes`() {
        val future = today.plusDays(1)
        val ledger = CheckInLedger(listOf(check(today), check(future)))
        assertEquals(future, ledger.latestDate("h1"))
        assertNull(ledger.latestDate("missing"))
    }
}
