package com.u707t.tomatosprout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class HabitSnapshot(
    val task: Task,
    val rule: HabitRule,
    val currentStreak: Int,
    val longestStreak: Int,
    val completion30d: Float,
)

data class DayAggregate(
    val date: LocalDate,
    val checkIns: Int = 0,
    val focusSeconds: Long = 0,
    val pomos: Int = 0,
)

data class GoalProgress(
    val used: Long,
    val target: Long?,
    val unit: String,
) {
    val ratio: Float? get() = target?.takeIf { it > 0 }?.let { (used.toFloat() / it).coerceIn(0f, 1f) }
}

enum class HeatmapMetric { CHECK_INS, FOCUS_DURATION }

data class HeatmapData(val metric: HeatmapMetric, val values: Map<LocalDate, Long>)

data class TimelineItem(
    val id: String,
    val atMillis: Long,
    val date: LocalDate,
    val timeLabel: String,
    val title: String,
    val kind: String,
    val detail: String,
    val taskId: String?,
)

data class StatsData(
    val today: LocalDate,
    val habits: List<HabitSnapshot>,
    val days: Map<LocalDate, DayAggregate>,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCheckIns: Int,
    val checkInDays: Int,
    val totalFocusSeconds: Long,
    val totalPomos: Int,
    val perfectDays: Int,
    val timeline: List<TimelineItem>,
    val badges: List<Badge>,
    val checks: List<CheckIn> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val sessions: List<FocusSession> = emptyList(),
    val goalProgress: Map<String, GoalProgress> = emptyMap(),
) {
    fun aggregate(date: LocalDate): DayAggregate = days[date] ?: DayAggregate(date)
    val unlockedBadges: Int get() = badges.count { it.unlockedAt != null }
}

object StatsCalculator {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        today: LocalDate,
        tasks: List<Task>,
        habitRules: List<HabitRule>,
        goalRules: List<GoalRule>,
        checkIns: List<CheckIn>,
        focusSessions: List<FocusSession>,
        badges: List<Badge>,
        zone: ZoneId,
    ): StatsData {
        val activeChecks = checkIns.filter { it.deletedAt == null }
        val activeFocus = focusSessions.filter { it.deletedAt == null }
        val taskById = tasks.associateBy { it.id }
        val rulesByTask = habitRules.associateBy { it.taskId }

        val habits = habitRules.mapNotNull { rule ->
            val task = taskById[rule.taskId] ?: return@mapNotNull null
            if (task.deletedAt != null) return@mapNotNull null
            HabitSnapshot(
                task = task,
                rule = rule,
                currentStreak = StreakCalculator.currentStreak(rule, activeChecks, today),
                longestStreak = StreakCalculator.longestStreak(rule, activeChecks, today),
                completion30d = StreakCalculator.completionRate(
                    rule, activeChecks, today.minusDays(29), today, today,
                ),
            )
        }

        val dayMap = HashMap<LocalDate, DayAggregate>()
        activeChecks.forEach { check ->
            val current = dayMap[check.date] ?: DayAggregate(check.date)
            dayMap[check.date] = current.copy(checkIns = current.checkIns + max(1, check.count))
        }
        activeFocus.forEach { session ->
            val date = Instant.ofEpochMilli(session.endedAt).atZone(zone).toLocalDate()
            val current = dayMap[date] ?: DayAggregate(date)
            dayMap[date] = current.copy(
                focusSeconds = current.focusSeconds + session.actualSeconds,
                pomos = current.pomos + if (session.mode == "pomodoro") 1 else 0,
            )
        }

        val perfectDays = countPerfectDays(today, habits, activeChecks)
        val timeline = buildTimeline(activeChecks, activeFocus, taskById, zone)
        val goalProgress = goalRules.associate { goal ->
            val count = activeChecks.filter {
                it.taskId == goal.taskId && !it.date.isBefore(goal.startDate) && !it.date.isAfter(goal.endDate)
            }.sumOf { max(1, it.count).toLong() }
            val seconds = activeFocus.filter {
                it.taskId == goal.taskId &&
                    Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate().let { d -> !d.isBefore(goal.startDate) && !d.isAfter(goal.endDate) }
            }.sumOf { it.actualSeconds.toLong() }
            goal.taskId to if (goal.targetTotalSeconds != null) {
                GoalProgress(used = seconds, target = goal.targetTotalSeconds, unit = "小时")
            } else {
                GoalProgress(used = count, target = goal.targetTotalCount?.toLong(), unit = "次")
            }
        }

        return StatsData(
            today = today,
            habits = habits.sortedBy { it.task.createdAt },
            days = dayMap,
            currentStreak = habits.maxOfOrNull { it.currentStreak } ?: 0,
            longestStreak = habits.maxOfOrNull { it.longestStreak } ?: 0,
            totalCheckIns = activeChecks.sumOf { max(1, it.count) },
            checkInDays = activeChecks.map { it.date }.distinct().size,
            totalFocusSeconds = activeFocus.sumOf { it.actualSeconds.toLong() },
            totalPomos = activeFocus.count { it.mode == "pomodoro" },
            perfectDays = perfectDays,
            timeline = timeline,
            badges = badges,
            checks = activeChecks,
            tasks = tasks.filter { it.deletedAt == null }.sortedBy { it.createdAt },
            sessions = activeFocus,
            goalProgress = goalProgress,
        )
    }

    private fun countPerfectDays(today: LocalDate, habits: List<HabitSnapshot>, checks: List<CheckIn>): Int {
        if (habits.isEmpty()) return 0
        val earliest = habits.minOfOrNull { it.rule.startDate } ?: return 0
        val from = maxOf(earliest, today.minusDays(730))
        var perfect = 0
        var date = from
        while (!date.isAfter(today)) {
            val scheduled = habits.filter { HabitSchedule.isScheduled(it.rule, date) }
            if (scheduled.isNotEmpty() && scheduled.all { StreakCalculator.isMet(it.rule, checks, date) }) {
                perfect++
            }
            date = date.plusDays(1)
        }
        return perfect
    }

    private fun buildTimeline(
        checks: List<CheckIn>,
        focus: List<FocusSession>,
        tasks: Map<String, Task>,
        zone: ZoneId,
    ): List<TimelineItem> {
        val items = ArrayList<TimelineItem>(checks.size + focus.size)
        checks.forEach { check ->
            val title = tasks[check.taskId]?.title ?: "已删除的习惯"
            val at = check.createdAt
            items += TimelineItem(
                id = "check-${check.id}",
                atMillis = at,
                date = check.date,
                timeLabel = Instant.ofEpochMilli(at).atZone(zone).format(timeFmt),
                title = title,
                kind = "checkin",
                detail = when {
                    check.isMakeup -> "补卡"
                    check.count > 1 -> "打卡 ×${check.count}"
                    else -> "打卡"
                },
                taskId = check.taskId,
            )
        }
        focus.forEach { session ->
            val title = session.taskId?.let { tasks[it]?.title } ?: "自由专注"
            val at = session.endedAt
            val minutes = max(1, session.actualSeconds / 60)
            items += TimelineItem(
                id = "focus-${session.id}",
                atMillis = at,
                date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate(),
                timeLabel = Instant.ofEpochMilli(at).atZone(zone).format(timeFmt),
                title = title,
                kind = "focus",
                detail = "专注 $minutes 分钟" + if (session.isMidnightShifted) " · 归属前一天" else "",
                taskId = session.taskId,
            )
        }
        return items.sortedByDescending { it.atMillis }.take(500)
    }
}
