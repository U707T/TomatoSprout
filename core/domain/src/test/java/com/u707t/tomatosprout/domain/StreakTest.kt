package com.u707t.tomatosprout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 3, 10)
    private fun rule(
        frequency: Frequency = Frequency.DAILY,
        mask: Int = 0b1111111,
        start: LocalDate = today.minusDays(60),
        target: Int = 1,
    ) = HabitRule("h1", frequency, target, null, mask, 1, start)

    private fun check(
        date: LocalDate,
        id: String = date.toString(),
        count: Int = 1,
        makeup: Boolean = false,
        deleted: Long? = null,
    ) = CheckIn(id, "h1", date, count, isMakeup = makeup, createdAt = 0L, deletedAt = deleted)

    @Test
    fun `daily streak counts consecutive days including today`() {
        val checks = (0..4).map { check(today.minusDays(it.toLong())) }
        assertEquals(5, StreakCalculator.currentStreak(rule(), checks, today))
    }

    @Test
    fun `today not checked yet does not break streak`() {
        val checks = (1..3).map { check(today.minusDays(it.toLong())) }
        assertEquals(3, StreakCalculator.currentStreak(rule(), checks, today))
    }

    @Test
    fun `gap resets current streak`() {
        val checks = listOf(
            check(today), check(today.minusDays(1)),
            check(today.minusDays(3)), check(today.minusDays(4)),
        )
        assertEquals(2, StreakCalculator.currentStreak(rule(), checks, today))
        assertEquals(2, StreakCalculator.longestStreak(rule(), checks, today))
    }

    @Test
    fun `makeup counts as a normal day`() {
        val checks = listOf(check(today), check(today.minusDays(1), makeup = true), check(today.minusDays(2)))
        assertEquals(3, StreakCalculator.currentStreak(rule(), checks, today))
    }

    @Test
    fun `deleted records are ignored`() {
        val checks = listOf(check(today), check(today.minusDays(1), deleted = 99L), check(today.minusDays(2)))
        assertEquals(1, StreakCalculator.currentStreak(rule(), checks, today))
    }

    @Test
    fun `duplicate checkins on same day count once`() {
        val checks = listOf(
            check(today, id = "a"), check(today, id = "b"),
            check(today.minusDays(1), id = "c"),
        )
        assertEquals(2, StreakCalculator.currentStreak(rule(), checks, today))
    }

    @Test
    fun `weekdays schedule skips weekend`() {
        // 2026-03-09 是周一，03-08 周日
        val monday = LocalDate.of(2026, 3, 9)
        val checks = listOf(
            check(monday), check(monday.minusDays(3)), check(monday.minusDays(4)),
        )
        val r = rule(Frequency.WEEKDAYS, start = monday.minusDays(30))
        // 周一、周五、周四连续三个排期日
        assertEquals(3, StreakCalculator.currentStreak(r, checks, monday))
    }

    @Test
    fun `weekly mask skips unscheduled days`() {
        val r = rule(Frequency.WEEKLY, mask = 0b1010101) // 一、三、五、日
        val checks = listOf(
            check(today),                    // 周二：非排期，忽略
            check(today.minusDays(1)),       // 周一
            check(today.minusDays(2)),       // 周日
            check(today.minusDays(4)),       // 周五（周六非排期，自动跳过）
        )
        assertEquals(3, StreakCalculator.currentStreak(r, checks, today))
    }

    @Test
    fun `timezone boundary uses passed local dates`() {
        // Domain 只接受 LocalDate，时区换算在调用方完成；这里验证跨月连续
        val first = LocalDate.of(2026, 2, 28)
        val checks = listOf(check(first), check(first.minusDays(1)), check(first.minusDays(2)))
        assertEquals(3, StreakCalculator.currentStreak(rule(), checks, first))
    }

    @Test
    fun `target count requires reaching target`() {
        val r = rule(target = 2)
        val checks = listOf(check(today, count = 1), check(today.minusDays(1), count = 2))
        // 今天未达标不算中断，昨天达标 → 连续 1
        assertEquals(1, StreakCalculator.currentStreak(r, checks, today))
        assertEquals(1, StreakCalculator.longestStreak(r, checks, today))
    }

    @Test
    fun `completion rate over scheduled window`() {
        val r = rule()
        val checks = listOf(check(today), check(today.minusDays(2)))
        val rate = StreakCalculator.completionRate(r, checks, today.minusDays(4), today, today)
        assertEquals(2f / 5f, rate, 0.001f)
    }
}
