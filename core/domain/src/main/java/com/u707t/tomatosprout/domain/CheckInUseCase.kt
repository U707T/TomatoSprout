package com.u707t.tomatosprout.domain

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

sealed interface CheckInResult {
    data class Checked(val streak: Int, val newBadges: List<Badge>) : CheckInResult
    data class Undone(val message: String = "已撤销打卡") : CheckInResult
    data class Rejected(val reason: String) : CheckInResult
}

data class CheckInOutcome(
    val written: Boolean,
    val checkInId: String?,
    val streak: Int,
    val newBadges: List<Badge> = emptyList(),
)

class CheckInUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val badges: BadgeRefresher,
    private val clock: AppClock = SystemAppClock,
) {
    /**
     * 习惯卡片上的直接打卡：
     * - 目标次数 > 1 时逐次累加，达到目标后再点则整体撤销；
     * - TODO 任务打卡后自动置为已完成；
     * - 写入后刷新徽章并返回最新连续天数。
     */
    suspend fun toggleToday(task: Task): CheckInResult {
        if (task.deletedAt != null) return CheckInResult.Rejected("任务已删除")
        val now = clock.nowMillis()
        val today = clock.today()
        val rule = if (task.type == TaskType.HABIT) tasks.habitRule(task.id) else null
        val target = max(1, rule?.targetCountPerPeriod ?: 1)
        // 排期外允许「看到」习惯，但不允许写打卡：否则会出现一条不计入连续天数、
        // 却显示成「已打卡」的幽灵记录。
        if (rule != null && !HabitSchedule.isScheduled(rule, today)) {
            return CheckInResult.Rejected("今天不在这个习惯的排期里")
        }
        val existing = checkIns.forTask(task.id).filter { it.date == today && it.deletedAt == null }
        val current = existing.firstOrNull()
        if (current != null && rule != null && current.count < target) {
            checkIns.updateCount(current.id, current.count + 1)
            val newBadges = runCatching { badges.refresh() }.getOrDefault(emptyList())
            return CheckInResult.Checked(streakOf(rule, today), newBadges)
        }
        if (existing.isNotEmpty()) {
            existing.forEach { checkIns.softDelete(it.id, now) }
            if (task.type == TaskType.TODO) {
                tasks.setStatus(task.id, TaskStatus.TODO, now)
                task.parentId?.let { tasks.setStatus(it, TaskStatus.TODO, now) }
            }
            return CheckInResult.Undone()
        }
        insertCheckIn(task.id, today, source = "manual", isMakeup = false, now = now)
        if (task.type == TaskType.TODO) markDone(task, now)
        val newBadges = runCatching { badges.refresh() }.getOrDefault(emptyList())
        return CheckInResult.Checked(rule?.let { streakOf(it, today) } ?: 0, newBadges)
    }

    /**
     * 专注结算专用：幂等写入指定日期的打卡。
     * 已存在记录时，习惯类按目标次数累加；其它类型保持一条，不重复计数。
     * 返回的 id 会写入 FocusSession.checkInId，方便回溯。
     */
    suspend fun recordFromFocus(task: Task, date: LocalDate, at: Long): CheckInOutcome {
        if (task.deletedAt != null) return CheckInOutcome(false, null, 0)
        val rule = if (task.type == TaskType.HABIT) tasks.habitRule(task.id) else null
        val target = max(1, rule?.targetCountPerPeriod ?: 1)
        val current = checkIns.forTask(task.id).firstOrNull { it.date == date && it.deletedAt == null }
        var written = false
        var id = current?.id
        if (current == null) {
            val created = insertCheckIn(task.id, date, source = "timer", isMakeup = false, now = at)
            written = created != null
            id = created?.id
        } else if (rule != null && current.count < target) {
            checkIns.updateCount(current.id, current.count + 1)
            written = true
        }
        // 计时完成只写打卡，不自动把任务标成完成；完成动作由用户在长按面板确认
        // 这里不刷新徽章：此时 FocusSession 还没落库，刷了也看不到本次专注；
        // FocusSessionUseCase 会在会话写入后统一刷新一次，避免一次专注算两遍全量统计。
        return CheckInOutcome(
            written = written,
            checkInId = id,
            streak = rule?.let { streakOf(it, clock.today()) } ?: 0,
            newBadges = emptyList(),
        )
    }

    suspend fun makeup(task: Task, habit: HabitRule, date: LocalDate): CheckInResult {
        val today = clock.today()
        if (!date.isBefore(today)) return CheckInResult.Rejected("只能补今天以前的记录")
        if (date.isBefore(today.minusDays(7))) return CheckInResult.Rejected("只能补最近 7 天")
        if (!HabitSchedule.isScheduled(habit, date)) return CheckInResult.Rejected("该日期不在习惯排期中")
        val existing = checkIns.forTask(task.id).any { it.date == date && it.deletedAt == null }
        if (existing) return CheckInResult.Rejected("该日期已经打过卡")
        val now = clock.nowMillis()
        insertCheckIn(task.id, date, source = "manual", isMakeup = true, now = now)
        val newBadges = runCatching { badges.refresh() }.getOrDefault(emptyList())
        return CheckInResult.Checked(streakOf(habit, today), newBadges)
    }

    suspend fun updateMeta(checkInId: String, note: String?, mood: Int?) {
        checkIns.updateMeta(checkInId, note?.takeIf { it.isNotBlank() }, mood)
    }

    private suspend fun streakOf(rule: HabitRule, today: LocalDate): Int =
        StreakCalculator.currentStreak(
            rule,
            CheckInLedger(checkIns.forTask(rule.taskId)),
            today,
        )

    private suspend fun markDone(task: Task, at: Long) {
        tasks.setStatus(task.id, TaskStatus.DONE, at)
        task.parentId?.let { parent ->
            val siblings = tasks.tasksByParent(parent).filter { it.deletedAt == null }
            if (siblings.isNotEmpty() && siblings.all { it.status == TaskStatus.DONE }) {
                tasks.setStatus(parent, TaskStatus.DONE, at)
            }
        }
    }

    private suspend fun insertCheckIn(
        taskId: String,
        date: LocalDate,
        source: String,
        isMakeup: Boolean,
        now: Long,
    ): CheckIn? {
        val candidate = CheckIn(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            date = date,
            count = 1,
            source = source,
            isMakeup = isMakeup,
            createdAt = now,
        )
        val inserted = checkIns.insert(candidate)
        if (!inserted) return null
        // 软删除复活时 id 会沿用旧记录，这里以数据库中的最新行为准
        return checkIns.forTask(taskId)
            .firstOrNull { it.date == date && it.isMakeup == isMakeup && it.deletedAt == null }
            ?: candidate
    }
}
