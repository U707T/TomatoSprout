package com.u707t.tomatosprout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class BadgeStatsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 3, 10)

    @Test
    fun `streak badges unlock at thresholds`() {
        val stats = BadgeStats(longestStreak = 30, totalCheckIns = 99)
        assertTrue(BadgeEvaluator.qualifies(BadgeCatalog.defs.first { it.key == "streak_7" }, stats))
        assertTrue(BadgeEvaluator.qualifies(BadgeCatalog.defs.first { it.key == "streak_30" }, stats))
        assertTrue(!BadgeEvaluator.qualifies(BadgeCatalog.defs.first { it.key == "streak_100" }, stats))
        assertTrue(!BadgeEvaluator.qualifies(BadgeCatalog.defs.first { it.key == "checkins_100" }, stats))
    }

    @Test
    fun `already unlocked badges are not returned again`() {
        val stats = BadgeStats(longestStreak = 7, totalCheckIns = 100)
        val newly = BadgeEvaluator.newlyQualified(stats, setOf("streak_7", "checkins_100"))
        assertTrue(newly.none { it.key == "streak_7" || it.key == "checkins_100" })
        assertTrue(newly.any { it.key == "perfect_day" }.not())
    }

    @Test
    fun `stats calculator counts checkins focus and perfect days`() {
        val habit = Task("h1", TaskType.HABIT, "阅读", createdAt = 1, updatedAt = 1)
        val rule = HabitRule("h1", Frequency.DAILY, startDate = today.minusDays(2))
        val checks = (0..2).map { offset ->
            CheckIn(
                id = "c$offset", taskId = "h1", date = today.minusDays(offset.toLong()),
                createdAt = today.minusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        val focus = FocusSession(
            id = "f1", taskId = "h1", mode = "pomodoro", plannedSeconds = 1500, actualSeconds = 1500,
            startedAt = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            endedAt = today.atTime(9, 25).atZone(zone).toInstant().toEpochMilli(),
        )
        val stats = StatsCalculator.build(
            today = today,
            tasks = listOf(habit),
            habitRules = listOf(rule),
            goalRules = emptyList(),
            checkIns = checks,
            focusSessions = listOf(focus),
            badges = BadgeCatalog.seedBadges(),
            zone = zone,
        )
        assertEquals(3, stats.totalCheckIns)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.perfectDays)
        assertEquals(1500L, stats.totalFocusSeconds)
        assertEquals(1, stats.totalPomos)
        assertEquals(1, stats.days[today]?.checkIns)
        assertEquals(1500L, stats.days[today]?.focusSeconds)
        assertEquals(1, stats.timeline.count { it.kind == "focus" })
        assertEquals(4, stats.timeline.size)
    }

    @Test
    fun `deleted checkins do not contribute to stats`() {
        val habit = Task("h1", TaskType.HABIT, "喝水", createdAt = 1, updatedAt = 1)
        val rule = HabitRule("h1", Frequency.DAILY, startDate = today)
        val checks = listOf(
            CheckIn("a", "h1", today, createdAt = 1, deletedAt = 2),
            CheckIn("b", "h1", today, createdAt = 3),
        )
        val stats = StatsCalculator.build(today, listOf(habit), listOf(rule), emptyList(), checks, emptyList(), emptyList(), zone)
        assertEquals(1, stats.totalCheckIns)
        assertEquals(1, stats.currentStreak)
    }
}
