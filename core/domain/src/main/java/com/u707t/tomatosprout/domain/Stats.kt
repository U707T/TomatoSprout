package com.u707t.tomatosprout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** 专注归属日期：优先使用落库时算好的 attributedDate，旧数据回退到结束时间。 */
fun FocusSession.attributedLocalDate(zone: ZoneId): LocalDate =
    attributedDate ?: Instant.ofEpochMilli(endedAt).atZone(zone).toLocalDate()

/** 统一处理「孤儿数据」的展示名：任务已删除或查不到时保留原始名称并置灰。 */
object TaskDisplay {
    fun isDeleted(task: Task?): Boolean = task == null || task.deletedAt != null

    fun title(task: Task?, fallback: String = "原任务"): String = when {
        task == null -> "[已删除] $fallback"
        task.deletedAt != null -> "[已删除] ${task.title}"
        else -> task.title
    }
}

data class HeatmapData(val metric: HeatmapMetric, val values: Map<LocalDate, Long>)

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
        // 统计一次只建一份索引，供所有习惯的连续天数 / 完成率 / 圆满日复用
        val ledger = CheckInLedger(activeChecks)
        // 防脏数据：时长 < 0 或结束时间非法的一律不参与统计与绘图
        val activeFocus = focusSessions.filter {
            it.deletedAt == null && it.actualSeconds >= 0 && it.endedAt > 0L
        }
        val taskById = tasks.associateBy { it.id }

        val habits = habitRules.mapNotNull { rule ->
            val task = taskById[rule.taskId] ?: return@mapNotNull null
            if (task.deletedAt != null) return@mapNotNull null
            HabitSnapshot(
                task = task,
                rule = rule,
                currentStreak = StreakCalculator.currentStreak(rule, ledger, today),
                longestStreak = StreakCalculator.longestStreak(rule, ledger, today),
                completion30d = StreakCalculator.completionRate(
                    rule, ledger, today.minusDays(29), today, today,
                ),
            )
        }

        val dayMap = HashMap<LocalDate, DayAggregate>()
        activeChecks.forEach { check ->
            val current = dayMap[check.date] ?: DayAggregate(check.date)
            dayMap[check.date] = current.copy(checkIns = current.checkIns + max(1, check.count))
        }
        activeFocus.forEach { session ->
            val date = session.attributedLocalDate(zone)
            val current = dayMap[date] ?: DayAggregate(date)
            dayMap[date] = current.copy(
                focusSeconds = current.focusSeconds + session.actualSeconds,
                pomos = current.pomos + if (session.mode == "pomodoro" && session.completed) 1 else 0,
            )
        }

        val perfectDays = countPerfectDays(today, habits, ledger)
        val goalProgress = goalRules.associate { goal ->
            val count = activeChecks.filter {
                it.taskId == goal.taskId && !it.date.isBefore(goal.startDate) && !it.date.isAfter(goal.endDate)
            }.sumOf { max(1, it.count).toLong() }
            val seconds = activeFocus.filter {
                it.taskId == goal.taskId &&
                    it.attributedLocalDate(zone).let { d -> !d.isBefore(goal.startDate) && !d.isAfter(goal.endDate) }
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
            totalPomos = activeFocus.count { it.mode == "pomodoro" && it.completed },
            perfectDays = perfectDays,
            badges = badges,
            checks = activeChecks,
            // 保留已删除任务，历史记录才能显示「[已删除] 原任务名」
            tasks = tasks.sortedBy { it.createdAt },
            sessions = activeFocus,
            goalProgress = goalProgress,
        )
    }

    private fun countPerfectDays(today: LocalDate, habits: List<HabitSnapshot>, ledger: CheckInLedger): Int {
        if (habits.isEmpty()) return 0
        val earliest = habits.minOfOrNull { it.rule.startDate } ?: return 0
        val from = maxOf(earliest, today.minusDays(730))
        var perfect = 0
        var date = from
        while (!date.isAfter(today)) {
            val scheduled = habits.filter { HabitSchedule.isScheduled(it.rule, date) }
            if (scheduled.isNotEmpty() && scheduled.all { ledger.isMet(it.rule, date) }) {
                perfect++
            }
            date = date.plusDays(1)
        }
        return perfect
    }
}
