package com.u707t.tomatosprout.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

class TaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val subTasks: SubTaskRepository,
) {
    fun observeActive(): Flow<List<Task>> = tasks.observeActiveTasks()
    fun observeAll(): Flow<List<Task>> = tasks.observeAllTasks()

    suspend fun task(id: String): Task? = tasks.task(id)
    suspend fun updateTask(task: Task) = tasks.upsert(task)
    suspend fun habitRule(id: String): HabitRule? = tasks.habitRule(id)
    suspend fun goalRule(id: String): GoalRule? = tasks.goalRule(id)
    suspend fun reminder(taskId: String): ReminderRule? = reminders.forTask(taskId)
    fun observeSubtasks(parentId: String): Flow<List<SubTask>> = subTasks.observeForParent(parentId)
    suspend fun subtasks(parentId: String): List<SubTask> = subTasks.forParent(parentId)

    suspend fun addSubtask(parentId: String, title: String, at: Long): SubTask {
        val existing = subTasks.forParent(parentId)
        val item = SubTask(
            id = UUID.randomUUID().toString(),
            parentTaskId = parentId,
            title = title.trim(),
            done = false,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAt = at,
        )
        subTasks.upsert(item)
        refreshParent(parentId)
        return item
    }

    suspend fun setSubtaskDone(subTask: SubTask, done: Boolean) {
        subTasks.upsert(subTask.copy(done = done))
        refreshParent(subTask.parentTaskId)
    }

    suspend fun deleteSubtask(subTask: SubTask) {
        subTasks.delete(subTask.id)
        refreshParent(subTask.parentTaskId)
    }

    /** 任务卡片流中的上移/下移；首次移动时把隐式顺序固化为 sortOrder。 */
    suspend fun moveTask(taskId: String, delta: Int, at: Long) {
        val all = tasks.captureAll().tasks
            .filter { it.deletedAt == null && it.status != TaskStatus.ARCHIVED && it.parentId == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.priority }, { it.createdAt }))
        val index = all.indexOfFirst { it.id == taskId }
        val target = index + delta
        if (index < 0 || target !in all.indices) return
        val normalized = all.all { it.sortOrder == 0 }
        if (normalized) {
            all.forEachIndexed { i, task -> tasks.updateSortOrder(task.id, i + 1, at) }
        }
        val current = all[index]
        val swapped = all[target]
        val currentOrder = if (normalized) index + 1 else current.sortOrder
        val swappedOrder = if (normalized) target + 1 else swapped.sortOrder
        tasks.updateSortOrder(current.id, swappedOrder, at)
        tasks.updateSortOrder(swapped.id, currentOrder, at)
    }

    suspend fun save(
        task: Task,
        habitRule: HabitRule?,
        goalRule: GoalRule?,
        reminder: ReminderRule?,
    ) {
        tasks.upsert(task)
        if (habitRule != null) tasks.upsertHabitRule(habitRule) else tasks.deleteHabitRule(task.id)
        if (goalRule != null) tasks.upsertGoalRule(goalRule) else tasks.deleteGoalRule(task.id)
        if (reminder != null) reminders.upsert(reminder) else reminders.deleteForTask(task.id)
        if (task.parentId != null) refreshParent(task.parentId)
    }

    suspend fun setDone(task: Task, done: Boolean, at: Long) {
        tasks.setStatus(task.id, if (done) TaskStatus.DONE else TaskStatus.TODO, at)
        task.parentId?.let { refreshParent(it) }
    }

    suspend fun restore(task: Task, at: Long) {
        tasks.setStatus(task.id, TaskStatus.TODO, at)
    }

    suspend fun archive(task: Task, at: Long) {
        tasks.setStatus(task.id, TaskStatus.ARCHIVED, at)
        reminders.deleteForTask(task.id)
    }

    suspend fun delete(task: Task, at: Long) {
        tasks.softDelete(task.id, at)
        reminders.deleteForTask(task.id)
    }

    /** 子任务全部完成 → 父任务自动完成；任一取消则回退。 */
    suspend fun refreshParent(parentId: String) {
        val parent = tasks.task(parentId) ?: return
        if (parent.status == TaskStatus.ARCHIVED) return
        val children = subTasks.forParent(parentId)
        if (children.isEmpty()) return
        val allDone = children.all { it.done }
        tasks.setStatus(parentId, if (allDone) TaskStatus.DONE else TaskStatus.TODO, System.currentTimeMillis())
    }
}

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

class CheckInUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeUseCase,
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
            it.deletedAt == null &&
                Instant.ofEpochMilli(it.endedAt).atZone(clock.zone()).toLocalDate() == today
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

    suspend fun toggle(item: TodayItem): CheckInResult {
        val today = clock.today()
        val existing = checkIns.forTask(item.task.id)
            .firstOrNull { it.date == today && it.deletedAt == null }
        // 目标次数 > 1 时，逐次累加 count；达到目标后再点则整体撤销
        if (existing != null && item.task.type == TaskType.HABIT && existing.count < max(1, item.target)) {
            checkIns.updateCount(existing.id, existing.count + 1)
            val newBadges = badges.refresh()
            val streak = item.habit?.let {
                StreakCalculator.currentStreak(it, checkIns.forTask(item.task.id).filter { c -> c.deletedAt == null }, today)
            } ?: 0
            return CheckInResult.Checked(streak, newBadges)
        }
        if (existing != null) {
            removeAll(item)
            return CheckInResult.Undone()
        }
        val now = clock.nowMillis()
        insertCheckIn(item.task.id, today, source = "manual", isMakeup = false, now = now)
        if (item.task.type == TaskType.TODO) {
            tasks.setStatus(item.task.id, TaskStatus.DONE, now)
            item.task.parentId?.let { parent ->
                val siblings = tasks.tasksByParent(parent).filter { it.deletedAt == null }
                if (siblings.all { it.status == TaskStatus.DONE }) {
                    tasks.setStatus(parent, TaskStatus.DONE, now)
                }
            }
        }
        val newBadges = badges.refresh()
        val streak = item.habit?.let {
            StreakCalculator.currentStreak(it, checkIns.forTask(item.task.id).filter { c -> c.deletedAt == null }, today)
        } ?: 0
        return CheckInResult.Checked(streak, newBadges)
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
        val newBadges = badges.refresh()
        return CheckInResult.Checked(
            StreakCalculator.currentStreak(habit, checkIns.forTask(task.id).filter { it.deletedAt == null }, today),
            newBadges,
        )
    }

    suspend fun updateMeta(checkInId: String, note: String?, mood: Int?) {
        checkIns.updateMeta(checkInId, note?.takeIf { it.isNotBlank() }, mood)
    }

    private suspend fun insertCheckIn(taskId: String, date: LocalDate, source: String, isMakeup: Boolean, now: Long) {
        checkIns.insert(
            CheckIn(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                date = date,
                count = 1,
                source = source,
                isMakeup = isMakeup,
                createdAt = now,
            ),
        )
    }
}

data class FocusCompletion(val session: FocusSession, val newBadges: List<Badge>)

class FocusUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val badges: BadgeUseCase,
    private val clock: AppClock = SystemAppClock,
) {
    /** 严格模式中断的番茄由调用方直接丢弃，不进入本方法。 */
    suspend fun complete(
        task: Task?,
        mode: String,
        plannedSeconds: Int,
        actualSeconds: Int,
        startedAt: Long,
        endedAt: Long,
        midnightShift: Boolean,
        interruptionCount: Int,
    ): FocusCompletion? {
        if (actualSeconds < 1) return null
        val session = FocusSession(
            id = UUID.randomUUID().toString(),
            taskId = task?.id,
            mode = mode,
            plannedSeconds = plannedSeconds,
            actualSeconds = actualSeconds,
            startedAt = startedAt,
            endedAt = endedAt,
            isMidnightShifted = SessionAttribution.isShifted(startedAt, midnightShift, clock.zone()),
            interruptionCount = interruptionCount,
        )
        focus.insert(session)
        if (task != null && task.type == TaskType.HABIT && actualSeconds >= 30) {
            val habit = tasks.habitRule(task.id)
            val date = SessionAttribution.dateOf(startedAt, midnightShift, clock.zone())
            val exists = habit != null && checkIns.forTask(task.id).any { it.date == date && it.deletedAt == null }
            if (habit != null && !exists) {
                checkIns.insert(
                    CheckIn(
                        id = UUID.randomUUID().toString(),
                        taskId = task.id,
                        date = date,
                        count = 1,
                        source = "timer",
                        isMakeup = false,
                        createdAt = endedAt,
                    ),
                )
            }
        }
        if (task != null && task.type == TaskType.POMODORO) {
            tasks.incrementCompletedPomos(task.id, 1, endedAt)
        }
        val newBadges = badges.refresh()
        return FocusCompletion(session, newBadges)
    }
}

class BadgeUseCase @Inject constructor(
    private val badges: BadgeRepository,
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val clock: AppClock = SystemAppClock,
) {
    suspend fun ensureSeeded() {
        badges.seed(BadgeCatalog.seedBadges())
    }

    suspend fun refresh(): List<Badge> {
        val all = badges.all()
        val bundle = tasks.captureAll()
        val stats = StatsCalculator.build(
            today = clock.today(),
            tasks = bundle.tasks,
            habitRules = bundle.habitRules,
            goalRules = bundle.goalRules,
            checkIns = checkIns.all(),
            focusSessions = focus.all(),
            badges = all,
            zone = clock.zone(),
        )
        val unlocked = all.filter { it.unlockedAt != null }.map { it.key }.toSet()
        val newly = BadgeEvaluator.newlyQualified(
            BadgeStats(
                currentStreak = stats.currentStreak,
                longestStreak = stats.longestStreak,
                totalCheckIns = stats.totalCheckIns,
                totalFocusSeconds = stats.totalFocusSeconds,
                totalPomos = stats.totalPomos,
                perfectDays = stats.perfectDays,
            ),
            unlocked,
        )
        if (newly.isEmpty()) return emptyList()
        val at = clock.nowMillis()
        badges.unlock(newly.map { it.key }.toSet(), at)
        return newly.map {
            Badge(it.key, it.key, it.name, "{}", at, it.description)
        }
    }
}

class HeatmapUseCase @Inject constructor(
    private val stats: StatsUseCase,
) {
    /** 两个独立 Flow，UI 切换维度时不需要重新查库（Room 会共享同一份冷流缓存）。 */
    fun observeCheckIns(): Flow<HeatmapData> = stats.observe()
        .map { data -> HeatmapData(HeatmapMetric.CHECK_INS, data.days.mapValues { it.value.checkIns.toLong() }) }
        .distinctUntilChanged()

    fun observeFocusDuration(): Flow<HeatmapData> = stats.observe()
        .map { data -> HeatmapData(HeatmapMetric.FOCUS_DURATION, data.days.mapValues { it.value.focusSeconds }) }
        .distinctUntilChanged()
}

class StatsUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeRepository,
    private val clock: AppClock = SystemAppClock,
) {
    fun observe(): Flow<StatsData> = combine(
        combine(tasks.observeAllTasks(), tasks.observeHabitRules(), tasks.observeGoalRules()) { t, h, g ->
            Triple(t, h, g)
        },
        checkIns.observeAll(),
        focus.observeAll(),
        badges.observeAll(),
    ) { bundle, checks, sessions, badgeList ->
        StatsCalculator.build(
            today = clock.today(),
            tasks = bundle.first,
            habitRules = bundle.second,
            goalRules = bundle.third,
            checkIns = checks,
            focusSessions = sessions,
            badges = badgeList,
            zone = clock.zone(),
        )
    }
}
