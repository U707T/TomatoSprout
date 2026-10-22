package com.u707t.tomatosprout.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

sealed interface CheckInResult {
    data class Checked(val streak: Int, val newBadges: List<Badge>) : CheckInResult
    data class Undone(val message: String = "已撤销打卡") : CheckInResult
    data class Rejected(val reason: String) : CheckInResult
}

data class TodayItem(
    val task: Task,
    val habit: HabitRule?,
    val count: Int,
    val checked: Boolean,
    val target: Int,
    val streak: Int,
    val checkedId: String? = null,
    val note: String? = null,
    val mood: Int? = null,
)

data class TodayData(
    val today: LocalDate,
    val items: List<TodayItem>,
    val habitTotal: Int,
    val habitDone: Int,
    val focusSeconds: Long,
    val overallStreak: Int,
) {
    val allDone: Boolean get() = habitTotal > 0 && habitDone >= habitTotal
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
    private val focus: FocusRepository,
    private val badges: BadgeRefresher,
    private val clock: AppClock = SystemAppClock,
) {
    private fun dates(): Flow<LocalDate> = flow {
        while (currentCoroutineContext().isActive) {
            emit(clock.today())
            delay(60_000)
        }
    }

    fun observeToday(): Flow<TodayData> = combine(
        tasks.observeActiveTasks(),
        tasks.observeHabitRules(),
        checkIns.observeAll(),
        focus.observeAll(),
        dates(),
    ) { active, rules, checks, sessions, today ->
        val rulesByTask = rules.associateBy { it.taskId }
        val items = ArrayList<TodayItem>()
        active.forEach { task ->
            if (task.deletedAt != null) return@forEach
            when (task.type) {
                TaskType.HABIT -> {
                    val rule = rulesByTask[task.id] ?: return@forEach
                    if (!HabitSchedule.isScheduled(rule, today)) return@forEach
                    val dayChecks = checks.filter { it.taskId == task.id && it.date == today && it.deletedAt == null }
                    val count = dayChecks.sumOf { max(1, it.count) }
                    val target = max(1, rule.targetCountPerPeriod)
                    items += TodayItem(
                        task = task,
                        habit = rule,
                        count = count,
                        checked = count >= target,
                        target = target,
                        streak = StreakCalculator.currentStreak(rule, checks.filter { it.taskId == task.id }, today),
                        checkedId = dayChecks.firstOrNull()?.id,
                        note = dayChecks.firstOrNull()?.note,
                        mood = dayChecks.firstOrNull()?.mood,
                    )
                }
                TaskType.TODO -> {
                    val dueToday = task.dueAt == null || Instant.ofEpochMilli(task.dueAt)
                        .atZone(clock.zone()).toLocalDate() <= today
                    if (task.status == TaskStatus.TODO && dueToday) {
                        val todayCheck = checks.firstOrNull { it.taskId == task.id && it.date == today && it.deletedAt == null }
                        items += TodayItem(
                            task, null, if (todayCheck != null) 1 else 0, todayCheck != null, 1, 0,
                            todayCheck?.id, todayCheck?.note, todayCheck?.mood,
                        )
                    }
                }
                else -> Unit
            }
        }
        val habits = items.filter { it.task.type == TaskType.HABIT }
        val focusToday = sessions.filter {
            it.deletedAt == null && it.actualSeconds >= 0 &&
                it.attributedLocalDate(clock.zone()) == today
        }.sumOf { it.actualSeconds.toLong() }
        TodayData(
            today = today,
            items = items.sortedWith(compareBy({ it.checked }, { it.task.priority }, { it.task.createdAt })),
            habitTotal = habits.size,
            habitDone = habits.count { it.checked },
            focusSeconds = focusToday,
            overallStreak = habits.maxOfOrNull { it.streak } ?: 0,
        )
    }

    /** 兼容旧调用：按任务执行一次「打卡 / 撤销」。 */
    suspend fun toggle(item: TodayItem): CheckInResult = toggleToday(item.task)

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
        val newBadges = if (written) {
            runCatching { badges.refresh() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return CheckInOutcome(
            written = written,
            checkInId = id,
            streak = rule?.let { streakOf(it, clock.today()) } ?: 0,
            newBadges = newBadges,
        )
    }

    suspend fun undo(taskId: String, date: LocalDate) {
        checkIns.forTask(taskId)
            .filter { it.date == date && it.deletedAt == null }
            .forEach { checkIns.softDelete(it.id, clock.nowMillis()) }
    }

    /** 撤销当天该任务的全部打卡（含多次计数）。 */
    suspend fun removeAll(item: TodayItem) {
        val now = clock.nowMillis()
        val today = clock.today()
        checkIns.forTask(item.task.id)
            .filter { it.date == today && it.deletedAt == null }
            .forEach { checkIns.softDelete(it.id, now) }
        if (item.task.type == TaskType.TODO) {
            tasks.setStatus(item.task.id, TaskStatus.TODO, now)
            item.task.parentId?.let { tasks.setStatus(it, TaskStatus.TODO, now) }
        }
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
            checkIns.forTask(rule.taskId).filter { it.deletedAt == null },
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
